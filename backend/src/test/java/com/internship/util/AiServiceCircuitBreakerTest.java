package com.internship.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class AiServiceCircuitBreakerTest {
    private final AiServiceCircuitBreaker breaker = new AiServiceCircuitBreaker();

    @BeforeEach void enable() { breaker.enableAiService(); }

    @Test void nullFailuresOpenTheGlobalCircuitAtTheThreshold() {
        for (int i = 0; i < 4; i++) {
            assertDoesNotThrow(() -> breaker.recordFailure(null));
            assertTrue(breaker.allowRequest(null));
        }
        breaker.recordFailure(null);
        assertFalse(breaker.allowRequest(null));
        assertFalse(breaker.allowRequest(12L));
        var status = breaker.getStatus();
        assertEquals(5, status.getFailureCount());
        assertTrue(status.isCircuitBreakOpen());
        assertTrue(status.getRemainingBreakTimeSeconds() > 0);
        assertTrue(status.getRemainingBreakTimeSeconds() <= 300);
    }

    @Test void successfulGlobalCallsResetConsecutiveFailures() {
        for (int i = 0; i < 4; i++) breaker.recordFailure(null);
        breaker.recordSuccess(null);
        assertEquals(0, breaker.getStatus().getFailureCount());
        for (int i = 0; i < 4; i++) breaker.recordFailure(null);
        assertTrue(breaker.allowRequest(null));
        breaker.recordFailure(null);
        assertFalse(breaker.allowRequest(null));
        breaker.resetCircuitBreaker(null);
        assertTrue(breaker.allowRequest(null));
        assertFalse(breaker.getStatus().isCircuitBreakOpen());
    }

    @Test void counselorAndGlobalFailureCountersRemainIndependent() {
        for (int i = 0; i < 5; i++) breaker.recordFailure(12L);
        assertFalse(breaker.allowRequest(12L));
        assertTrue(breaker.allowRequest(13L));
        assertTrue(breaker.allowRequest(null));
        assertEquals(0, breaker.getStatus().getFailureCount());
        for (int i = 0; i < 5; i++) breaker.recordFailure(null);
        assertFalse(breaker.allowRequest(13L));
        breaker.resetCircuitBreaker(null);
        assertTrue(breaker.allowRequest(13L));
        assertFalse(breaker.allowRequest(12L));
    }

    @Test void parallelGlobalFailuresAreCountedWithoutNullKeyCrashes() throws Exception {
        var start = new CountDownLatch(1);
        var results = new ArrayList<Future<?>>();
        try (var workers = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < 8; i++) results.add(workers.submit(() -> {
                assertTrue(start.await(2, TimeUnit.SECONDS));
                breaker.recordFailure(null);
                return null;
            }));
            start.countDown();
            for (var result : results) result.get(3, TimeUnit.SECONDS);
        }
        assertEquals(8, breaker.getStatus().getFailureCount());
        assertFalse(breaker.allowRequest(null));
    }
}
