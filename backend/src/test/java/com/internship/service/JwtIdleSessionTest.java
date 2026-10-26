package com.internship.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtIdleSessionTest {
    private final JwtRedisService service = new JwtRedisService();
    private static final long START = 1_800_000_000_000L;
    private static final long MINUTE = 60_000L;

    private void at(long minutes) {
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(Instant.ofEpochMilli(START + minutes * MINUTE), ZoneOffset.UTC));
    }

    @Test void pollingAndRotationDoNotExtendIdleTime() {
        at(0); service.startSession("session", 7 * 24 * 60 * MINUTE);
        at(14); assertEquals(START, service.checkSession("session", 15 * MINUTE, false));
        service.retainSession("session", 7 * 24 * 60 * MINUTE);
        at(15); assertEquals(-1, service.checkSession("session", 15 * MINUTE, false));
    }

    @Test void activityKeepsAnOldLoginActiveAndSessionsRemainIndependent() {
        at(0); service.startSession("active", 7 * 24 * 60 * MINUTE); service.startSession("idle", 7 * 24 * 60 * MINUTE);
        for (int minute = 14; minute <= 140; minute += 14) {
            at(minute); assertEquals(START + minute * MINUTE, service.checkSession("active", 15 * MINUTE, true));
        }
        assertEquals(-1, service.checkSession("idle", 15 * MINUTE, true));
        at(154); assertTrue(service.checkSession("active", 15 * MINUTE, false) > 0);
        at(155); assertEquals(-1, service.checkSession("active", 15 * MINUTE, true));
    }

    @Test void expiredSessionCannotBeRevivedByActivityOrIncreasingTimeout() {
        at(0); service.startSession("session", 7 * 24 * 60 * MINUTE);
        at(15); assertEquals(-1, service.checkSession("session", 15 * MINUTE, true));
        assertEquals(-1, service.checkSession("session", 480 * MINUTE, true));
        service.retainSession("session", 7 * 24 * 60 * MINUTE);
        assertEquals(-1, service.checkSession("session", 480 * MINUTE, false));
        assertEquals(-1, service.checkSession("missing", 15 * MINUTE, true));
        assertEquals(-1, service.checkSession(null, 15 * MINUTE, true));
    }

    @Test void changingTimeoutUsesLastActivityAndRetentionRemainsBounded() {
        at(0); service.startSession("session", 30 * MINUTE);
        at(20); assertTrue(service.checkSession("session", 120 * MINUTE, false) > 0);
        assertEquals(-1, service.checkSession("session", 15 * MINUTE, false));
        at(0); service.startSession("short-retention", MINUTE);
        at(1); assertEquals(-1, service.checkSession("short-retention", 120 * MINUTE, true));
    }

    @Test void sharedRedisFailureNeverFallsBackToLocalActivity() {
        at(0); service.startSession("session", 30 * MINUTE);
        var redis = mock(StringRedisTemplate.class);
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redis);
        assertThrows(IllegalStateException.class, () -> service.checkSession("session", 15 * MINUTE, true));
    }
}
