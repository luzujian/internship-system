package com.internship.mapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 自动备份去重逻辑回归测试：每天备份两份 BUG 的验证。
 *
 * <p>根因：旧 {@code updateSchedule} SQL 未包含 {@code last_execution_date} 列，
 * 导致去重标记从未落库；5 分钟轮询窗口（cron {@code 0 0/5 * * * ?} + 5 分钟时间窗）
 * 必然命中 2 次 tick，每次都执行完整备份。</p>
 *
 * <p>只允许显式启用的本机隔离测试库。测试会临时修改
 * {@code backup_schedule(id=1)}，不得指向共享业务库。</p>
 */
class BackupScheduleClaimVerifyTest {

    private static final String USER = "user";

    // 与 BackupScheduleMapper 中完全一致的 SQL（修后版本）
    private static final String UPDATE_SCHEDULE =
            "UPDATE backup_schedule SET enabled = ?, frequency = ?, "
                    + "backup_time = ?, retention_days = ?, remark = ?, "
                    + "last_execution_date = ? WHERE id = 1";
    private static final String CLAIM_TODAY =
            "UPDATE backup_schedule SET last_execution_date = CURDATE() "
                    + "WHERE id = 1 AND (last_execution_date IS NULL OR last_execution_date <> CURDATE())";
    private static final String CLEAR_TODAY =
            "UPDATE backup_schedule SET last_execution_date = NULL WHERE id = 1";

    // 修前版本（缺 last_execution_date 列），用于复现 BUG
    private static final String OLD_UPDATE_SCHEDULE =
            "UPDATE backup_schedule SET enabled = ?, frequency = ?, "
                    + "backup_time = ?, retention_days = ?, remark = ? WHERE id = 1";

    private Connection conn;
    private Boolean enabled;
    private String frequency;
    private String backupTime;
    private Integer retentionDays;
    private String remark;
    private Date origLastExecutionDate;

    private Connection newConnection() throws Exception {
        String database = System.getenv("BACKUP_SCHEDULE_TEST_DB");
        String password = System.getenv("LOCAL_DB_PASSWORD");
        Assumptions.assumeTrue("true".equalsIgnoreCase(System.getenv("BACKUP_SCHEDULE_LOCAL_DB_TESTS")),
                "必须显式启用本机隔离备份测试");
        Assumptions.assumeTrue(database != null && database.matches("[A-Za-z0-9_]+")
                        && !"internship".equalsIgnoreCase(database),
                "BACKUP_SCHEDULE_TEST_DB 必须是独立测试库，不能是业务库 internship");
        Assumptions.assumeTrue(password != null && !password.isBlank(), "缺少 LOCAL_DB_PASSWORD");
        String url = "jdbc:mysql://127.0.0.1:3306/" + database
                + "?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8&useUnicode=true";
        return DriverManager.getConnection(url, USER, password);
    }

    @BeforeEach
    void connectAndSnapshot() {
        try {
            conn = newConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT enabled, frequency, backup_time, retention_days, remark, "
                            + "last_execution_date FROM backup_schedule WHERE id = 1");
                 ResultSet rs = ps.executeQuery()) {
                Assumptions.assumeTrue(rs.next(), "backup_schedule(id=1) 不存在，跳过验证");
                enabled = rs.getBoolean(1);
                frequency = rs.getString(2);
                backupTime = rs.getString(3);
                retentionDays = (Integer) rs.getObject(4);
                remark = rs.getString(5);
                origLastExecutionDate = rs.getDate(6);
            }
            System.out.println("[VERIFY] 原始 last_execution_date=" + origLastExecutionDate);
        } catch (Exception e) {
            Assumptions.abort("连不上验证库，跳过验证: " + e.getMessage());
        }
    }

    @AfterEach
    void restore() throws Exception {
        if (conn == null || conn.isClosed()) {
            return;
        }
        try (PreparedStatement ps = conn.prepareStatement(UPDATE_SCHEDULE)) {
            ps.setBoolean(1, enabled);
            ps.setString(2, frequency);
            ps.setString(3, backupTime);
            ps.setObject(4, retentionDays);
            ps.setString(5, remark);
            ps.setDate(6, origLastExecutionDate);
            ps.executeUpdate();
        }
        System.out.println("[VERIFY] 已恢复 last_execution_date=" + origLastExecutionDate);
        conn.close();
    }

    private Date queryLastExecutionDate() throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT last_execution_date FROM backup_schedule WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getDate(1);
        }
    }

    @Test
    void oldSqlCannotPersistMark_newSqlCan() throws Exception {
        // 1) 先把标记清零（模拟每天第一次进入窗口）
        try (PreparedStatement ps = conn.prepareStatement(CLEAR_TODAY)) {
            ps.executeUpdate();
        }
        assertNull(queryLastExecutionDate());

        // 2) 复现 BUG：旧 SQL 即使“想”标记，也落不下去（列根本不在 SET 里）
        try (PreparedStatement ps = conn.prepareStatement(OLD_UPDATE_SCHEDULE)) {
            ps.setBoolean(1, enabled);
            ps.setString(2, frequency);
            ps.setString(3, backupTime);
            ps.setObject(4, retentionDays);
            ps.setString(5, remark);
            ps.executeUpdate();
        }
        assertNull(queryLastExecutionDate(), "旧SQL写不进标记，这就是每天备份两份的根因");
        System.out.println("[VERIFY] 复现成功：旧 updateSchedule 确实无法持久化 last_execution_date");

        // 3) 验证修复：新 SQL 可以持久化（用过去的固定日期，不影响今日真实备份）
        Date probe = Date.valueOf("2020-01-01");
        try (PreparedStatement ps = conn.prepareStatement(UPDATE_SCHEDULE)) {
            ps.setBoolean(1, enabled);
            ps.setString(2, frequency);
            ps.setString(3, backupTime);
            ps.setObject(4, retentionDays);
            ps.setString(5, remark);
            ps.setDate(6, probe);
            assertEquals(1, ps.executeUpdate());
        }
        assertEquals(probe, queryLastExecutionDate(), "新SQL应能持久化 last_execution_date");
        System.out.println("[VERIFY] 修复有效：新 updateSchedule 可持久化 last_execution_date");
    }

    @Test
    void twoTicksInWindowProduceOnlyOneClaim() throws Exception {
        // 模拟 02:00 和 02:05 两次 tick：第一次抢到，第二次必须抢不到
        try (PreparedStatement ps = conn.prepareStatement(CLEAR_TODAY)) {
            ps.executeUpdate();
        }
        int first, second;
        try (PreparedStatement ps = conn.prepareStatement(CLAIM_TODAY)) {
            first = ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(CLAIM_TODAY)) {
            second = ps.executeUpdate();
        }
        assertEquals(1, first, "窗口内第一次 tick 应抢到执行权");
        assertEquals(0, second, "窗口内第二次 tick 必须跳过，否则就是第二份备份");
        System.out.println("[VERIFY] 两次tick只产生一次占位 first=1 second=0，符合预期（每天只备份一份）");

        // 失败回滚后允许重试
        try (PreparedStatement ps = conn.prepareStatement(CLEAR_TODAY)) {
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(CLAIM_TODAY)) {
            assertEquals(1, ps.executeUpdate(), "回滚后应允许重试");
        }
        System.out.println("[VERIFY] 失败回滚后可重试，符合预期");
    }

    @Test
    void concurrentClaimsElectExactlyOneWinner() throws Exception {
        // 模拟多 tick/多实例并发抢占：10 路并发，只能有 1 个成功
        try (PreparedStatement ps = conn.prepareStatement(CLEAR_TODAY)) {
            ps.executeUpdate();
        }
        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            List<Callable<Integer>> tasks = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                tasks.add(() -> {
                    try (Connection c = newConnection();
                         PreparedStatement ps = c.prepareStatement(CLAIM_TODAY)) {
                        return ps.executeUpdate();
                    }
                });
            }
            int total = 0;
            for (Future<Integer> f : pool.invokeAll(tasks)) {
                total += f.get();
            }
            assertEquals(1, total, "10路并发抢占必须恰好1个成功，否则会重复备份");
            System.out.println("[VERIFY] 10路并发抢占 total=1，符合预期");
        } finally {
            pool.shutdownNow();
        }
    }
}
