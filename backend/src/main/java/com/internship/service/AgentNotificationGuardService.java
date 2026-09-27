package com.internship.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/** A durable claim shared by every teacher/task for one real todo and channel. */
@Service
public class AgentNotificationGuardService {
    private final JdbcTemplate jdbc;
    public AgentNotificationGuardService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record Claim(String key, String token, boolean acquired, String reason) {}

    public static String key(Long studentId, String todoId, String channel) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    (studentId + "\n" + todoId + "\n" + channel).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Claim claim(Long studentId, String todoId, String channel, Long taskId, int cooldownMinutes) {
        String key = key(studentId, todoId, channel);
        // Duplicate initialization must take an exclusive lock directly. INSERT IGNORE
        // followed by SELECT FOR UPDATE can deadlock when several teachers upgrade shared locks.
        jdbc.update("INSERT INTO agent_notification_guard (dedupe_key,student_id,todo_id,channel,state,updated_at) VALUES (?,?,?,?,'IDLE',CURRENT_TIMESTAMP(3)) ON DUPLICATE KEY UPDATE dedupe_key=dedupe_key",
                key, studentId, todoId, channel);
        Map<String,Object> row = jdbc.queryForMap("SELECT state,last_success_at,updated_at FROM agent_notification_guard WHERE dedupe_key=? FOR UPDATE", key);
        String state = String.valueOf(row.get("state"));
        if ("PENDING".equals(state) || "UNKNOWN".equals(state)) {
            return new Claim(key, null, false, "同事项已有发送正在确认或结果待核实，已阻止自动重发。");
        }
        Object last = row.get("last_success_at");
        Instant lastInstant = last instanceof Timestamp t ? t.toInstant()
                : last instanceof java.time.LocalDateTime t ? t.atZone(java.time.ZoneId.systemDefault()).toInstant() : null;
        if (lastInstant != null && lastInstant.plusSeconds(cooldownMinutes * 60L).isAfter(Instant.now())) {
            return new Claim(key, null, false, "同事项已有提醒，仍在统一冷却期内。");
        }
        String token = UUID.randomUUID().toString();
        jdbc.update("UPDATE agent_notification_guard SET state='PENDING',claim_token=?,task_id=?,updated_at=CURRENT_TIMESTAMP(3) WHERE dedupe_key=?", token, taskId, key);
        return new Claim(key, token, true, null);
    }

    /** Joins a station-reminder transaction so insertion and receipt commit together. */
    @Transactional
    public void finish(Claim claim, String state, Long sendLogId) {
        if (claim == null || !claim.acquired()) return;
        if (!java.util.Set.of("SENT", "UNKNOWN", "FAILED", "CANCELLED").contains(state)) throw new IllegalArgumentException("Invalid notification state");
        int updated = jdbc.update("UPDATE agent_notification_guard SET state=?,send_log_id=?,last_success_at=CASE WHEN ?='SENT' THEN CURRENT_TIMESTAMP(3) ELSE last_success_at END,updated_at=CURRENT_TIMESTAMP(3) WHERE dedupe_key=? AND claim_token=? AND state='PENDING'",
                state, sendLogId, state, claim.key(), claim.token());
        if (updated != 1) throw new IllegalStateException("通知占用状态已变化，必须核对发送结果。");
    }
}
