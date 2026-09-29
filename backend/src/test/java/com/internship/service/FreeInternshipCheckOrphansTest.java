package com.internship.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 查询孤儿 stage_assignment 详情
 */
@EnabledIfEnvironmentVariable(named = "RUN_FREE_INTERNSHIP_DB_INVARIANTS", matches = "true")
public class FreeInternshipCheckOrphansTest {

    private static Connection conn;

    @BeforeAll
    static void setup() throws Exception {
        String host = System.getenv("DB_HOST");
        String port = System.getenv("DB_PORT");
        String db = System.getenv("DB_NAME");
        String user = System.getenv("DB_USERNAME");
        String pass = System.getenv("DB_PASSWORD");
        conn = DriverManager.getConnection(
                "jdbc:mysql://" + host + ":" + port + "/" + db + "?useSSL=false&allowPublicKeyRetrieval=true",
                user, pass);
    }

    @Test
    void checkOrphanStageAssignments() throws SQLException {
        String sql = """
            SELECT sa.id, sa.student_id, sa.status, sa.current_flag, sa.source_type,
                   sa.company_id, sa.position_id, sa.internship_start_time, sa.internship_end_time,
                   sa.confirmation_record_id, sa.stage_no, sa.sequence_no
            FROM internship_stage_assignment sa
            LEFT JOIN student_users su ON sa.student_id = su.id
            WHERE su.id IS NULL
            ORDER BY sa.id
            """;
        try (var stmt = conn.createStatement(); var rs = stmt.executeQuery(sql)) {
            var meta = rs.getMetaData();
            int cols = meta.getColumnCount();
            int count = 0;
            while (rs.next()) {
                count++;
                System.out.println("--- 孤儿记录 " + count + " ---");
                for (int i = 1; i <= cols; i++) {
                    System.out.println("  " + meta.getColumnName(i) + " = " + rs.getString(i));
                }
            }
            System.out.println("\n共 " + count + " 条孤儿 stage_assignment 记录");
        }
    }

    @Test
    void checkStudentUsers() throws SQLException {
        String sql = "SELECT id, student_user_id, username, role, status FROM student_users ORDER BY id";
        try (var stmt = conn.createStatement(); var rs = stmt.executeQuery(sql)) {
            var meta = rs.getMetaData();
            int cols = meta.getColumnCount();
            while (rs.next()) {
                System.out.print("--- ");
                for (int i = 1; i <= cols; i++) {
                    System.out.print(meta.getColumnName(i) + "=" + rs.getString(i) + " ");
                }
                System.out.println();
            }
        }
    }
}
