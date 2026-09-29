package com.internship.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.Test;

import java.sql.*;

/**
 * 诊断 s001 (student_id=7) 无法申请岗位的原因
 */
@EnabledIfEnvironmentVariable(named = "RUN_FREE_INTERNSHIP_DB_INVARIANTS", matches = "true")
public class FreeInternshipDiagS001Test {

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
    void diagS001() throws SQLException {
        System.out.println("========== 1. s001 学生信息 ==========");
        try (var stmt = conn.createStatement(); var rs = stmt.executeQuery(
                "SELECT id, username, name, status FROM student_users WHERE username = 's001' OR id = 7")) {
            while (rs.next()) {
                System.out.println("  id=" + rs.getInt("id") +
                        " username=" + rs.getString("username") + " name=" + rs.getString("name") +
                        " status=" + rs.getInt("status"));
            }
        }

        System.out.println("\n========== 2. s001 的岗位申请记录 ==========");
        try (var stmt = conn.createStatement(); var rs = stmt.executeQuery(
                "SELECT id, student_id, position_id, position_name, company_name, status, apply_date " +
                "FROM student_job_application WHERE student_id = 7 ORDER BY apply_date DESC")) {
            int count = 0;
            while (rs.next()) {
                count++;
                System.out.println("  申请#" + count + ": id=" + rs.getInt("id") +
                        " position_id=" + rs.getInt("position_id") +
                        " position_name=" + rs.getString("position_name") +
                        " company_name=" + rs.getString("company_name") +
                        " status=" + rs.getString("status") +
                        " apply_date=" + rs.getString("apply_date"));
            }
            if (count == 0) System.out.println("  无申请记录");
        }

        System.out.println("\n========== 3. s001 的 internship_application 记录 ==========");
        try (var stmt = conn.createStatement(); var rs = stmt.executeQuery(
                "SELECT id, student_id, position_id, position_name, status, stage_no " +
                "FROM internship_application WHERE student_id = 7 ORDER BY id DESC")) {
            int count = 0;
            while (rs.next()) {
                count++;
                System.out.println("  记录#" + count + ": id=" + rs.getInt("id") +
                        " position_id=" + rs.getInt("position_id") +
                        " position_name=" + rs.getString("position_name") +
                        " status=" + rs.getString("status") +
                        " stage_no=" + rs.getInt("stage_no"));
            }
            if (count == 0) System.out.println("  无记录");
        }

        System.out.println("\n========== 4. 所有岗位及状态 ==========");
        try (var stmt = conn.createStatement(); var rs = stmt.executeQuery(
                "SELECT id, position_name, company_id, status, planned_recruit, recruited_count " +
                "FROM position ORDER BY id")) {
            int count = 0;
            while (rs.next()) {
                count++;
                System.out.println("  岗位#" + count + ": id=" + rs.getInt("id") +
                        " name=" + rs.getString("position_name") +
                        " company_id=" + rs.getInt("company_id") +
                        " status=" + rs.getString("status") +
                        " planned=" + rs.getInt("planned_recruit") +
                        " recruited=" + rs.getInt("recruited_count"));
            }
            if (count == 0) System.out.println("  无岗位");
        }

        System.out.println("\n========== 5. s001 (id=8) 当前实习状态 ==========");
        try (var stmt = conn.createStatement(); var rs = stmt.executeQuery(
                "SELECT student_id, company_id, position_id, status, recall_status " +
                "FROM student_internship_status WHERE student_id IN (7, 8)")) {
            while (rs.next()) {
                System.out.println("  student_id=" + rs.getInt("student_id") +
                        " company_id=" + rs.getInt("company_id") +
                        " position_id=" + rs.getInt("position_id") +
                        " status=" + rs.getInt("status") +
                        " recall_status=" + rs.getInt("recall_status"));
            }
        }

        System.out.println("\n========== 6. s001 (id=8) 的 stage_assignment ==========");
        try (var stmt = conn.createStatement(); var rs = stmt.executeQuery(
                "SELECT id, student_id, company_id, position_id, status, current_flag, stage_no " +
                "FROM internship_stage_assignment WHERE student_id IN (7, 8) ORDER BY id")) {
            int count = 0;
            while (rs.next()) {
                count++;
                System.out.println("  段#" + count + ": id=" + rs.getInt("id") +
                        " student_id=" + rs.getInt("student_id") +
                        " company_id=" + rs.getInt("company_id") +
                        " position_id=" + rs.getInt("position_id") +
                        " status=" + rs.getString("status") +
                        " current_flag=" + rs.getInt("current_flag") +
                        " stage_no=" + rs.getInt("stage_no"));
            }
            if (count == 0) System.out.println("  无记录");
        }

        System.out.println("\n========== 7. 所有学生用户 ==========");
        try (var stmt = conn.createStatement(); var rs = stmt.executeQuery(
                "SELECT id, username, name, status FROM student_users ORDER BY id")) {
            while (rs.next()) {
                System.out.println("  id=" + rs.getInt("id") +
                        " username=" + rs.getString("username") +
                        " name=" + rs.getString("name") +
                        " status=" + rs.getInt("status"));
            }
        }

        System.out.println("\n========== 诊断完成 ==========");
    }
}
