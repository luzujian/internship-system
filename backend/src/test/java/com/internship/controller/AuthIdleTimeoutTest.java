package com.internship.controller;

import com.internship.config.SystemSettingsConfig;
import com.internship.entity.SystemSettings;
import com.internship.service.JwtRedisService;
import com.internship.utils.JwtUtils;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AuthIdleTimeoutTest {
    private final JwtRedisService redis = new JwtRedisService();
    private final JwtUtils jwt = new JwtUtils();
    private final AuthController controller = new AuthController();
    private final long start = System.currentTimeMillis();
    private SystemSettings original;

    @BeforeEach void setup() {
        original = SystemSettingsConfig.getCurrentSettings();
        var settings = new SystemSettings(); settings.setSessionTimeout(15);
        ReflectionTestUtils.setField(SystemSettingsConfig.class, "currentSettings", settings);
        ReflectionTestUtils.setField(jwt, "secretKeyString", "unit-test-idle-session-secret-32-bytes-minimum");
        ReflectionTestUtils.setField(jwt, "expirationTime", 86_400_000L);
        ReflectionTestUtils.setField(jwt, "refreshExpirationTime", 604_800_000L);
        ReflectionTestUtils.setField(jwt, "slidingWindowThreshold", 700_000L);
        ReflectionTestUtils.setField(jwt, "jwtRedisService", redis); jwt.init();
        ReflectionTestUtils.setField(controller, "jwtUtils", jwt);
        at(0);
    }
    @AfterEach void restore() { ReflectionTestUtils.setField(SystemSettingsConfig.class, "currentSettings", original); }
    private void at(int minutes) { ReflectionTestUtils.setField(redis, "clock", Clock.fixed(Instant.ofEpochMilli(start + minutes * 60_000L), ZoneOffset.UTC)); }
    private Map<String, Object> claims(String role) {
        return new HashMap<>(Map.of("sid", jwt.createSession(), "username", "idle-test", "role", role, "userId", 7L));
    }

    @Test void refreshingBothTokensDoesNotResetIdleDeadlineForAnyRole() {
        for (String role : new String[]{"ROLE_ADMIN", "ROLE_STUDENT", "ROLE_COMPANY", "ROLE_TEACHER_COLLEGE", "ROLE_TEACHER_DEPARTMENT", "ROLE_TEACHER_COUNSELOR"}) {
            at(0); var claims = claims(role);
            String refresh = jwt.generateRefreshToken(claims);
            at(14); var result = controller.refreshToken(Map.of("refreshToken", refresh));
            assertEquals(200, result.getCode());
            var tokens = (Map<?, ?>) result.getData();
            assertEquals(claims.get("sid"), jwt.parseToken((String) tokens.get("accessToken")).get("sid"));
            assertTrue(jwt.isSessionActive(jwt.parseToken((String) tokens.get("accessToken"))));
            at(15);
            assertFalse(jwt.isSessionActive(jwt.parseToken((String) tokens.get("accessToken"))));
            var expired = controller.refreshToken(Map.of("refreshToken", (String) tokens.get("refreshToken")));
            assertEquals(401, expired.getCode());
            assertEquals(Map.of("errorCode", "SESSION_TIMEOUT"), expired.getData());
        }
    }

    @Test void pollingDoesNotRenewButUserActivityDoesAndCannotReviveAnExpiredSession() {
        var claims = claims("ROLE_STUDENT");
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwt.generateAccessToken(claims));
        at(14);
        var status = (Map<?, ?>) controller.sessionStatus(request).getData();
        assertEquals(start, status.get("lastActivityAt"));
        var active = (Map<?, ?>) controller.sessionActivity(request).getData();
        assertEquals(start + 14 * 60_000L, active.get("lastActivityAt"));
        at(28); assertEquals(200, controller.sessionStatus(request).getCode());
        at(29); assertEquals(401, controller.sessionActivity(request).getCode());
        assertEquals(401, controller.sessionStatus(request).getCode());
    }

    @Test void legacySessionsWithoutServerActivityRequireLogin() {
        var claims = claims("ROLE_ADMIN"); claims.put("sid", "previous-untracked-session");
        assertEquals(401, controller.refreshToken(Map.of("refreshToken", jwt.generateRefreshToken(claims))).getCode());
    }
}
