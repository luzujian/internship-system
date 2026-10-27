package com.internship.service;

import com.internship.config.SystemSettingsConfig;
import com.internship.entity.SystemSettings;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OnlineUserServiceTest {
    private final JwtRedisService jwt = new JwtRedisService();
    private final OnlineUserService online = new OnlineUserService(jwt);
    private final long start = System.currentTimeMillis();
    private SystemSettings original;

    @BeforeEach void setup() {
        original = SystemSettingsConfig.getCurrentSettings();
        var settings = new SystemSettings(); settings.setSessionTimeout(15);
        ReflectionTestUtils.setField(SystemSettingsConfig.class, "currentSettings", settings);
        at(0);
    }
    @AfterEach void restore() {
        ReflectionTestUtils.setField(SystemSettingsConfig.class, "currentSettings", original);
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }
    private void at(long seconds) {
        Clock clock = Clock.fixed(Instant.ofEpochMilli(start + seconds * 1000), ZoneOffset.UTC);
        ReflectionTestUtils.setField(jwt, "clock", clock);
        ReflectionTestUtils.setField(online, "clock", clock);
    }
    private Claims session(String username, String role) {
        String sid = UUID.randomUUID().toString(); jwt.startSession(sid, 3_600_000);
        return Jwts.claims().add(Map.of("sid", sid, "username", username, "role", role,
                "name", "姓名-" + username, "userId", 1, "ver", 1)).build();
    }
    private void observe(Claims claims) {
        online.observe(claims, jwt.checkSession(claims.get("sid", String.class), 900_000, false));
    }

    @Test void rolesHaveSeparateIdentitiesAndMultipleSessionsAndTeacherSubtypesAreDeduplicated() {
        observe(session("same", "ROLE_STUDENT")); observe(session("same", "ROLE_COMPANY"));
        observe(session("teacher", "ROLE_TEACHER_COLLEGE")); observe(session("teacher", "ROLE_TEACHER_COUNSELOR"));
        observe(session("admin", "ROLE_ADMIN"));
        var page = online.list("", "", "", 1, 20);
        assertEquals(4, page.total()); assertEquals(4, page.summary().active());
        assertEquals(Map.of("STUDENT", 1, "TEACHER", 1, "COMPANY", 1, "ADMIN", 1), page.summary().byRole());
        assertEquals(2, page.rows().stream().filter(u -> u.accountType().equals("TEACHER")).findFirst().orElseThrow().sessionCount());
        assertEquals(1, online.list("姓名-same", "STUDENT", "ACTIVE", 1, 20).total());
        assertTrue(online.list("", "", "", Integer.MAX_VALUE, 100).rows().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> online.list("", "STUDENT", "OTHER", 1, 20));
        assertThrows(IllegalArgumentException.class, () -> online.list("", "", "", 1, 101));
    }

    @Test void pollingSeparatesPresenceFromRealActivityAndNeverRenewsIdleDeadline() {
        var claims = session("student", "ROLE_STUDENT"); observe(claims);
        at(119); observe(claims); assertEquals(1, online.summary().active());
        at(120); observe(claims);
        assertEquals(1, online.summary().idle());
        var row = online.list("", "", "", 1, 20).rows().getFirst();
        assertEquals(start, row.lastActivityAt()); assertEquals(start + 120_000, row.lastSeenAt());
        for (int second = 180; second < 900; second += 60) { at(second); observe(claims); assertEquals(1, online.summary().totalOnline()); }
        at(900); observe(claims);
        assertEquals(-1, jwt.checkSession(claims.get("sid", String.class), 900_000, true));
        assertEquals(0, online.summary().totalOnline());
    }

    @Test void closedPagesDisappearAtThreeMinutesAndAnAuthenticatedReturnCanRejoin() {
        var claims = session("student", "ROLE_STUDENT"); observe(claims);
        at(179); assertEquals(1, online.summary().totalOnline());
        at(180); assertEquals(0, online.summary().totalOnline());
        at(181); observe(claims);
        at(186); assertEquals(1, online.summary().totalOnline());
    }

    @Test void administratorPreviewIsGroupedUnderTheRealAdministrator() {
        observe(session("admin", "ROLE_ADMIN"));
        var target = session("student", "ROLE_STUDENT");
        var claims = Jwts.claims().add(target).add("isReadOnly", true).add("originalAdminId", 5)
                .add("originalAdminUsername", "admin").add("originalAdminName", "管理员姓名").add("originalAdminVersion", 1).build();
        observe(claims);
        var page = online.list("", "", "", 1, 20);
        assertEquals(1, page.total()); assertEquals(0, page.summary().byRole().get("STUDENT"));
        var row = page.rows().getFirst(); assertEquals("ADMIN", row.accountType());
        assertTrue(row.simulation()); assertEquals(2, row.sessionCount()); assertEquals("admin", row.username());
        jwt.incrementUserTokenVersion("admin"); at(5); observe(claims);
        assertEquals(0, online.summary().totalOnline());
    }

    @Test void logoutOfOneSessionPreservesAnotherAndLateHeartbeatsCannotReviveRevokedSessions() {
        var first = session("student", "ROLE_STUDENT");
        var second = session("student", "ROLE_STUDENT");
        observe(first); observe(second);
        assertEquals(2, online.list("", "", "", 1, 20).rows().getFirst().sessionCount());
        jwt.blacklistToken("session:" + first.get("sid"), System.currentTimeMillis() + 60_000);
        at(5); observe(first);
        assertEquals(1, online.list("", "", "", 1, 20).rows().getFirst().sessionCount());
        jwt.incrementUserTokenVersion("student"); at(10); observe(second);
        assertEquals(0, online.summary().totalOnline());
    }

    @Test void disablingAndDeletionRejectLateRequestsAndOnlyCommittedChangesTakeEffect() {
        var claims = session("student", "ROLE_STUDENT"); observe(claims);
        TransactionSynchronizationManager.initSynchronization();
        online.accountChanged("STUDENT", "student", "student", 0);
        at(5); assertEquals(1, online.summary().totalOnline());
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
        TransactionSynchronizationManager.clearSynchronization();
        observe(claims); assertEquals(0, online.summary().totalOnline());
        online.accountChanged("STUDENT", "student", "student", 1); observe(claims);
        at(10); assertEquals(1, online.summary().totalOnline());
        online.accountChanged("STUDENT", "student", null, 0); observe(claims);
        assertEquals(0, online.summary().totalOnline());
    }

    @Test void configuredRedisFailuresDoNotCreateAnEmptyOnlineSnapshotOrBreakSessionChecks() {
        var redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new IllegalStateException("offline"));
        ReflectionTestUtils.setField(online, "stringRedisTemplate", redis);
        var claims = session("student", "ROLE_STUDENT");
        assertDoesNotThrow(() -> observe(claims));
        assertTrue(jwt.checkSession(claims.get("sid", String.class), 900_000, false) > 0);
        assertThrows(IllegalStateException.class, online::summary);
    }
}
