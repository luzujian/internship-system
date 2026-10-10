package com.internship.util;

import com.internship.agent.business.AgentException;
import com.internship.config.AiRuntimeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AiCallGuardContractTest {
    private AiCallGuard twoRequests() {
        var limits = new AiRuntimeProperties(); limits.setMaxRequestsPerMinute(2);
        return new AiCallGuard(limits);
    }

    private ChatResponse text() {
        return new ChatResponse(List.of(new Generation(new AssistantMessage("reply"))));
    }

    @Test void defaultSynchronousOverloadsStillGoThroughAdmission() {
        var delegate = mock(ChatModel.class); when(delegate.call(any(Prompt.class))).thenReturn(text());
        var model = twoRequests().wrap(delegate);
        assertEquals("reply", model.call("first"));
        assertEquals("reply", model.call(new UserMessage("second")));
        assertEquals("RATE_LIMITED", assertThrows(AgentException.class, () -> model.call("third")).getCode());
        verify(delegate, times(2)).call(any(Prompt.class));
    }

    @Test void defaultStreamingOverloadsCountOnSubscription() {
        var delegate = mock(ChatModel.class); when(delegate.stream(any(Prompt.class))).thenReturn(Flux.just(text()));
        var model = twoRequests().wrap(delegate);
        assertEquals(List.of("reply"), model.stream("first").collectList().block());
        assertEquals(List.of("reply"), model.stream(new UserMessage("second")).collectList().block());
        assertEquals("RATE_LIMITED", assertThrows(AgentException.class,
                () -> model.stream("third").collectList().block()).getCode());
        verify(delegate, times(2)).stream(any(Prompt.class));
    }

    @Test void completionDoesNotDropTheFinalUsageOnlyChunk() {
        var usage = new ChatResponse(List.of(), ChatResponseMetadata.builder()
                .usage(new DefaultUsage(17, 5, 22)).build());
        var delegate = mock(ChatModel.class);
        when(delegate.stream(any(Prompt.class))).thenReturn(Flux.just(text(), usage));
        var responses = twoRequests().wrap(delegate).stream(new Prompt("test")).collectList().block();
        assertNotNull(responses); assertEquals(2, responses.size());
        assertSame(usage, responses.getLast());
        assertNull(responses.getLast().getResult());
        assertEquals(22, responses.getLast().getMetadata().getUsage().getTotalTokens());
    }
}
