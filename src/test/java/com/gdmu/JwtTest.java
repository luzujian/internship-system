package com.gdmu;

import com.gdmu.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JWT 工具类集成测试
 */
@SpringBootTest
public class JwtTest {

    @Autowired
    private JwtUtils jwtUtils;

    /**
     * 测试 JwtUtils 生成访问令牌功能
     */
    @Test
    public void testGenerateAccessToken() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", 1);
        claims.put("username", "admin");
        claims.put("role", "ROLE_ADMIN");

        String token = jwtUtils.generateAccessToken(claims);
        System.out.println("生成的令牌：" + token);
        assertNotNull(token, "生成的令牌不应为 null");
        assertFalse(token.isEmpty(), "生成的令牌不应为空字符串");
    }

    /**
     * 测试 JwtUtils 解析令牌功能
     */
    @Test
    public void testParseToken() {
        // 首先生成一个令牌
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", 1);
        claims.put("username", "admin");
        claims.put("role", "ROLE_ADMIN");

        String token = jwtUtils.generateAccessToken(claims);
        System.out.println("用于解析的令牌：" + token);

        // 然后解析这个令牌
        Claims parsedClaims = jwtUtils.parseToken(token);
        System.out.println("解析得到的 Claims: " + parsedClaims);
        assertNotNull(parsedClaims, "解析得到的 Claims 不应为 null");
        assertEquals(1, parsedClaims.get("id"), "解析得到的 id 应与设置的值一致");
        assertEquals("admin", parsedClaims.get("username"), "解析得到的 username 应与设置的值一致");
        assertEquals("ROLE_ADMIN", parsedClaims.get("role"), "解析得到的 role 应与设置的值一致");
    }

    /**
     * 测试令牌验证功能
     */
    @Test
    public void testValidateToken() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", 1);
        claims.put("username", "admin");
        claims.put("role", "ROLE_ADMIN");

        String validToken = jwtUtils.generateAccessToken(claims);

        boolean isValid = jwtUtils.validateToken(validToken);
        assertTrue(isValid, "有效的令牌应通过验证");

        // 测试无效令牌
        String invalidToken = validToken + "invalid";
        boolean isInvalid = jwtUtils.validateToken(invalidToken);
        assertFalse(isInvalid, "无效的令牌不应通过验证");
    }
}
