package com.gdmu.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import com.gdmu.service.JwtRedisService;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * JwtUtils 类的单元测试，验证滑动窗口刷新、黑名单管理和令牌版本控制等功能
 */
public class JwtUtilsTest {

    private JwtUtils jwtUtils;

    @Mock
    private JwtRedisService jwtRedisService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        jwtUtils = new JwtUtils();

        // 使用反射注入 mock 的 JwtRedisService
        try {
            java.lang.reflect.Field field = JwtUtils.class.getDeclaredField("jwtRedisService");
            field.setAccessible(true);
            field.set(jwtUtils, jwtRedisService);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject JwtRedisService", e);
        }

        // 初始化 JwtUtils（会调用 @PostConstruct 方法）
        jwtUtils.init();

        // 配置 mock 行为
        when(jwtRedisService.getUserTokenVersion(anyString())).thenReturn(1);
        when(jwtRedisService.isInBlacklist(anyString())).thenReturn(false);
    }

    @Test
    void generateAndParseAccessToken() {
        // 准备测试数据
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", "testuser");
        claims.put("role", "ROLE_USER");
        claims.put("userId", 1L);

        // 生成访问令牌
        String accessToken = jwtUtils.generateAccessToken(claims);
        assertNotNull(accessToken);

        // 解析令牌
        io.jsonwebtoken.Claims parsedClaims = jwtUtils.parseToken(accessToken);
        assertNotNull(parsedClaims);
        assertEquals("testuser", parsedClaims.get("username"));
        assertEquals("ROLE_USER", parsedClaims.get("role"));
        assertEquals(1L, parsedClaims.get("userId"));
        assertNotNull(parsedClaims.get("jti")); // 验证令牌 ID 存在
        assertNotNull(parsedClaims.get("ver")); // 验证版本号存在
        assertNotNull(parsedClaims.get("typ")); // 验证类型标识存在
        assertEquals("access", parsedClaims.get("typ")); // 验证类型为 access
    }

    @Test
    void generateAndParseRefreshToken() {
        // 准备测试数据
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", "testuser");
        claims.put("role", "ROLE_USER");
        claims.put("userId", 1L);

        // 生成刷新令牌
        String refreshToken = jwtUtils.generateRefreshToken(claims);
        assertNotNull(refreshToken);

        // 解析令牌
        io.jsonwebtoken.Claims parsedClaims = jwtUtils.parseToken(refreshToken);
        assertNotNull(parsedClaims);
        assertEquals("testuser", parsedClaims.get("username"));
        assertEquals("ROLE_USER", parsedClaims.get("role"));
        assertEquals(1L, parsedClaims.get("userId"));
        assertNotNull(parsedClaims.get("jti")); // 验证令牌 ID 存在
        assertNotNull(parsedClaims.get("ver")); // 验证版本号存在
        assertNotNull(parsedClaims.get("typ")); // 验证类型标识存在
        assertEquals("refresh", parsedClaims.get("typ")); // 验证类型为 refresh
    }

    @Test
    void blacklistToken() {
        // 准备测试数据
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", "testuser");
        claims.put("role", "ROLE_USER");
        claims.put("userId", 1L);

        // 生成访问令牌
        String accessToken = jwtUtils.generateAccessToken(claims);

        // 获取令牌 ID
        String jwtId = io.jsonwebtoken.Jwts.parser()
                .verifyWith(jwtUtils.getSecretKey())
                .build()
                .parseSignedClaims(accessToken)
                .getPayload()
                .getId();

        assertNotNull(jwtId);

        // 配置 mock 行为 - 模拟令牌在黑名单中
        when(jwtRedisService.isInBlacklist(jwtId)).thenReturn(true);

        // 验证已加入黑名单的令牌解析应该抛出异常
        assertThrows(JwtUtils.JwtException.class, () -> {
            jwtUtils.parseToken(accessToken);
        });
    }

    @Test
    void tokenVersionControl() {
        // 准备测试数据
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", "testuser");
        claims.put("role", "ROLE_USER");
        claims.put("userId", 1L);

        // 配置初始版本号为 1
        when(jwtRedisService.getUserTokenVersion("testuser")).thenReturn(1);

        // 生成访问令牌
        String accessToken = jwtUtils.generateAccessToken(claims);

        // 解析令牌，应该成功
        io.jsonwebtoken.Claims parsedClaims = jwtUtils.parseToken(accessToken);
        assertNotNull(parsedClaims);

        // 配置 mock 返回更高的版本号（模拟 incrementUserTokenVersion 被调用）
        when(jwtRedisService.getUserTokenVersion("testuser")).thenReturn(2);

        // 再次解析令牌，应该失败（版本号不匹配）
        assertThrows(JwtUtils.JwtException.class, () -> {
            jwtUtils.parseToken(accessToken);
        });
    }

    @Test
    void shouldRefreshToken() {
        // 准备测试数据
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", "testuser");
        claims.put("role", "ROLE_USER");
        claims.put("userId", 1L);

        // 生成访问令牌（正常情况下不应该刷新）
        String accessToken = jwtUtils.generateAccessToken(claims);
        boolean shouldRefresh = jwtUtils.shouldRefreshToken(accessToken);

        // 新生成的令牌不应该需要刷新
        assertFalse(shouldRefresh);
    }

    @Test
    void incrementUserTokenVersion() {
        // 配置 mock 返回值
        when(jwtRedisService.incrementUserTokenVersion("testuser")).thenReturn(2);

        // 调用方法
        int newVersion = jwtUtils.incrementUserTokenVersion("testuser");

        // 验证返回值
        assertEquals(2, newVersion);

        // 验证 mock 被调用
        verify(jwtRedisService, times(1)).incrementUserTokenVersion("testuser");
    }
}
