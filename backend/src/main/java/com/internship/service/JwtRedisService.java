package com.internship.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.util.List;
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
    private static final String SESSION_PREFIX = "jwt:session-activity:";
    private final ConcurrentHashMap<String, SessionActivity> sessionsMemory = new ConcurrentHashMap<>();
    private Clock clock = Clock.systemUTC();
    private record SessionActivity(long lastActivity, long expiresAt) {}
    private static final DefaultRedisScript<Long> CHECK_SESSION = new DefaultRedisScript<>("""
            local last = tonumber(redis.call('GET', KEYS[1]))
            if not last or last <= 0 then return -1 end
            local ttl = redis.call('PTTL', KEYS[1])
            if ttl <= 0 then return -1 end
            local now = tonumber(ARGV[1])
            if now - last >= tonumber(ARGV[2]) then
                redis.call('PSETEX', KEYS[1], ttl, '-1')
                return -1
            end
            if ARGV[3] == '1' then
                last = math.max(last, now)
                redis.call('PSETEX', KEYS[1], ttl, tostring(last))
            end
            return last
            """, Long.class);

    public void startSession(String sessionId, long retentionMillis) {
        long now = clock.millis();
        if (stringRedisTemplate != null) {
            stringRedisTemplate.opsForValue().set(SESSION_PREFIX + sessionId, Long.toString(now), retentionMillis, TimeUnit.MILLISECONDS);
        } else {
            sessionsMemory.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
            sessionsMemory.put(sessionId, new SessionActivity(now, now + retentionMillis));
        }
    }

    /** Checks and optional user activity updates are atomic; an expired session stays expired. */
    public long checkSession(String sessionId, long timeoutMillis, boolean userActivity) {
        if (sessionId == null || sessionId.isBlank()) return -1;
        long now = clock.millis();
        if (stringRedisTemplate != null) {
            Long last = stringRedisTemplate.execute(CHECK_SESSION, List.of(SESSION_PREFIX + sessionId),
                    Long.toString(now), Long.toString(timeoutMillis), userActivity ? "1" : "0");
            if (last == null) throw new IllegalStateException("会话状态不可用");
            return last;
        }
        SessionActivity state = sessionsMemory.computeIfPresent(sessionId, (key, current) -> {
            if (current.expiresAt() <= now) return null;
            if (current.lastActivity() <= 0 || now - current.lastActivity() >= timeoutMillis) {
                return new SessionActivity(-1, current.expiresAt());
            }
            return userActivity ? new SessionActivity(Math.max(now, current.lastActivity()), current.expiresAt()) : current;
        });
        return state == null ? -1 : state.lastActivity();
    }

    /** Rotation extends storage retention only, never the last user activity. */
    public void retainSession(String sessionId, long retentionMillis) {
        if (sessionId == null || sessionId.isBlank()) return;
        if (stringRedisTemplate != null) {
            stringRedisTemplate.expire(SESSION_PREFIX + sessionId, retentionMillis, TimeUnit.MILLISECONDS);
        } else {
            sessionsMemory.computeIfPresent(sessionId, (key, state) ->
                    new SessionActivity(state.lastActivity(), clock.millis() + retentionMillis));
        }
    }

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
