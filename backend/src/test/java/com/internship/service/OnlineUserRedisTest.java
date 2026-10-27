package com.internship.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in: use a disposable local Redis, never the application's configured server. */
@EnabledIfEnvironmentVariable(named = "TEST_REDIS_ONLINE", matches = "true")
class OnlineUserRedisTest {
    private LettuceConnectionFactory factory;
    private StringRedisTemplate template;
    private JwtRedisService jwt;
    private OnlineUserService online;
    private final Set<String> keys = new HashSet<>(), sids = new HashSet<>();
    private final long start = System.currentTimeMillis();

    @BeforeEach void setup() {
        int port = Integer.parseInt(System.getenv().getOrDefault("TEST_REDIS_ONLINE_PORT", "16391"));
        factory = new LettuceConnectionFactory("127.0.0.1", port); factory.afterPropertiesSet(); factory.start();
        template = new StringRedisTemplate(factory);
        jwt = new JwtRedisService(); ReflectionTestUtils.setField(jwt, "stringRedisTemplate", template);
        online = new OnlineUserService(jwt); ReflectionTestUtils.setField(online, "stringRedisTemplate", template);
        at(0);
    }
    @AfterEach void cleanup() {
        if (template != null) {
            if (!keys.isEmpty()) template.delete(keys);
            if (!sids.isEmpty()) template.opsForZSet().remove("jwt:online:index", sids.toArray());
        }
        if (factory != null) factory.destroy();
    }
    private void at(int seconds) {
        Clock time = Clock.fixed(Instant.ofEpochMilli(start + seconds * 1000L), ZoneOffset.UTC);
        ReflectionTestUtils.setField(jwt, "clock", time); ReflectionTestUtils.setField(online, "clock", time);
    }
    private Claims session(String username) {
        String sid = UUID.randomUUID().toString(); sids.add(sid);
        keys.addAll(List.of("jwt:session-activity:" + sid, "jwt:online:session:" + sid, "jwt:blacklist:session:" + sid,
                "jwt:version:" + username, "jwt:online:disabled:STUDENT:" + username));
        jwt.startSession(sid, 3_600_000);
        return Jwts.claims().add(Map.of("sid", sid, "username", username, "role", "ROLE_STUDENT", "name", username, "ver", 1)).build();
    }
    private void observe(Claims claims) { online.observe(claims, jwt.checkSession(claims.get("sid", String.class), 900_000, false)); }

    @Test void realRedisScriptsDeduplicateAndExpirePresenceWithoutRenewingAuthentication() {
        String username = "online-qa-" + UUID.randomUUID();
        var first = session(username); var second = session(username); observe(first); observe(second);
        var page = online.list(username, "STUDENT", "", 1, 20);
        assertEquals(1, page.total()); assertEquals(2, page.rows().getFirst().sessionCount());
        assertTrue(template.getExpire("jwt:online:session:" + first.get("sid"), TimeUnit.MILLISECONDS) > 170_000);
        assertEquals(Long.toString(start), template.opsForValue().get("jwt:session-activity:" + first.get("sid")));
        at(120); observe(first); observe(second);
        assertEquals(1, online.list(username, "", "IDLE", 1, 20).total());
        at(299); assertEquals(1, online.list(username, "", "", 1, 20).total());
        at(300); assertEquals(0, online.list(username, "", "", 1, 20).total());
    }

    @Test void redisRejectsLateHeartbeatsAfterRevocationAndAccountDisabling() {
        String username = "online-qa-" + UUID.randomUUID();
        var claims = session(username); observe(claims);
        assertEquals(1, online.list(username, "", "", 1, 20).total());
        jwt.blacklistToken("session:" + claims.get("sid"), System.currentTimeMillis() + 60_000);
        IntStream.range(0, 24).parallel().forEach(i -> observe(claims));
        at(5); assertEquals(0, online.list(username, "", "", 1, 20).total());
        assertFalse(Boolean.TRUE.equals(template.hasKey("jwt:online:session:" + claims.get("sid"))));
        var other = session(username); observe(other);
        online.accountChanged("STUDENT", username, username, 0); observe(other);
        assertEquals(0, online.list(username, "", "", 1, 20).total());
        online.accountChanged("STUDENT", username, username, 1); observe(other);
        at(10); assertEquals(1, online.list(username, "", "", 1, 20).total());
        jwt.incrementUserTokenVersion(username); observe(other);
        at(15); assertEquals(0, online.list(username, "", "", 1, 20).total());
    }
}
