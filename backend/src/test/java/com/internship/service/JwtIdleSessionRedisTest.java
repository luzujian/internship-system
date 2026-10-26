package com.internship.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "TEST_REDIS_IDLE_SESSION", matches = "true")
class JwtIdleSessionRedisTest {
    @Test void realRedisChecksAreAtomicPreserveTtlAndNeverReviveExpiredSessions() {
        var factory = new LettuceConnectionFactory("127.0.0.1", 6379);
        factory.afterPropertiesSet(); factory.start();
        var template = new StringRedisTemplate(factory);
        String sid = "idle-test-" + UUID.randomUUID(), key = "jwt:session-activity:" + sid;
        long start = System.currentTimeMillis();
        var service = new JwtRedisService();
        ReflectionTestUtils.setField(service, "stringRedisTemplate", template);
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(Instant.ofEpochMilli(start), ZoneOffset.UTC));
        try {
            service.startSession(sid, 60_000);
            ReflectionTestUtils.setField(service, "clock", Clock.fixed(Instant.ofEpochMilli(start + 14 * 60_000L), ZoneOffset.UTC));
            assertEquals(start, service.checkSession(sid, 15 * 60_000L, false));
            service.retainSession(sid, 120_000);
            assertEquals(start, service.checkSession(sid, 15 * 60_000L, false));
            long touched = service.checkSession(sid, 15 * 60_000L, true);
            assertEquals(start + 14 * 60_000L, touched);
            assertTrue(template.getExpire(key, TimeUnit.MILLISECONDS) > 0);
            ReflectionTestUtils.setField(service, "clock", Clock.fixed(Instant.ofEpochMilli(start + 29 * 60_000L), ZoneOffset.UTC));
            assertTrue(IntStream.range(0, 30).parallel().allMatch(i -> service.checkSession(sid, 15 * 60_000L, true) == -1));
            assertEquals(-1, service.checkSession(sid, 480 * 60_000L, true));
            assertTrue(template.getExpire(key, TimeUnit.MILLISECONDS) > 0);
            template.delete(key);
            assertEquals(-1, service.checkSession(sid, 15 * 60_000L, true));
            assertFalse(template.hasKey(key));
        } finally { template.delete(key); factory.destroy(); }
    }
}
