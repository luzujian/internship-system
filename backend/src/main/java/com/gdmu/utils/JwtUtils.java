package com.gdmu.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import javax.crypto.SecretKey;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import com.gdmu.service.JwtRedisService;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * JWT 工具类 - 提供令牌生成、解析和验证功能
 * 实现大厂级别的双令牌机制，包括滑动窗口刷新、令牌黑名单等安全特性
 * 使用 Redis 实现分布式存储，支持多实例部署
 */
@Slf4j
@Component
public class JwtUtils {

    // 从配置文件读取密钥和过期时间
    @Value("${jwt.secret: aXRoZWltYQ==}")
    private String secretKeyString;

    @Value("${jwt.expiration:43200000}")
    private long expirationTime;

    @Value("${jwt.refresh-expiration:604800000}")
    private long refreshExpirationTime;

    // 滑动窗口刷新阈值（秒），当令牌剩余有效期小于此值时才刷新
    @Value("${jwt.sliding-window-threshold:3600}")
    private long slidingWindowThreshold;

    @Getter
    private SecretKey secretKey;

    @Autowired
    private JwtRedisService jwtRedisService;

    /**
     * 初始化 JWT 工具类，准备密钥
     */
    @PostConstruct
    public void init() {
        // 确保密钥长度至少为 32 字节（256 位）
        byte[] keyBytes = secretKeyString.getBytes();
        if (keyBytes.length < 32) {
            byte[] extendedKey = new byte[32];
            System.arraycopy(keyBytes, 0, extendedKey, 0, keyBytes.length);
            // 填充剩余字节
            for (int i = keyBytes.length; i < extendedKey.length; i++) {
                extendedKey[i] = (byte)i;
            }
            secretKey = Keys.hmacShaKeyFor(extendedKey);
        } else {
            secretKey = Keys.hmacShaKeyFor(keyBytes);
        }
        log.info("JWT 工具类初始化完成，使用密钥长度：{} 字节", keyBytes.length);
        log.info("JWT 配置：访问令牌过期时间={}秒，刷新令牌过期时间={}秒，滑动窗口阈值={}秒",
                expirationTime/1000, refreshExpirationTime/1000, slidingWindowThreshold);
    }

    /**
     * 生成 JWT 访问令牌
     * @param claims 令牌中包含的信息
     * @return 生成的 JWT 访问令牌字符串
     */
    public String generateAccessToken(Map<String, Object> claims) {
        Objects.requireNonNull(claims, "JWT claims cannot be null");

        // 创建副本以避免修改原始 claims
        Map<String, Object> tokenClaims = new HashMap<>(claims);

        // 生成唯一的令牌 ID
        String tokenId = generateTokenId();
        tokenClaims.put("jti", tokenId);

        // 添加令牌版本号
        String username = (String) claims.get("username");
        if (username != null) {
            int version = jwtRedisService.getUserTokenVersion(username);
            tokenClaims.put("ver", version);
        }

        // 添加令牌类型标识
        tokenClaims.put("typ", "access");

        // 添加签发时间和过期时间
        Date now = new Date();
        tokenClaims.put("iat", now);

        return Jwts.builder()
                .claims(tokenClaims)
                .expiration(new Date(now.getTime() + expirationTime))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 生成 JWT 刷新令牌
     * @param claims 令牌中包含的信息
     * @return 生成的 JWT 刷新令牌字符串
     */
    public String generateRefreshToken(Map<String, Object> claims) {
        Objects.requireNonNull(claims, "JWT claims cannot be null");

        // 为刷新令牌创建一个新的 claims 副本，移除不必要的信息
        Map<String, Object> refreshClaims = new HashMap<>(claims);

        // 只保留必要的用户标识信息
        refreshClaims.keySet().retainAll(Arrays.asList("username", "role", "userId"));

        // 生成唯一的刷新令牌 ID
        String refreshTokenId = generateTokenId();
        refreshClaims.put("jti", refreshTokenId);

        // 添加令牌版本号，与访问令牌保持一致
        String username = (String) claims.get("username");
        if (username != null) {
            int version = jwtRedisService.getUserTokenVersion(username);
            refreshClaims.put("ver", version);
        }

        // 添加令牌类型标识
        refreshClaims.put("typ", "refresh");

        // 添加签发时间和过期时间
        Date now = new Date();
        refreshClaims.put("iat", now);

        return Jwts.builder()
                .claims(refreshClaims)
                .expiration(new Date(now.getTime() + refreshExpirationTime))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 生成唯一的令牌 ID
     * @return 令牌 ID
     */
    private String generateTokenId() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 解析 JWT 令牌
     * @param token 要解析的 JWT 令牌字符串
     * @return 包含令牌信息的 Claims 对象
     * @throws JwtException 如果令牌无效、过期或格式错误
     */
    public Claims parseToken(String token) {
        Objects.requireNonNull(token, "JWT token cannot be null");

        // 检查令牌是否在黑名单中
        try {
            // 先快速解析不验证签名，获取 jti 进行黑名单检查
            String jwtId = getJwtIdWithoutVerification(token);
            if (StringUtils.hasText(jwtId) && jwtRedisService.isInBlacklist(jwtId)) {
                log.warn("令牌已在黑名单中：{}", jwtId);
                throw new JwtException("Token has been revoked", JwtErrorCode.SIGNATURE_INVALID);
            }
        } catch (Exception e) {
            // 如果无法解析 jti，则继续进行完整验证
            log.debug("无法解析令牌 ID 进行黑名单检查：{}", e.getMessage());
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // 验证令牌版本号
            String username = (String) claims.get("username");
            Integer tokenVersion = claims.get("ver", Integer.class);

            if (StringUtils.hasText(username) && tokenVersion != null) {
                int currentVersion = jwtRedisService.getUserTokenVersion(username);
                // 如果用户的当前版本号大于令牌中的版本号，说明令牌已过期
                if (currentVersion > tokenVersion) {
                    log.warn("令牌版本不匹配，用户 {} 的当前版本：{}, 令牌版本：{}",
                            username, currentVersion, tokenVersion);
                    throw new JwtException("Token version mismatch", JwtErrorCode.SIGNATURE_INVALID);
                }
            }

            return claims;
        } catch (ExpiredJwtException e) {
            log.warn("JWT token has expired: {}", e.getMessage());
            throw new JwtException("Token has expired", JwtErrorCode.EXPIRED);
        } catch (MalformedJwtException e) {
            log.warn("JWT token is malformed: {}", e.getMessage());
            throw new JwtException("Invalid token format", JwtErrorCode.MALFORMED);
        } catch (SignatureException e) {
            log.warn("JWT signature validation failed: {}", e.getMessage());
            throw new JwtException("Invalid token signature", JwtErrorCode.SIGNATURE_INVALID);
        } catch (UnsupportedJwtException e) {
            log.warn("JWT token is unsupported: {}", e.getMessage());
            throw new JwtException("Unsupported token format", JwtErrorCode.UNSUPPORTED);
        } catch (JwtException e) {
            // 直接抛出已有的 JWT 异常
            throw e;
        } catch (Exception e) {
            log.error("JWT token parsing error: {}", e.getMessage());
            throw new JwtException("Failed to parse token", JwtErrorCode.PARSING_FAILED);
        }
    }

    /**
     * 不验证签名，仅解析获取令牌 ID（用于黑名单快速检查）
     * @param token JWT 令牌
     * @return 令牌 ID
     */
    private String getJwtIdWithoutVerification(String token) {
        try {
            // 只解析 payload 部分
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return null;
            }

            // Base64 解码 payload
            String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));

            // 简单解析 JSON 获取 jti
            int jtiIndex = payload.indexOf("\"jti\":");
            if (jtiIndex != -1) {
                jtiIndex += 7; // "jti":" 的长度
                int endIndex = payload.indexOf('"', jtiIndex);
                if (endIndex != -1) {
                    return payload.substring(jtiIndex, endIndex);
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 验证令牌是否有效
     * @param token 要验证的 JWT 令牌
     * @return 如果令牌有效则返回 true，否则返回 false
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    /**
     * 检查令牌是否已过期
     * @param token 要检查的 JWT 令牌
     * @return 如果令牌已过期则返回 true，否则返回 false
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = parseToken(token);
            Date expiration = claims.getExpiration();
            return expiration.before(new Date());
        } catch (JwtException e) {
            if (e.getErrorCode() == JwtErrorCode.EXPIRED) {
                return true;
            }
            throw e;
        }
    }

    /**
     * 检查令牌是否需要刷新（滑动窗口策略）
     * @param token JWT 访问令牌
     * @return 如果令牌剩余有效期小于滑动窗口阈值，则返回 true
     */
    public boolean shouldRefreshToken(String token) {
        try {
            Claims claims = parseToken(token);
            Date expiration = claims.getExpiration();
            long timeToExpiration = expiration.getTime() - System.currentTimeMillis();

            // 如果剩余有效期小于滑动窗口阈值（转换为毫秒），则需要刷新
            return timeToExpiration > 0 && timeToExpiration < slidingWindowThreshold * 1000;
        } catch (JwtException e) {
            // 令牌已过期或无效，需要刷新
            return true;
        }
    }

    /**
     * 将令牌加入黑名单（使其失效）
     * @param token JWT 令牌
     */
    public void blacklistToken(String token) {
        try {
            Claims claims = parseToken(token);
            String jwtId = (String) claims.get("jti");
            Date expiration = claims.getExpiration();

            if (StringUtils.hasText(jwtId)) {
                long expiryTime = expiration.getTime();
                long currentTime = System.currentTimeMillis();

                // 只将有效的令牌加入黑名单，直到其过期时间
                if (expiryTime > currentTime) {
                    jwtRedisService.blacklistToken(jwtId, expiryTime);
                    log.info("令牌已加入黑名单：{}, 过期时间：{}", jwtId, new Date(expiryTime));
                }
            }
        } catch (JwtException e) {
            // 令牌已过期或无效，不需要加入黑名单
            log.debug("忽略黑名单操作，令牌已无效：{}", e.getMessage());
        }
    }

    /**
     * 增加用户的令牌版本号，强制用户所有现有令牌失效
     * 当用户修改密码、登出所有设备等操作时调用
     * @param username 用户名
     * @return 新的版本号
     */
    public int incrementUserTokenVersion(String username) {
        if (username == null) {
            return 0;
        }
        return jwtRedisService.incrementUserTokenVersion(username);
    }

    /**
     * 删除用户的 Token 版本（用户注销时使用）
     * @param username 用户名
     */
    public void deleteUserTokenVersion(String username) {
        if (username != null) {
            jwtRedisService.deleteUserTokenVersion(username);
        }
    }

    /**
     * 获取令牌中的特定声明
     * @param token JWT 令牌
     * @param claimName 声明名称
     * @return 声明值，如果不存在则返回 null
     */
    public <T> T getClaimFromToken(String token, String claimName, Class<T> requiredType) {
        Claims claims = parseToken(token);
        Object value = claims.get(claimName);
        if (value == null) {
            return null;
        }
        try {
            return requiredType.cast(value);
        } catch (ClassCastException e) {
            log.warn("Cannot cast claim '{}' to required type: {}", claimName, requiredType.getName());
            return null;
        }
    }

    /**
     * 自定义 JWT 异常类
     */
    public static class JwtException extends RuntimeException {
        private final JwtErrorCode errorCode;

        public JwtException(String message, JwtErrorCode errorCode) {
            super(message);
            this.errorCode = errorCode;
        }

        public JwtErrorCode getErrorCode() {
            return errorCode;
        }
    }

    /**
     * JWT 错误码枚举
     */
    public enum JwtErrorCode {
        EXPIRED, MALFORMED, SIGNATURE_INVALID, UNSUPPORTED, PARSING_FAILED
    }
}
