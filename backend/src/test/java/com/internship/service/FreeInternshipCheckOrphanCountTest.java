package com.internship.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.Test;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 精确查 orphan 数量
 */
@EnabledIfEnvironmentVariable(named = "RUN_FREE_INTERNSHIP_DB_INVARIANTS", matches = "true")
public class FreeInternshipCheckOrphanCountTest {

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
    void checkCounts() throws SQLException {
        String[] tables = {"internship_stage_assignment", "internship_confirmation_record",
                "interview_invitation", "internship_progress_record", "position_view_record"};
        for (String table : tables) {
            String sql = "SELECT COUNT(*) FROM " + table + " r " +
                    "LEFT JOIN student_users su ON r.student_id = su.id " +
                    "WHERE su.id IS NULL";
            try (var stmt = conn.createStatement(); var rs = stmt.executeQuery(sql)) {
                assertTrue(rs.next());
                System.out.println("  " + table + ": " + rs.getInt(1) + " orphan records");
            }
        }
    }
}
