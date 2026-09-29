package com.internship.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Shared revocation state fails closed; memory mode requires Redis to be absent. */
@Service
public class JwtRedisService {
    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;
    private final ConcurrentHashMap<String, Long> tokenBlacklistMemory = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> userTokenVersionsMemory = new ConcurrentHashMap<>();
    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";
    private static final String VERSION_PREFIX = "jwt:version:";

    public void blacklistToken(String jwtId, long expiryTime) {
        long ttl = expiryTime - System.currentTimeMillis();
        if (ttl <= 0) return;
        if (stringRedisTemplate != null) {
            stringRedisTemplate.opsForValue().set(BLACKLIST_PREFIX + jwtId, Long.toString(expiryTime), ttl, TimeUnit.MILLISECONDS);
        } else {
            tokenBlacklistMemory.put(jwtId, expiryTime);
        }
    }

    public boolean isInBlacklist(String jwtId) {
        if (stringRedisTemplate != null) {
            return stringRedisTemplate.opsForValue().get(BLACKLIST_PREFIX + jwtId) != null;
        }
        Long expiry = tokenBlacklistMemory.get(jwtId);
        if (expiry == null) return false;
        if (expiry <= System.currentTimeMillis()) {
            tokenBlacklistMemory.remove(jwtId, expiry);
            return false;
        }
        return true;
    }

    public int getUserTokenVersion(String username) {
        if (stringRedisTemplate == null) return userTokenVersionsMemory.computeIfAbsent(username, key -> 1);
        String key = VERSION_PREFIX + username;
        stringRedisTemplate.opsForValue().setIfAbsent(key, "1");
        String version = stringRedisTemplate.opsForValue().get(key);
        if (version == null) throw new IllegalStateException("认证版本状态不可用");
        return Integer.parseInt(version);
    }

    public int incrementUserTokenVersion(String username) {
        if (stringRedisTemplate == null) {
            return userTokenVersionsMemory.compute(username, (key, current) -> Math.addExact(current == null ? 1 : current, 1));
        }
        String key = VERSION_PREFIX + username;
        stringRedisTemplate.opsForValue().setIfAbsent(key, "1");
        Long version = stringRedisTemplate.opsForValue().increment(key);
        if (version == null) throw new IllegalStateException("认证版本状态不可用");
        return Math.toIntExact(version);
    }

    public void deleteUserTokenVersion(String username) {
        // Preserve a tombstone so recreating an account cannot revive its old tokens.
        incrementUserTokenVersion(username);
    }
}
