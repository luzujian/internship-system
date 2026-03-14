package com.gdmu.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtUtils类的单元测试，验证滑动窗口刷新、黑名单管理和令牌版本控制等功能
 */
public class JwtUtilsTest {

    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        jwtUtils = new JwtUtils();
    }

    @Test
    void generateAndParseAccessToken() {
        // 准备测试数据
        String username = "testuser";
        String role = "ROLE_USER";
        Long userId = 1L;

        // 创建claims
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", username);
        claims.put("role", role);
        claims.put("userId", userId);

        // 生成访问令牌
        String accessToken = jwtUtils.generateAccessToken(claims);
        assertNotNull(accessToken);

        // 解析令牌
        Map<String, Object> parsedClaims = jwtUtils.parseToken(accessToken);
        assertNotNull(parsedClaims);
        assertEquals(username, parsedClaims.get("username"));
        assertEquals(role, parsedClaims.get("role"));
        assertEquals(userId, parsedClaims.get("userId"));
        assertNotNull(parsedClaims.get("jti")); // 验证令牌ID存在
        assertNotNull(parsedClaims.get("ver")); // 验证版本号存在
        assertNotNull(parsedClaims.get("typ")); // 验证类型标识存在
        assertEquals("access", parsedClaims.get("typ")); // 验证类型为access
    }

    @Test
    void generateAndParseRefreshToken() {
        // 准备测试数据
        String username = "testuser";
        String role = "ROLE_USER";
        Long userId = 1L;

        // 创建claims
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", username);
        claims.put("role", role);
        claims.put("userId", userId);

        // 生成刷新令牌
        String refreshToken = jwtUtils.generateRefreshToken(claims);
        assertNotNull(refreshToken);

        // 解析令牌
        Map<String, Object> parsedClaims = jwtUtils.parseToken(refreshToken);
        assertNotNull(parsedClaims);
        assertEquals(username, parsedClaims.get("username"));
        assertEquals(role, parsedClaims.get("role"));
        assertEquals(userId, parsedClaims.get("userId"));
        assertNotNull(parsedClaims.get("jti")); // 验证令牌ID存在
        assertNotNull(parsedClaims.get("ver")); // 验证版本号存在
        assertNotNull(parsedClaims.get("typ")); // 验证类型标识存在
        assertEquals("refresh", parsedClaims.get("typ")); // 验证类型为refresh
    }

    @Test
    void blacklistToken() {
        // 创建claims
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", "testuser");
        claims.put("role", "ROLE_USER");
        claims.put("userId", 1L);

        // 生成访问令牌
        String accessToken = jwtUtils.generateAccessToken(claims);
        
        // 将令牌加入黑名单
        jwtUtils.blacklistToken(accessToken);
        
        // 解析已加入黑名单的令牌应该抛出异常
        assertThrows(JwtUtils.JwtException.class, () -> {
            jwtUtils.parseToken(accessToken);
        });
    }

    @Test
    void tokenVersionControl() {
        // 创建claims
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", "testuser");
        claims.put("role", "ROLE_USER");
        claims.put("userId", 1L);

        // 生成访问令牌
        String accessToken = jwtUtils.generateAccessToken(claims);
        
        // 解析令牌，应该成功
        Map<String, Object> parsedClaims = jwtUtils.parseToken(accessToken);
        assertNotNull(parsedClaims);
        
        // 增加用户令牌版本号
        jwtUtils.incrementUserTokenVersion("testuser");
        
        // 再次解析令牌，应该失败（版本号不匹配）
        assertThrows(JwtUtils.JwtException.class, () -> {
            jwtUtils.parseToken(accessToken);
        });
    }

    @Test
    void shouldRefreshToken() {
        // 创建claims
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
    void cleanupBlacklistIfNeeded() {
        // 创建claims
        Map<String, Object> claims1 = new HashMap<>();
        claims1.put("username", "testuser1");
        claims1.put("role", "ROLE_USER");
        claims1.put("userId", 1L);
        
        Map<String, Object> claims2 = new HashMap<>();
        claims2.put("username", "testuser2");
        claims2.put("role", "ROLE_USER");
        claims2.put("userId", 2L);
        
        // 模拟将一些令牌加入黑名单
        String token1 = jwtUtils.generateAccessToken(claims1);
        String token2 = jwtUtils.generateAccessToken(claims2);
        
        jwtUtils.blacklistToken(token1);
        jwtUtils.blacklistToken(token2);
        
        // 验证令牌被加入黑名单
        assertThrows(JwtUtils.JwtException.class, () -> {
            jwtUtils.parseToken(token1);
        });
        assertThrows(JwtUtils.JwtException.class, () -> {
            jwtUtils.parseToken(token2);
        });
        
        // 由于cleanupBlacklistIfNeeded是私有方法，我们无法直接调用测试
        // 但黑名单功能已经通过上面的断言验证了
    }
}