package com.gdmu;

import com.gdmu.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.crypto.SecretKey;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class JwtTest {

    @Autowired
    private JwtUtils jwtUtils;

    /**
     * 测试JwtUtils生成令牌功能
     */
    @Test
    public void testGenerateToken() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", 1);
        claims.put("username", "admin");

        String token = jwtUtils.generateAccessToken(claims);
        System.out.println("生成的令牌: " + token);
        assertNotNull(token, "生成的令牌不应为null");
        assertFalse(token.isEmpty(), "生成的令牌不应为空字符串");
    }

    /**
     * 测试JwtUtils解析令牌功能
     */
    @Test
    public void testParseToken() {
        // 首先生成一个令牌
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", 1);
        claims.put("username", "admin");
        String token = jwtUtils.generateAccessToken(claims);
        System.out.println("用于解析的令牌: " + token);

        // 然后解析这个令牌
        Claims parsedClaims = jwtUtils.parseToken(token);
        System.out.println("解析得到的Claims: " + parsedClaims);
        assertNotNull(parsedClaims, "解析得到的Claims不应为null");
        assertEquals(1, parsedClaims.get("id"), "解析得到的id应与设置的值一致");
        assertEquals("admin", parsedClaims.get("username"), "解析得到的username应与设置的值一致");
    }

    /**
     * 测试令牌验证功能
     */
    @Test
    public void testValidateToken() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", 1);
        claims.put("username", "admin");
        String validToken = jwtUtils.generateAccessToken(claims);

        boolean isValid = jwtUtils.validateToken(validToken);
        assertTrue(isValid, "有效的令牌应通过验证");

        // 测试无效令牌
        String invalidToken = validToken + "invalid";
        boolean isInvalid = jwtUtils.validateToken(invalidToken);
        assertFalse(isInvalid, "无效的令牌不应通过验证");
    }
}