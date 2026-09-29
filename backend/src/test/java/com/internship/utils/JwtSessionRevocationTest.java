package com.internship.utils;

import com.internship.service.JwtRedisService;
import com.internship.controller.AuthController;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class JwtSessionRevocationTest {
    @Test void logoutRevokesRefreshAndRotatedAccessButPreservesOtherDevice() {
        var jwt = new JwtUtils();
        ReflectionTestUtils.setField(jwt, "secretKeyString", "unit-test-secret-key-32-bytes-minimum-1234567890");
        ReflectionTestUtils.setField(jwt, "expirationTime", 3600000L);
        ReflectionTestUtils.setField(jwt, "refreshExpirationTime", 86400000L);
        ReflectionTestUtils.setField(jwt, "jwtRedisService", new JwtRedisService());
        jwt.init();
        Map<String,Object> claims = Map.of("username", "alice", "userId", 1L, "role", "ROLE_STUDENT", "sid", "device1");
        String access = jwt.generateAccessToken(claims);
        String refresh = jwt.generateRefreshToken(claims);
        var controller = new AuthController();
        ReflectionTestUtils.setField(controller, "jwtUtils", jwt);
        var rotated = controller.refreshToken(Map.of("refreshToken", refresh));
        assertEquals(200, rotated.getCode());
        String rotatedAccess = (String)((Map<?,?>)rotated.getData()).get("accessToken");
        String other = jwt.generateAccessToken(Map.of("username", "alice", "sid", "device2"));
        jwt.revokeSession(access);
        assertThrows(JwtUtils.JwtException.class, () -> jwt.parseToken(access));
        assertThrows(JwtUtils.JwtException.class, () -> jwt.parseToken(refresh));
        assertThrows(JwtUtils.JwtException.class, () -> jwt.parseToken(rotatedAccess));
        assertEquals("alice", jwt.parseToken(other).get("username"));
        assertNotEquals(200, controller.refreshToken(Map.of("refreshToken", refresh)).getCode());
        String legacy = jwt.generateRefreshToken(Map.of("username", "alice", "userId", 1L, "role", "ROLE_STUDENT"));
        assertNotEquals(200, controller.refreshToken(Map.of("refreshToken", legacy)).getCode());
    }
}
