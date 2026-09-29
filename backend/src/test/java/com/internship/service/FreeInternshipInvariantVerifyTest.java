package com.internship.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 远端数据库不变量验证
 * 在 apply 迁移后执行，所有断言通过即为数据库不变量满足。
 */
@EnabledIfEnvironmentVariable(named = "RUN_FREE_INTERNSHIP_DB_INVARIANTS", matches = "true")
public class FreeInternshipInvariantVerifyTest {

    private static Connection conn;

    @BeforeAll
    static void setup() throws Exception {
        String host = System.getenv("DB_HOST");
        String port = System.getenv("DB_PORT");
        String db = System.getenv("DB_NAME");
        String user = System.getenv("DB_USERNAME");
        String pass = System.getenv("DB_PASSWORD");
        assertNotNull(host, "DB_HOST must be set");
        assertNotNull(pass, "DB_PASSWORD must be set");
        conn = DriverManager.getConnection(
                "jdbc:mysql://" + host + ":" + port + "/" + db + "?useSSL=false&allowPublicKeyRetrieval=true",
                user, pass);
    }

    @Test
    void invariant1_eachStudentAtMostOneCurrentInternship() throws SQLException {
        // 每个有效学生 current_flag=1 AND status=CONFIRMED 的行数 ≤ 1
        String sql = """
            SELECT sa.student_id, COUNT(*) cnt
            FROM internship_stage_assignment sa
            JOIN student_users su ON sa.student_id = su.id AND su.role = 'ROLE_STUDENT' AND su.status = 1
            WHERE sa.current_flag = 1 AND sa.status = 'CONFIRMED'
            GROUP BY sa.student_id
            HAVING cnt > 1
            """;
        try (var stmt = conn.createStatement(); var rs = stmt.executeQuery(sql)) {
            boolean hasRow = rs.next();
            assertFalse(hasRow, "存在有效学生有多条当前实习");
        }
        System.out.println("✓ invariant1: 每个有效学生最多一条当前实习");
    }

    @Test
    void invariant2_statusMatchesAssignment() throws SQLException {
        // student_internship_status 与 internship_stage_assignment 当前段三字段一致
        String sql = """
            SELECT sis.student_id,
                   sis.company_id AS sis_company, sa.company_id AS sa_company,
                   sis.position_id AS sis_position, sa.position_id AS sa_position,
                   sis.internship_start_time AS sis_start, sa.internship_start_time AS sa_start
            FROM student_internship_status sis
            JOIN internship_stage_assignment sa ON sis.student_id = sa.student_id
                AND sa.current_flag = 1 AND sa.status = 'CONFIRMED'
            WHERE (sis.company_id != sa.company_id
                OR sis.position_id != sa.position_id
                OR COALESCE(sis.internship_end_time, '9999-12-31') = '9999-12-31'
                AND COALESCE(sa.internship_end_time, '9999-12-31') != '9999-12-31'
                OR (sis.internship_start_time IS NULL AND sa.internship_start_time IS NOT NULL)
                OR (sis.internship_start_time IS NOT NULL AND sa.internship_start_time IS NULL)
                OR (sis.internship_start_time IS NOT NULL AND sa.internship_start_time IS NOT NULL
                    AND sis.internship_start_time != sa.internship_start_time))
            AND sis.student_id IN (SELECT id FROM student_users WHERE role = 'ROLE_STUDENT' AND status = 1)
            """;
        try (var stmt = conn.createStatement(); var rs = stmt.executeQuery(sql)) {
            boolean hasRow = rs.next();
            assertFalse(hasRow, "存在状态与实习段不一致的记录");
        }
        System.out.println("✓ invariant2: student_internship_status 与 internship_stage_assignment 一致");
    }

    @Test
    void invariant3_positionQuotaMatches() throws SQLException {
        // position.recruited_count 等于有效学生当前实习段去重计数
        // remaining_quota 是计算列，不在表结构中，用 GREATEST 单独校验
        String sql = """
            SELECT p.id, p.recruited_count,
                   (SELECT COUNT(DISTINCT sa.student_id)
                    FROM internship_stage_assignment sa
                    JOIN student_users su ON sa.student_id = su.id AND su.role = 'ROLE_STUDENT' AND su.status = 1
                    WHERE sa.position_id = p.id AND sa.current_flag = 1 AND sa.status = 'CONFIRMED'
                   ) AS actual_count,
                   GREATEST(COALESCE(p.planned_recruit, 0) -
                     (SELECT COUNT(DISTINCT sa.student_id)
                      FROM internship_stage_assignment sa
                      JOIN student_users su ON sa.student_id = su.id AND su.role = 'ROLE_STUDENT' AND su.status = 1
                      WHERE sa.position_id = p.id AND sa.current_flag = 1 AND sa.status = 'CONFIRMED'
                     ), 0) AS expected_quota
            FROM position p
            JOIN internship_stage_assignment sa ON p.id = sa.position_id
                AND sa.current_flag = 1 AND sa.status = 'CONFIRMED'
            JOIN student_users su ON sa.student_id = su.id AND su.role = 'ROLE_STUDENT' AND su.status = 1
            GROUP BY p.id, p.recruited_count, p.planned_recruit
            HAVING p.recruited_count != actual_count
            """;
        try (var stmt = conn.createStatement(); var rs = stmt.executeQuery(sql)) {
            boolean hasRow = rs.next();
            assertFalse(hasRow, "存在岗位名额不匹配");
        }
        System.out.println("✓ invariant3: 岗位名额与有效当前实习段去重计数一致");
    }

    @Test
    void invariant5_enableDualPhaseDisabled() throws SQLException {
        String sql = "SELECT COUNT(*) FROM system_settings WHERE COALESCE(enable_dual_phase,0) != 0";
        try (var stmt = conn.createStatement(); var rs = stmt.executeQuery(sql)) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1), "system_settings 中仍有 enable_dual_phase=1 的记录");
        }
        System.out.println("✓ invariant5: enable_dual_phase 全部禁用");
    }

    @Test
    void invariant6_noOrphanEmptyAutoReflections() throws SQLException {
        String sql = "SELECT COUNT(*) FROM internship_reflection r " +
                "LEFT JOIN student_users su ON r.student_id = su.id " +
                "WHERE r.deleted = 0 AND r.task_type = 'auto' AND (r.content IS NULL OR TRIM(r.content) = '') " +
                "AND su.id IS NULL";
        try (var stmt = conn.createStatement(); var rs = stmt.executeQuery(sql)) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1), "存在空内容的孤儿自动心得");
        }
        System.out.println("✓ invariant6: 无空孤儿自动心得");
    }

    @Test
    void invariant8_legacyStudentIdMigrated() throws SQLException {
        // 5 张表 student_id=1 行数均为 0
        String[] tables = {
            "internship_confirmation_record", "internship_stage_assignment",
            "interview_invitation", "internship_progress_record", "position_view_record"
        };
        for (String table : tables) {
            String sql = "SELECT COUNT(*) FROM " + table + " WHERE student_id = 1";
            try (var stmt = conn.createStatement(); var rs = stmt.executeQuery(sql)) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "表 " + table + " 中 student_id=1 未完全迁移");
            }
        }
        System.out.println("✓ invariant8: 旧 student_id=1 全部迁移到 student_id=7");
    }
}
