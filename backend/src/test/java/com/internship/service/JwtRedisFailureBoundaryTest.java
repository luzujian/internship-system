package com.internship.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtRedisFailureBoundaryTest {
    @Test void loginCounterPassesBothKeysSeparatelyFromLuaArguments() {
        var redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class), anyList(), any(Object[].class)))
                .thenAnswer(call -> {
                    assertEquals(java.util.List.of("login:attempts:alice", "login:locked:alice"), call.getArgument(1));
                    assertDoesNotThrow(() -> Integer.parseInt((String)call.getArgument(2)));
                    assertDoesNotThrow(() -> Integer.parseInt((String)call.getArgument(3)));
                    return 2L;
                });
        var service = new LoginAttemptService(); ReflectionTestUtils.setField(service,"stringRedisTemplate",redis);
        assertEquals(2,service.recordFailure("alice"));
    }
    @Test void configuredRedisFailureCannotFallBackToEmptyLocalState() {
        var redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new IllegalStateException("offline"));
        var service = new JwtRedisService();
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redis);
        assertThrows(IllegalStateException.class, () -> service.isInBlacklist("revoked"));
        assertThrows(IllegalStateException.class, () -> service.getUserTokenVersion("alice"));
        assertThrows(IllegalStateException.class, () -> service.incrementUserTokenVersion("alice"));
        assertThrows(IllegalStateException.class, () -> service.blacklistToken("jti", System.currentTimeMillis()+60000));
    }
    @Test void memoryModePreservesDeletionTombstoneAndConcurrentIncrements() {
        var service = new JwtRedisService();
        assertEquals(1, service.getUserTokenVersion("alice"));
        java.util.stream.IntStream.range(0, 100).parallel().forEach(i -> service.incrementUserTokenVersion("alice"));
        service.deleteUserTokenVersion("alice");
        assertEquals(102, service.getUserTokenVersion("alice"));
        service.blacklistToken("jti", System.currentTimeMillis()+60000);
        assertTrue(service.isInBlacklist("jti"));
        assertFalse(service.isInBlacklist("other"));
    }
    @Test void loginCannotProceedWhileSharedLockStateIsUnavailable() {
        var redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new IllegalStateException("offline"));
        when(redis.hasKey(anyString())).thenThrow(new IllegalStateException("offline"));
        var service = new LoginAttemptService();
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redis);
        assertFalse(service.tryAcquireLoginLock("alice"));
        assertTrue(service.isLocked("alice"));
        assertThrows(IllegalStateException.class, () -> service.recordFailure("alice"));
    }
}
