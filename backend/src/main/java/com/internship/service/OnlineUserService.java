package com.internship.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.config.SystemSettingsConfig;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Presence is observational: it never renews a login or stores tokens, IPs or page history. */
@Slf4j
@Service
public class OnlineUserService {
    private static final long ONLINE_MILLIS = 180_000;
    private static final long ACTIVE_MILLIS = 120_000;
    private static final long SNAPSHOT_MILLIS = 5_000;
    private static final String INDEX = "jwt:online:index";
    private static final String RECORD = "jwt:online:session:";
    private static final String BLOCKED = "jwt:online:disabled:";
    private static final ObjectMapper JSON = new ObjectMapper();

    // Both reads and writes check authoritative authentication state atomically with Redis.
    private static final String VALIDITY = """
            local function valid(s)
                local last = tonumber(redis.call('GET', 'jwt:session-activity:' .. s.sid))
                if not last or last <= 0 or tonumber(ARGV[1]) - last >= tonumber(ARGV[2]) then return false end
                if redis.call('EXISTS', 'jwt:blacklist:session:' .. s.sid) == 1 then return false end
                if redis.call('EXISTS', 'jwt:online:disabled:' .. s.subjectType .. ':' .. s.subjectUsername) == 1 then return false end
                if redis.call('EXISTS', 'jwt:online:disabled:' .. s.accountType .. ':' .. s.username) == 1 then return false end
                local version = tonumber(redis.call('GET', 'jwt:version:' .. s.subjectUsername) or '1')
                local ownerVersion = tonumber(redis.call('GET', 'jwt:version:' .. s.username) or '1')
                return version <= s.subjectVersion and ownerVersion <= s.ownerVersion
            end
            """;
    private static final DefaultRedisScript<Long> TOUCH = new DefaultRedisScript<>(VALIDITY + """
            local s = cjson.decode(ARGV[3])
            if not valid(s) then return 0 end
            local previous = redis.call('GET', KEYS[2])
            if previous then
                local p = cjson.decode(previous)
                s.lastActivityAt = math.max(s.lastActivityAt, p.lastActivityAt)
                s.lastSeenAt = math.max(s.lastSeenAt, p.lastSeenAt)
            end
            redis.call('PSETEX', KEYS[2], 180000, cjson.encode(s))
            redis.call('ZADD', KEYS[1], s.lastSeenAt, s.sid)
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', tonumber(ARGV[1]) - 180000)
            redis.call('PEXPIRE', KEYS[1], 180000)
            return 1
            """, Long.class);
    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> READ = new DefaultRedisScript<>(VALIDITY + """
            local now = tonumber(ARGV[1])
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now - 180000)
            local ids = redis.call('ZRANGEBYSCORE', KEYS[1], now - 180000, '+inf')
            local result = {}
            for _, sid in ipairs(ids) do
                local raw = redis.call('GET', 'jwt:online:session:' .. sid)
                if raw then
                    local s = cjson.decode(raw)
                    if valid(s) then
                        s.lastActivityAt = tonumber(redis.call('GET', 'jwt:session-activity:' .. sid))
                        table.insert(result, cjson.encode(s))
                    else
                        redis.call('DEL', 'jwt:online:session:' .. sid)
                        redis.call('ZREM', KEYS[1], sid)
                    end
                else
                    redis.call('ZREM', KEYS[1], sid)
                end
            end
            return result
            """, List.class);

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;
    private final JwtRedisService jwtRedisService;
    private final Map<String, Presence> memory = new ConcurrentHashMap<>();
    private final Set<String> blockedMemory = ConcurrentHashMap.newKeySet();
    private final AtomicLong lastWarning = new AtomicLong();
    private Clock clock = Clock.systemUTC();
    private volatile Snapshot cached;

    public OnlineUserService(JwtRedisService jwtRedisService) { this.jwtRedisService = jwtRedisService; }

    record Presence(String sid, String accountType, String username, String displayName, String role,
                    String teacherType, boolean simulation, String subjectType, String subjectUsername,
                    int subjectVersion, int ownerVersion, long lastSeenAt, long lastActivityAt) {}
    public record OnlineUser(String key, String username, String displayName, String accountType,
                             String role, String teacherType, String status, long lastSeenAt,
                             long lastActivityAt, int sessionCount, boolean simulation) {}
    public record Summary(int totalOnline, int active, int idle, Map<String, Integer> byRole, long serverTime) {}
    public record Page(List<OnlineUser> rows, int total, int page, int pageSize, Summary summary, long serverTime) {}
    private record Snapshot(List<OnlineUser> users, Summary summary, long createdAt, long validUntil, int timeoutMinutes) {}

    /** Called only after the existing authenticated session check succeeds. Tracking failures are non-fatal. */
    public void observe(Claims claims, long lastActivityAt) {
        try {
            String sid = text(claims.get("sid")), subject = text(claims.get("username"));
            String subjectRole = text(claims.get("role")), subjectType = accountType(subjectRole);
            if (sid.isBlank() || subject.isBlank() || subjectType.isBlank() || lastActivityAt <= 0) return;
            boolean simulation = Boolean.TRUE.equals(claims.get("isReadOnly"))
                    || claims.containsKey("originalAdminId") || claims.containsKey("originalAdminUsername");
            String username = simulation ? text(claims.get("originalAdminUsername")) : subject;
            if (username.isBlank()) return;
            String name = text(claims.get(simulation ? "originalAdminName" : "name"));
            int version = claims.get("ver") instanceof Number n ? n.intValue() : jwtRedisService.getUserTokenVersion(subject);
            int ownerVersion = simulation
                    ? (claims.get("originalAdminVersion") instanceof Number n ? n.intValue() : jwtRedisService.getUserTokenVersion(username)) : version;
            var presence = new Presence(sid, simulation ? "ADMIN" : subjectType, username,
                    name.isBlank() ? username : name, simulation ? "ROLE_ADMIN" : subjectRole,
                    simulation ? "" : text(claims.get("teacherType")), simulation, subjectType, subject,
                    version, ownerVersion, clock.millis(), lastActivityAt);
            if (stringRedisTemplate != null) {
                Long result = stringRedisTemplate.execute(TOUCH, List.of(INDEX, RECORD + sid),
                        Long.toString(clock.millis()), Long.toString(timeoutMillis()), JSON.writeValueAsString(presence));
                if (result == null) throw new IllegalStateException("在线状态写入失败");
            } else {
                synchronized (memory) {
                    memory.values().removeIf(p -> clock.millis() - p.lastSeenAt() >= ONLINE_MILLIS);
                    if (validMemory(presence)) {
                        Presence previous = memory.get(sid);
                        memory.put(sid, previous == null ? presence : new Presence(presence.sid(), presence.accountType(),
                                presence.username(), presence.displayName(), presence.role(), presence.teacherType(), presence.simulation(),
                                presence.subjectType(), presence.subjectUsername(), presence.subjectVersion(), presence.ownerVersion(),
                                Math.max(previous.lastSeenAt(), presence.lastSeenAt()), Math.max(previous.lastActivityAt(), presence.lastActivityAt())));
                    }
                }
            }
        } catch (Exception e) { warn(e); }
    }

    /** A commit-only hook for status changes, renames and deletion; no authentication behavior changes. */
    public void accountChanged(String type, String oldUsername, String newUsername, Integer status) {
        Runnable action = () -> {
            try {
                if (oldUsername != null && !oldUsername.equals(newUsername)) setBlocked(type, oldUsername, true);
                if (newUsername != null && status != null) setBlocked(type, newUsername, status != 1);
                cached = null;
            } catch (Exception e) { warn(e); }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { action.run(); }
            });
        } else action.run();
    }

    private void setBlocked(String type, String username, boolean blocked) {
        if (username.isBlank()) return;
        String key = type + ":" + username;
        if (stringRedisTemplate != null) {
            if (blocked) stringRedisTemplate.opsForValue().set(BLOCKED + key, "1");
            else stringRedisTemplate.delete(BLOCKED + key);
        } else {
            synchronized (memory) {
                if (blocked) blockedMemory.add(key); else blockedMemory.remove(key);
                if (blocked) memory.values().removeIf(p -> key.equals(p.accountType() + ":" + p.username())
                        || key.equals(p.subjectType() + ":" + p.subjectUsername()));
            }
        }
    }

    public Summary summary() { return snapshot().summary(); }

    public Page list(String keyword, String role, String status, int page, int pageSize) {
        String search = text(keyword).trim().toLowerCase(Locale.ROOT);
        String filterRole = text(role), filterStatus = text(status);
        if (search.length() > 100 || page < 1 || pageSize < 1 || pageSize > 100
                || (!filterRole.isEmpty() && !Set.of("STUDENT", "TEACHER", "COMPANY", "ADMIN").contains(filterRole))
                || (!filterStatus.isEmpty() && !Set.of("ACTIVE", "IDLE").contains(filterStatus))) {
            throw new IllegalArgumentException("在线用户筛选参数无效");
        }
        Snapshot snapshot = snapshot();
        List<OnlineUser> filtered = snapshot.users().stream()
                .filter(u -> filterRole.isEmpty() || filterRole.equals(u.accountType()))
                .filter(u -> filterStatus.isEmpty() || filterStatus.equals(u.status()))
                .filter(u -> search.isEmpty() || u.username().toLowerCase(Locale.ROOT).contains(search)
                        || u.displayName().toLowerCase(Locale.ROOT).contains(search)).toList();
        int start = (int) Math.min((long) (page - 1) * pageSize, filtered.size());
        return new Page(filtered.subList(start, Math.min(start + pageSize, filtered.size())), filtered.size(),
                page, pageSize, snapshot.summary(), snapshot.summary().serverTime());
    }

    private synchronized Snapshot snapshot() {
        long now = clock.millis();
        int timeout = SystemSettingsConfig.getSessionTimeout();
        Snapshot current = cached;
        if (current != null && current.timeoutMinutes() == timeout && now - current.createdAt() >= 0
                && now < current.validUntil()) return current;
        List<Presence> sessions = readSessions(now);
        long validUntil = now + SNAPSHOT_MILLIS;
        for (Presence presence : sessions) {
            validUntil = Math.min(validUntil, Math.min(presence.lastSeenAt() + ONLINE_MILLIS, presence.lastActivityAt() + timeoutMillis()));
            if (now < presence.lastActivityAt() + ACTIVE_MILLIS) validUntil = Math.min(validUntil, presence.lastActivityAt() + ACTIVE_MILLIS);
        }
        Map<String, List<Presence>> grouped = new HashMap<>();
        sessions.forEach(p -> grouped.computeIfAbsent(p.accountType() + ":" + p.username(), key -> new ArrayList<>()).add(p));
        List<OnlineUser> users = new ArrayList<>();
        Map<String, Integer> byRole = new LinkedHashMap<>();
        for (String type : List.of("STUDENT", "TEACHER", "COMPANY", "ADMIN")) byRole.put(type, 0);
        int active = 0;
        for (var group : grouped.entrySet()) {
            List<Presence> entries = group.getValue();
            Presence latest = entries.stream().max(Comparator.comparingLong(Presence::lastSeenAt)).orElseThrow();
            String name = entries.stream().filter(p -> !p.displayName().equals(p.username()))
                    .max(Comparator.comparingLong(Presence::lastSeenAt)).map(Presence::displayName).orElse(latest.displayName());
            long lastActivity = entries.stream().mapToLong(Presence::lastActivityAt).max().orElseThrow();
            String state = now - lastActivity < ACTIVE_MILLIS ? "ACTIVE" : "IDLE";
            if (state.equals("ACTIVE")) active++;
            users.add(new OnlineUser(group.getKey(), latest.username(), name, latest.accountType(), latest.role(),
                    latest.teacherType(), state, latest.lastSeenAt(), lastActivity, entries.size(),
                    entries.stream().anyMatch(Presence::simulation)));
            byRole.merge(latest.accountType(), 1, Integer::sum);
        }
        users.sort(Comparator.comparingLong(OnlineUser::lastActivityAt).reversed().thenComparing(OnlineUser::key));
        cached = new Snapshot(List.copyOf(users), new Summary(users.size(), active, users.size() - active,
                Collections.unmodifiableMap(byRole), now), now, validUntil, timeout);
        return cached;
    }

    private List<Presence> readSessions(long now) {
        if (stringRedisTemplate == null) {
            synchronized (memory) {
                memory.values().removeIf(p -> now - p.lastSeenAt() >= ONLINE_MILLIS || !validMemory(p));
                return List.copyOf(memory.values());
            }
        }
        List<?> rows = stringRedisTemplate.execute(READ, List.of(INDEX), Long.toString(now), Long.toString(timeoutMillis()));
        if (rows == null) throw new IllegalStateException("在线状态暂时不可用");
        List<Presence> sessions = new ArrayList<>();
        for (Object row : rows) {
            try { sessions.add(JSON.readValue(row.toString(), Presence.class)); }
            catch (Exception e) { throw new IllegalStateException("在线状态读取失败", e); }
        }
        return sessions;
    }

    private boolean validMemory(Presence p) {
        return !blockedMemory.contains(p.accountType() + ":" + p.username())
                && !blockedMemory.contains(p.subjectType() + ":" + p.subjectUsername())
                && !jwtRedisService.isInBlacklist("session:" + p.sid())
                && jwtRedisService.checkSession(p.sid(), timeoutMillis(), false) > 0
                && jwtRedisService.getUserTokenVersion(p.subjectUsername()) <= p.subjectVersion()
                && jwtRedisService.getUserTokenVersion(p.username()) <= p.ownerVersion();
    }

    private long timeoutMillis() { return SystemSettingsConfig.getSessionTimeout() * 60_000L; }
    private static String text(Object value) { return value instanceof String s ? s : ""; }
    private static String accountType(String role) {
        if (role.startsWith("ROLE_TEACHER")) return "TEACHER";
        return switch (role) {
            case "ROLE_STUDENT" -> "STUDENT";
            case "ROLE_COMPANY" -> "COMPANY";
            case "ROLE_ADMIN" -> "ADMIN";
            default -> "";
        };
    }
    private void warn(Exception error) {
        long now = clock.millis(), previous = lastWarning.get();
        if (now - previous > 60_000 && lastWarning.compareAndSet(previous, now)) {
            log.warn("在线统计暂时不可用，不影响原有会话检查：{}", error.getClass().getSimpleName());
        }
    }
}
