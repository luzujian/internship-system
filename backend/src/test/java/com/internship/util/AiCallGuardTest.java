package com.internship.util;

import com.internship.agent.business.AgentException;
import com.internship.config.AiRuntimeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiCallGuardTest {
    private AiRuntimeProperties singleSlot() {
        var limits = new AiRuntimeProperties(); limits.setMaxConcurrentCalls(1); return limits;
    }

    @Test void leaseIsIdempotentAndExceptionsReleaseCapacity() {
        var guard = new AiCallGuard(singleSlot());
        var first = guard.acquire();
        assertEquals(429, assertThrows(AgentException.class, guard::acquire).getHttpStatus());
        first.close(); first.close();
        try (var second = guard.acquire()) { assertThrows(AgentException.class, guard::acquire); }
        assertThrows(IllegalStateException.class, () -> guard.call(() -> { throw new IllegalStateException("test"); }));
        assertEquals("next", guard.call(() -> "next"));
    }

    @Test void minuteLimitRecoversWithoutLeakingTheConcurrentSlot() {
        var limits = singleSlot(); limits.setMaxRequestsPerMinute(2);
        var clock = new AtomicLong(0); var guard = new AiCallGuard(limits, clock::get);
        guard.call(() -> 1); guard.call(() -> 2);
        assertEquals("RATE_LIMITED", assertThrows(AgentException.class, guard::acquire).getCode());
        clock.set(60000);
        assertEquals(3, guard.call(() -> 3));
    }

    @Test void userRequestsAreSingleFlightAndRateLimitedIndependently() {
        var limits = singleSlot(); limits.setMaxUserRequestsPerMinute(1);
        var clock = new AtomicLong(0); var guard = new AiCallGuard(limits, clock::get);
        try (var user = guard.acquireUser("fill:student1")) {
            assertEquals("AI_BUSY", assertThrows(AgentException.class, () -> guard.acquireUser("fill:student1")).getCode());
            try (var other = guard.acquireUser("fill:student2")) { assertNotNull(other); }
        }
        assertEquals("RATE_LIMITED", assertThrows(AgentException.class, () -> guard.acquireUser("fill:student1")).getCode());
        clock.set(60000);
        try (var next = guard.acquireUser("fill:student1")) { assertNotNull(next); }
    }

    @Test void cancellationAndPublisherConstructionFailureReleaseCapacity() {
        var guard = new AiCallGuard(singleSlot());
        var stream = guard.stream(Flux::<Integer>never).subscribe();
        assertThrows(AgentException.class, guard::acquire);
        stream.dispose();
        assertEquals(1, guard.call(() -> 1));
        assertThrows(IllegalStateException.class,
                () -> guard.stream(() -> { throw new IllegalStateException("construction failed"); }).blockLast());
        assertEquals(2, guard.call(() -> 2));
        assertThrows(IllegalArgumentException.class,
                () -> guard.stream(() -> Flux.error(new IllegalArgumentException("upstream failed"))).blockLast());
        assertEquals(3, guard.call(() -> 3));
    }

    @Test void streamingAndSynchronousClientsShareTheSameCapacity() {
        var guard = new AiCallGuard(singleSlot());
        var first = mock(ChatModel.class); var second = mock(ChatModel.class);
        var prompt = new Prompt("test");
        when(first.stream(prompt)).thenReturn(Flux.never());
        when(second.call(prompt)).thenReturn(new ChatResponse(List.of()));
        var stream = guard.wrap(first).stream(prompt).subscribe();
        assertEquals("AI_BUSY", assertThrows(AgentException.class, () -> guard.wrap(second).call(prompt)).getCode());
        verifyNoInteractions(second);
        stream.dispose();
        assertNotNull(guard.wrap(second).call(prompt));
    }

    @Test void continuousChunksStillHaveAnAbsoluteStreamDeadline() {
        var limits = singleSlot(); limits.setReadTimeoutMillis(1000); limits.setStreamTimeoutMillis(60);
        var guard = new AiCallGuard(limits);
        var error = assertThrows(AgentException.class,
                () -> guard.stream(() -> Flux.interval(Duration.ofMillis(5))).blockLast(Duration.ofSeconds(3)));
        assertEquals("MODEL_TIMEOUT", error.getCode());
        assertEquals(1, guard.call(() -> 1));
    }
}
