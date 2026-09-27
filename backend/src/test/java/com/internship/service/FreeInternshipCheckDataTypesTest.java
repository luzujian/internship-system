package com.internship.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.Test;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 查 orphan stage_assignment 的列类型和实际数据
 */
@EnabledIfEnvironmentVariable(named = "RUN_FREE_INTERNSHIP_DB_INVARIANTS", matches = "true")
public class FreeInternshipCheckDataTypesTest {

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
    void checkTypes() throws SQLException {
        // 查 student_users id 列类型
        try (var rs = conn.getMetaData().getColumns(null, null, "student_users", "id")) {
            while (rs.next()) {
                System.out.println("student_users.id: type=" + rs.getString("TYPE_NAME")
                        + " size=" + rs.getInt("COLUMN_SIZE"));
            }
        }
        // 查 internship_stage_assignment student_id 列类型
        try (var rs = conn.getMetaData().getColumns(null, null, "internship_stage_assignment", "student_id")) {
            while (rs.next()) {
                System.out.println("internship_stage_assignment.student_id: type=" + rs.getString("TYPE_NAME")
                        + " size=" + rs.getInt("COLUMN_SIZE"));
            }
        }
        // 查 internship_confirmation_record student_id 列类型
        try (var rs = conn.getMetaData().getColumns(null, null, "internship_confirmation_record", "student_id")) {
            while (rs.next()) {
                System.out.println("internship_confirmation_record.student_id: type=" + rs.getString("TYPE_NAME")
                        + " size=" + rs.getInt("COLUMN_SIZE"));
            }
        }
    }

    @Test
    void checkActualValues() throws SQLException {
        // 查 stage_assignment 中所有 student_id 的值
        String sql = "SELECT DISTINCT sa.student_id, CAST(sa.student_id AS CHAR) AS str_id, " +
                "su.id AS su_id FROM internship_stage_assignment sa " +
                "LEFT JOIN student_users su ON sa.student_id = su.id " +
                "ORDER BY sa.student_id";
        try (var stmt = conn.createStatement(); var rs = stmt.executeQuery(sql)) {
            var meta = rs.getMetaData();
            int cols = meta.getColumnCount();
            int count = 0;
            while (rs.next()) {
                count++;
                System.out.print("--- ");
                for (int i = 1; i <= cols; i++) {
                    System.out.print(meta.getColumnName(i) + "=[" + rs.getString(i) + "] ");
                }
                System.out.println();
            }
            System.out.println("共 " + count + " 条 distinct student_id");
        }
    }
}
