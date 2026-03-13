package com.gdmu.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * JWT Redis 服务
 * 用于在分布式环境中管理 Token 黑名单和版本追踪
 *
 * Redis 不可用时自动降级到内存存储，保证系统可用性
 */
@Slf4j
@Service
public class JwtRedisService {

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    // 内存降级存储（Redis 不可用时使用）
    private final ConcurrentHashMap<String, Long> tokenBlacklistMemory = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> userTokenVersionsMemory = new ConcurrentHashMap<>();

    // Redis Key 前缀
    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";
    private static final String VERSION_PREFIX = "jwt:version:";

    /**
     * 检查 Redis 是否可用
     */
    private boolean isRedisAvailable() {
        try {
            if (redisTemplate == null) {
                return false;
            }
            redisTemplate.getConnectionFactory().getConnection().ping();
            return true;
        } catch (Exception e) {
            log.debug("Redis 不可用，使用内存存储：{}", e.getMessage());
            return false;
        }
    }

    // ==================== Token 黑名单管理 ====================

    /**
     * 将 Token 加入黑名单
     * @param jwtId Token ID
     * @param expiryTime 过期时间（毫秒时间戳）
     */
    public void blacklistToken(String jwtId, long expiryTime) {
        if (isRedisAvailable()) {
            try {
                String key = BLACKLIST_PREFIX + jwtId;
                long ttl = expiryTime - System.currentTimeMillis();
                if (ttl > 0) {
                    stringRedisTemplate.opsForValue().set(key, String.valueOf(expiryTime), ttl, TimeUnit.MILLISECONDS);
                    log.debug("Token 已加入 Redis 黑名单：{}, 过期时间：{}", jwtId, expiryTime);
                }
            } catch (Exception e) {
                log.error("Redis 黑名单操作失败，降级到内存：{}", e.getMessage());
                tokenBlacklistMemory.put(jwtId, expiryTime);
            }
        } else {
            tokenBlacklistMemory.put(jwtId, expiryTime);
            log.debug("Token 已加入内存黑名单：{}", jwtId);
        }
    }

    /**
     * 检查 Token 是否在黑名单中
     * @param jwtId Token ID
     * @return 如果在黑名单中返回 true
     */
    public boolean isInBlacklist(String jwtId) {
        if (isRedisAvailable()) {
            try {
                String key = BLACKLIST_PREFIX + jwtId;
                String value = stringRedisTemplate.opsForValue().get(key);
                if (value != null) {
                    log.debug("Token 在 Redis 黑名单中：{}", jwtId);
                    return true;
                }
                return false;
            } catch (Exception e) {
                log.error("Redis 黑名单查询失败，降级到内存：{}", e.getMessage());
                return isInBlacklistMemory(jwtId);
            }
        } else {
            return isInBlacklistMemory(jwtId);
        }
    }

    /**
     * 检查 Token 是否在内存黑名单中
     */
    private boolean isInBlacklistMemory(String jwtId) {
        Long expiryTime = tokenBlacklistMemory.get(jwtId);
        if (expiryTime == null) {
            return false;
        }
        // 检查是否已过期
        if (expiryTime < System.currentTimeMillis()) {
            tokenBlacklistMemory.remove(jwtId);
            return false;
        }
        return true;
    }

    // ==================== JWT 版本追踪 ====================

    /**
     * 获取用户的 Token 版本号
     * @param username 用户名
     * @return 版本号
     */
    public int getUserTokenVersion(String username) {
        if (isRedisAvailable()) {
            try {
                String key = VERSION_PREFIX + username;
                String version = stringRedisTemplate.opsForValue().get(key);
                if (version != null) {
                    return Integer.parseInt(version);
                }
                // 初始化版本号为 1
                return initializeUserTokenVersion(username);
            } catch (Exception e) {
                log.error("Redis 版本查询失败，降级到内存：{}", e.getMessage());
                return getUserTokenVersionMemory(username);
            }
        } else {
            return getUserTokenVersionMemory(username);
        }
    }

    /**
     * 获取内存中的版本号
     */
    private int getUserTokenVersionMemory(String username) {
        return userTokenVersionsMemory.computeIfAbsent(username, k -> 1);
    }

    /**
     * 初始化用户 Token 版本号
     */
    private int initializeUserTokenVersion(String username) {
        if (isRedisAvailable()) {
            try {
                String key = VERSION_PREFIX + username;
                stringRedisTemplate.opsForValue().set(key, "1");
                log.debug("初始化用户 {} 的 Token 版本为 1", username);
                return 1;
            } catch (Exception e) {
                log.error("Redis 版本初始化失败：{}", e.getMessage());
            }
        }
        return userTokenVersionsMemory.computeIfAbsent(username, k -> 1);
    }

    /**
     * 增加用户 Token 版本号（强制所有现有 Token 失效）
     * @param username 用户名
     * @return 新的版本号
     */
    public int incrementUserTokenVersion(String username) {
        if (isRedisAvailable()) {
            try {
                String key = VERSION_PREFIX + username;
                // 先获取当前版本号
                String currentVersion = stringRedisTemplate.opsForValue().get(key);
                int newVersion;
                if (currentVersion != null) {
                    newVersion = Integer.parseInt(currentVersion) + 1;
                    // 防止整数溢出
                    if (newVersion < 0) {
                        newVersion = 1;
                    }
                } else {
                    newVersion = 1;
                }
                stringRedisTemplate.opsForValue().set(key, String.valueOf(newVersion));
                log.info("用户 {} 的 Token 版本已更新为：{}", username, newVersion);
                return newVersion;
            } catch (Exception e) {
                log.error("Redis 版本递增失败，降级到内存：{}", e.getMessage());
                return incrementUserTokenVersionMemory(username);
            }
        } else {
            return incrementUserTokenVersionMemory(username);
        }
    }

    /**
     * 递增内存中的版本号
     */
    private int incrementUserTokenVersionMemory(String username) {
        Integer currentVersion = userTokenVersionsMemory.get(username);
        if (currentVersion == null) {
            currentVersion = getUserTokenVersionMemory(username);
        }
        int newVersion = currentVersion + 1;
        if (newVersion < 0) {
            newVersion = 1;
        }
        userTokenVersionsMemory.put(username, newVersion);
        log.info("用户 {} 的 Token 版本已更新为：{} (内存)", username, newVersion);
        return newVersion;
    }

    /**
     * 删除用户的 Token 版本（用户注销时使用）
     * @param username 用户名
     */
    public void deleteUserTokenVersion(String username) {
        if (isRedisAvailable()) {
            try {
                String key = VERSION_PREFIX + username;
                stringRedisTemplate.delete(key);
                log.debug("已删除用户 {} 的 Token 版本", username);
            } catch (Exception e) {
                log.error("Redis 版本删除失败：{}", e.getMessage());
            }
        }
        userTokenVersionsMemory.remove(username);
    }
}
