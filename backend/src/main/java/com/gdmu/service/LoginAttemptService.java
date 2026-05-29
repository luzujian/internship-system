package com.gdmu.service;

import com.gdmu.config.SystemSettingsConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 登录尝试管理服务
 * 使用 Redis 实现分布式锁 + 原子计数器，解决并发登录时计数器竞态问题
 * Redis 不可用时降级为 JVM 锁 + 原子整数
 */
@Slf4j
@Service
public class LoginAttemptService {

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    private static final String LOCK_KEY_PREFIX = "login:lock:";
    private static final String ATTEMPTS_KEY_PREFIX = "login:attempts:";
    private static final long LOCK_TIMEOUT_SECONDS = 30;

    // --- JVM 降级 ---
    private static final ConcurrentHashMap<String, Object> JVM_LOCKS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AttemptInfo> JVM_ATTEMPTS = new ConcurrentHashMap<>();

    private static class AttemptInfo {
        volatile int count;
        volatile long lockedUntil; // epoch millis
    }

    /**
     * 尝试获取登录锁（同一用户串行化）
     * @return true 表示获取成功，false 表示账号正在被锁定或已被锁定
     */
    public boolean tryAcquireLoginLock(String username) {
        if (stringRedisTemplate != null) {
            return tryAcquireRedisLock(username);
        }
        return tryAcquireJvmLock(username);
    }

    /**
     * 释放登录锁
     */
    public void releaseLoginLock(String username) {
        if (stringRedisTemplate != null) {
            releaseRedisLock(username);
        }
        // JVM 锁在 synchronized 块结束后自动释放
    }

    /**
     * 检查账号是否被锁定
     */
    public boolean isLocked(String username) {
        if (stringRedisTemplate != null) {
            return isLockedRedis(username);
        }
        return isLockedJvm(username);
    }

    /**
     * 获取剩余锁定时间（秒）
     */
    public long getRemainingLockTime(String username) {
        if (stringRedisTemplate != null) {
            return getRemainingLockTimeRedis(username);
        }
        return getRemainingLockTimeJvm(username);
    }

    /**
     * 记录一次登录失败（原子递增），达到阈值时锁定账号
     * @return 当前失败次数
     */
    public int recordFailure(String username) {
        int maxAttempts = SystemSettingsConfig.getMaxLoginAttempts();
        int lockTimeMinutes = SystemSettingsConfig.getLockTime();

        if (stringRedisTemplate != null) {
            return recordFailureRedis(username, maxAttempts, lockTimeMinutes);
        }
        return recordFailureJvm(username, maxAttempts, lockTimeMinutes);
    }

    /**
     * 清除登录失败记录（登录成功时调用）
     */
    public void clearFailures(String username) {
        if (stringRedisTemplate != null) {
            try {
                stringRedisTemplate.delete(ATTEMPTS_KEY_PREFIX + username);
                log.debug("Redis 清除登录失败记录: {}", username);
            } catch (Exception e) {
                log.warn("Redis 清除登录失败记录异常: {}", e.getMessage());
            }
        }
        JVM_ATTEMPTS.remove(username);
    }

    /**
     * 获取对象锁用于 synchronized 块（JVM 降级用）
     */
    public Object getJvmLock(String username) {
        return JVM_LOCKS.computeIfAbsent(username, k -> new Object());
    }

    // ==================== Redis 实现 ====================

    private boolean tryAcquireRedisLock(String username) {
        String lockKey = LOCK_KEY_PREFIX + username;
        try {
            // SETNX + TTL，获取锁成功返回 true
            Boolean acquired = stringRedisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            boolean result = Boolean.TRUE.equals(acquired);
            if (!result) {
                log.debug("Redis 登录锁获取失败（已有请求在处理）: {}", username);
            }
            return result;
        } catch (Exception e) {
            log.warn("Redis 获取登录锁异常，降级到 JVM 锁: {}", e.getMessage());
            return true; // 降级：允许继续
        }
    }

    private void releaseRedisLock(String username) {
        String lockKey = LOCK_KEY_PREFIX + username;
        try {
            stringRedisTemplate.delete(lockKey);
        } catch (Exception e) {
            log.warn("Redis 释放登录锁异常: {}", e.getMessage());
        }
    }

    private boolean isLockedRedis(String username) {
        String lockedKey = "login:locked:" + username;
        try {
            Boolean exists = stringRedisTemplate.hasKey(lockedKey);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.warn("Redis 检查锁定状态异常: {}", e.getMessage());
            return false;
        }
    }

    private long getRemainingLockTimeRedis(String username) {
        String lockedKey = "login:locked:" + username;
        try {
            Long ttl = stringRedisTemplate.getExpire(lockedKey, TimeUnit.SECONDS);
            return (ttl != null && ttl > 0) ? ttl : 0;
        } catch (Exception e) {
            log.warn("Redis 获取剩余锁定时间异常: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Redis 原子递增失败计数
     * 使用 Lua 脚本保证"检查-递增-锁定"的原子性
     */
    private int recordFailureRedis(String username, int maxAttempts, int lockTimeMinutes) {
        String attemptsKey = ATTEMPTS_KEY_PREFIX + username;
        String lockedKey = "login:locked:" + username;

        // Lua 脚本：原子递增 + 达到阈值时设置锁定标记
        String luaScript = """
            local attempts_key = KEYS[1]
            local locked_key = KEYS[2]
            local max_attempts = tonumber(ARGV[1])
            local lock_ttl = tonumber(ARGV[2])

            -- 检查是否已被锁定
            if redis.call('EXISTS', locked_key) == 1 then
                return -1  -- 已被锁定
            end

            -- 原子递增
            local count = redis.call('INCR', attempts_key)

            -- 设置 attempts key 的过期时间（防止内存泄漏）
            if count == 1 then
                redis.call('EXPIRE', attempts_key, lock_ttl + 60)
            end

            -- 达到阈值时设置锁定标记
            if count >= max_attempts then
                redis.call('SETEX', locked_key, lock_ttl, '1')
                redis.call('DEL', attempts_key)
                return -count  -- 返回负数表示已锁定
            end

            return count
        """;

        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(luaScript, Long.class);
            Long result = stringRedisTemplate.execute(script,
                    Collections.singletonList(attemptsKey),
                    Collections.singletonList(lockedKey),
                    String.valueOf(maxAttempts),
                    String.valueOf(lockTimeMinutes * 60));

            if (result == null) {
                log.warn("Redis Lua 脚本执行返回 null，降级到 JVM");
                return recordFailureJvm(username, maxAttempts, lockTimeMinutes);
            }

            int count = result.intValue();
            if (count == -1) {
                log.debug("Redis: 账号已被锁定: {}", username);
                return -1;
            } else if (count < 0) {
                log.warn("Redis: 账号因失败次数过多被锁定: {}, 次数={}", username, -count);
                return -count;
            } else {
                log.debug("Redis: 登录失败计数: {}, username={}", count, username);
                return count;
            }
        } catch (Exception e) {
            log.warn("Redis 记录登录失败异常，降级到 JVM: {}", e.getMessage());
            return recordFailureJvm(username, maxAttempts, lockTimeMinutes);
        }
    }

    // ==================== JVM 降级实现 ====================

    private boolean tryAcquireJvmLock(String username) {
        // JVM 环境下，synchronized 块本身就是本地锁
        // 这里返回 true 让调用方使用 synchronized(getJvmLock(username))
        return true;
    }

    private boolean isLockedJvm(String username) {
        AttemptInfo info = JVM_ATTEMPTS.get(username);
        if (info == null) return false;
        if (info.lockedUntil > 0 && System.currentTimeMillis() < info.lockedUntil) {
            return true;
        }
        // 锁定已过期，清理
        if (info.lockedUntil > 0 && System.currentTimeMillis() >= info.lockedUntil) {
            JVM_ATTEMPTS.remove(username);
            return false;
        }
        return false;
    }

    private long getRemainingLockTimeJvm(String username) {
        AttemptInfo info = JVM_ATTEMPTS.get(username);
        if (info == null || info.lockedUntil <= 0) return 0;
        long remaining = (info.lockedUntil - System.currentTimeMillis()) / 1000;
        return Math.max(remaining, 0);
    }

    private int recordFailureJvm(String username, int maxAttempts, int lockTimeMinutes) {
        // per-username 锁，不同用户并发无阻塞
        Object lock = JVM_LOCKS.computeIfAbsent(username, k -> new Object());
        synchronized (lock) {
            AttemptInfo info = JVM_ATTEMPTS.computeIfAbsent(username, k -> new AttemptInfo());

            // 检查是否已被锁定
            if (info.lockedUntil > 0 && System.currentTimeMillis() < info.lockedUntil) {
                return -1;
            }

            // 如果锁定已过期，重置计数
            if (info.lockedUntil > 0 && System.currentTimeMillis() >= info.lockedUntil) {
                info.count = 0;
                info.lockedUntil = 0;
            }

            info.count++;

            if (info.count >= maxAttempts) {
                info.lockedUntil = System.currentTimeMillis() + (lockTimeMinutes * 60 * 1000L);
                log.warn("JVM 降级: 账号被锁定: {}, 锁定时间={}分钟", username, lockTimeMinutes);
                return -info.count;
            }

            return info.count;
        }
    }
}
