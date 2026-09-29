package com.internship.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.dto.FreeInternshipReconciliationApplyResult;
import com.internship.dto.FreeInternshipReconciliationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FreeInternshipReconciliationLiveApplyTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_LIVE_RECONCILIATION_APPLY", matches = "true")
    @EnabledIfEnvironmentVariable(
            named = "LIVE_RECONCILIATION_CONFIRMATION",
            matches = FreeInternshipReconciliationService.APPLY_CONFIRMATION_TOKEN)
    void appliesKnownMappingInsideAnExplicitDatabaseTransactionAndVerifiesFinalState() {
        DriverManagerDataSource dataSource = dataSource();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        FreeInternshipReconciliationService service =
                new FreeInternshipReconciliationService(jdbcTemplate);
        Path backupFile = backupBeforeApply(jdbcTemplate);
        TransactionTemplate transaction =
                new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        FreeInternshipReconciliationRequest request = new FreeInternshipReconciliationRequest(
                List.of(new FreeInternshipReconciliationRequest.StudentIdMapping(1L, 7L)),
                FreeInternshipReconciliationService.APPLY_CONFIRMATION_TOKEN);

        FreeInternshipReconciliationApplyResult result = transaction.execute(status -> service.apply(request));

        assertNotNull(result);
        assertTrue(Files.isRegularFile(backupFile));
        assertTrue(fileSize(backupFile) > 0);
        System.out.println("Free-internship reconciliation backup: " + backupFile.toAbsolutePath());
        assertTrue(result.after().executable(), () -> result.after().warnings().toString());
        assertTrue(result.after().mappings().get(0).alreadyApplied());
        assertTrue(result.after().mappingImpacts().values().stream().allMatch(List::isEmpty));
        assertTrue(result.after().chainMerges().isEmpty());
        assertTrue(result.after().positionQuotaChanges().isEmpty());
        assertTrue(result.after().enabledDualPhaseSettingIds().isEmpty());
        assertTrue(result.after().invalidEmptyAutoReflectionIds().isEmpty());

        for (String table : List.of(
                "internship_confirmation_record",
                "internship_stage_assignment",
                "interview_invitation",
                "internship_progress_record",
                "position_view_record")) {
            assertEquals(0L, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + table + " WHERE student_id = 1", Long.class));
        }
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM internship_stage_assignment WHERE student_id = 7 " +
                        "AND status = 'CONFIRMED' AND current_flag = 1 AND internship_end_time IS NULL " +
                        "AND confirmation_record_id IS NOT NULL", Long.class));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM internship_stage_assignment WHERE student_id = 7 " +
                        "AND source_type = 'CONTINUE' AND status = 'REPLACED' AND current_flag = 0 " +
                        "AND confirmation_record_id IS NULL", Long.class));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM student_internship_status WHERE student_id = 7 AND status = 3 " +
                        "AND recall_status = 0 AND is_delayed = 0 AND is_interrupted = 0 " +
                        "AND internship_end_time IS NULL AND internship_duration IS NULL", Long.class));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system_settings WHERE COALESCE(enable_dual_phase, 0) <> 0", Long.class));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM internship_reflection r LEFT JOIN student_users su ON su.id = r.student_id " +
                        "WHERE su.id IS NULL AND r.deleted = 0 AND LOWER(r.task_type) = 'auto' " +
                        "AND COALESCE(TRIM(r.content), '') = ''", Long.class));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM internship_appraisal_submission WHERE id = 2 AND status = 'SUBMITTED'", Long.class));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM position p LEFT JOIN (" +
                        "SELECT a.position_id, COUNT(DISTINCT a.student_id) actual_count " +
                        "FROM internship_stage_assignment a JOIN student_users su ON su.id = a.student_id " +
                        "AND su.role = 'ROLE_STUDENT' AND su.status = 1 " +
                        "WHERE a.status = 'CONFIRMED' AND a.current_flag = 1 GROUP BY a.position_id" +
                        ") actual ON actual.position_id = p.id " +
                        "WHERE p.recruited_count <> COALESCE(actual.actual_count, 0) " +
                        "OR p.remaining_quota <> GREATEST(p.planned_recruit - COALESCE(actual.actual_count, 0), 0)",
                Long.class));
    }

    private static Path backupBeforeApply(JdbcTemplate jdbcTemplate) {
        try {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("createdAt", LocalDateTime.now().toString());
            snapshot.put("mapping", Map.of("legacyStudentId", 1L, "targetStudentId", 7L));
            snapshot.put("student_users", jdbcTemplate.queryForList(
                    "SELECT * FROM student_users WHERE id IN (?, ?) ORDER BY id", 1L, 7L));
            for (String table : List.of(
                    "internship_confirmation_record",
                    "internship_stage_assignment",
                    "interview_invitation",
                    "internship_progress_record",
                    "position_view_record")) {
                snapshot.put(table, jdbcTemplate.queryForList(
                        "SELECT * FROM " + table + " WHERE student_id IN (?, ?) ORDER BY id", 1L, 7L));
            }
            snapshot.put("student_internship_status", jdbcTemplate.queryForList(
                    "SELECT * FROM student_internship_status WHERE student_id IN (?, ?) ORDER BY id", 1L, 7L));
            snapshot.put("affected_position", jdbcTemplate.queryForList(
                    "SELECT p.* FROM position p LEFT JOIN (" +
                            "SELECT a.position_id, COUNT(DISTINCT a.student_id) actual_count " +
                            "FROM internship_stage_assignment a JOIN student_users su ON su.id = a.student_id " +
                            "AND su.role = 'ROLE_STUDENT' AND su.status = 1 " +
                            "WHERE a.status = 'CONFIRMED' AND a.current_flag = 1 GROUP BY a.position_id" +
                            ") actual ON actual.position_id = p.id " +
                            "WHERE p.recruited_count <> COALESCE(actual.actual_count, 0) " +
                            "OR p.remaining_quota <> GREATEST(p.planned_recruit - COALESCE(actual.actual_count, 0), 0) " +
                            "ORDER BY p.id"));
            snapshot.put("invalid_empty_auto_reflection", jdbcTemplate.queryForList(
                    "SELECT r.* FROM internship_reflection r LEFT JOIN student_users su ON su.id = r.student_id " +
                            "WHERE su.id IS NULL AND r.deleted = 0 AND LOWER(r.task_type) = 'auto' " +
                            "AND COALESCE(TRIM(r.content), '') = '' ORDER BY r.id"));
            snapshot.put("system_settings", jdbcTemplate.queryForList(
                    "SELECT * FROM system_settings ORDER BY id"));
            snapshot.put("orphan_appraisal", jdbcTemplate.queryForList(
                    "SELECT a.* FROM internship_appraisal_submission a " +
                            "LEFT JOIN student_users su ON su.id = a.student_id " +
                            "WHERE su.id IS NULL ORDER BY a.id"));

            Path directory = Path.of("target", "reconciliation-backups");
            Files.createDirectories(directory);
            Path backupFile = directory.resolve("free-internship-before-" +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")) + ".json");
            // JDBC maps contain LocalDate/LocalDateTime values. Register the JSR-310 module so
            // the mandatory pre-DML backup cannot fail on temporal columns.
            new ObjectMapper().findAndRegisterModules()
                    .writerWithDefaultPrettyPrinter()
                    .writeValue(backupFile.toFile(), snapshot);
            if (!Files.isRegularFile(backupFile) || Files.size(backupFile) == 0) {
                throw new IllegalStateException("Reconciliation backup file was not created");
            }
            return backupFile;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot create reconciliation backup before applying DML", exception);
        }
    }

    private static long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot read reconciliation backup file", exception);
        }
    }

    private static DriverManagerDataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl("jdbc:mysql://" + required("DB_HOST") + ":" + required("DB_PORT") + "/" +
                required("DB_NAME") + "?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8" +
                "&useUnicode=true&allowPublicKeyRetrieval=true");
        dataSource.setUsername(required("DB_USERNAME"));
        dataSource.setPassword(required("DB_PASSWORD"));
        return dataSource;
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing environment variable " + name);
        }
        return value;
    }
}
