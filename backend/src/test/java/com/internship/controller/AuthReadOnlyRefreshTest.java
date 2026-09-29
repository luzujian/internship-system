package com.internship.controller;

import com.internship.entity.Result;
import com.internship.service.JwtRedisService;
import com.internship.utils.JwtUtils;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AuthReadOnlyRefreshTest {

    private AuthController controller;
    private JwtUtils jwt;

    @BeforeEach
    void setUp() {
        JwtRedisService redis = mock(JwtRedisService.class);
        when(redis.getUserTokenVersion(anyString())).thenReturn(1);
        jwt = new JwtUtils();
        ReflectionTestUtils.setField(jwt, "secretKeyString", "unit-test-secret-key-32-bytes-minimum-1234567890");
        ReflectionTestUtils.setField(jwt, "expirationTime", 3_600_000L);
        ReflectionTestUtils.setField(jwt, "refreshExpirationTime", 86_400_000L);
        ReflectionTestUtils.setField(jwt, "slidingWindowThreshold", 172_800L);
        ReflectionTestUtils.setField(jwt, "jwtRedisService", redis);
        jwt.init();
        controller = new AuthController();
        ReflectionTestUtils.setField(controller, "jwtUtils", jwt);
    }

    @Test
    void readOnlyRefreshAndRotationPreserveOriginalAdmin() {
        Map<String, Object> claims = baseClaims();
        claims.put("isReadOnly", true);
        claims.put("originalAdminId", 1L);
        claims.put("originalAdminUsername", "admin");
        String oldRefresh = jwt.generateRefreshToken(claims);

        Result result = controller.refreshToken(Map.of("refreshToken", oldRefresh));

        assertEquals(200, result.getCode());
        Map<?, ?> tokens = (Map<?, ?>) result.getData();
        assertNotEquals(oldRefresh, tokens.get("refreshToken"), "sliding window should rotate the refresh token");
        for (String key : new String[] {"accessToken", "refreshToken"}) {
            var payload = jwt.parseToken((String) tokens.get(key));
            assertEquals(Boolean.TRUE, payload.get("isReadOnly"));
            assertEquals(1L, ((Number) payload.get("originalAdminId")).longValue());
            assertEquals("admin", payload.get("originalAdminUsername"));
        }
    }

    @Test
    void normalRefreshKeepsExplicitFalseMarker() {
        String token = jwt.generateRefreshToken(baseClaims());

        Result result = controller.refreshToken(Map.of("refreshToken", token));

        assertEquals(200, result.getCode());
        Map<?, ?> tokens = (Map<?, ?>) result.getData();
        assertEquals(Boolean.FALSE, jwt.parseToken((String) tokens.get("accessToken")).get("isReadOnly"));
        assertEquals(Boolean.FALSE, jwt.parseToken((String) tokens.get("refreshToken")).get("isReadOnly"));
    }

    @Test
    void legacyUnmarkedRefreshRequiresRelogin() {
        String legacy = Jwts.builder().claims(baseClaims()).claim("typ", "refresh")
                .expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(jwt.getSecretKey()).compact();

        Result result = controller.refreshToken(Map.of("refreshToken", legacy));

        assertEquals(400, result.getCode());
        assertNull(result.getData());
    }

    @Test
    void accessTokenCannotBeUsedToRefresh() {
        Result result = controller.refreshToken(Map.of("refreshToken", jwt.generateAccessToken(baseClaims())));
        assertEquals(400, result.getCode());
    }

    @Test
    void inconsistentReadOnlyOriginCannotDowngrade() {
        Map<String, Object> claims = baseClaims();
        claims.put("isReadOnly", false);
        claims.put("originalAdminId", 1L);
        claims.put("originalAdminUsername", "admin");
        String inconsistent = Jwts.builder().claims(claims).claim("typ", "refresh")
                .expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(jwt.getSecretKey()).compact();

        Result result = controller.refreshToken(Map.of("refreshToken", inconsistent));

        assertEquals(400, result.getCode());
    }

    private Map<String, Object> baseClaims() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", "student");
        claims.put("role", "ROLE_STUDENT");
        claims.put("userId", 7L);
        claims.put("sid", "test-session");
        return claims;
    }
}
