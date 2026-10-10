package com.internship.mapper;

import com.internship.entity.InternshipReflection;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReflectionAiDispatchMapperXmlTest {
    @Test
    void recoveryBindsItsLimitsAndFiltersCurrentSubmissionFailuresAndCooldown() throws Exception {
        BoundSql bound = statement("findPendingAiAnalysis", Map.of("limit", 64, "maxAttempts", 3, "retryDelaySeconds", 60));
        String sql = normalize(bound);
        assertTrue(sql.contains("ir.status = 0 AND ir.remark IS NULL"));
        assertTrue(sql.contains("ir.submit_time IS NOT NULL"));
        assertTrue(sql.contains("settings.enable_ai_scoring = 1"));
        assertTrue(sql.contains("relation.class_id = su.class_id"));
        assertTrue(sql.contains("INNER JOIN teacher_users counselor ON counselor.id = relation.counselor_id AND counselor.status = 1 AND counselor.teacher_type = 'COUNSELOR'"));
        assertTrue(sql.contains("student_reflection_ai_analysis"));
        assertTrue(sql.contains("NOT EXISTS (SELECT 1 FROM student_reflection_evaluation evaluation WHERE evaluation.reflection_id = ir.id AND evaluation.total_score IS NOT NULL)"));
        assertTrue(sql.contains("reflection_withdrawn"));
        assertTrue(sql.contains("failure.student_id = ir.student_id"));
        assertTrue(sql.contains("DATE_SUB(NOW(), INTERVAL ? SECOND)"));
        assertTrue(sql.endsWith("LIMIT ?"));
        assertEquals(java.util.List.of("maxAttempts", "retryDelaySeconds", "limit"),
                bound.getParameterMappings().stream().map(mapping -> mapping.getProperty()).toList());
    }

    @Test
    void failureInsertMaterializesTargetTableReadsAndBindsSnapshotWithoutChangingSubmittedState() throws Exception {
        InternshipReflection snapshot = new InternshipReflection();
        snapshot.setId(10L); snapshot.setStudentId(42L); snapshot.setContent("private content"); snapshot.setSubmitTime(new Date());
        BoundSql bound = statement("recordAiFailure", Map.of("snapshot", snapshot, "version", 55L, "failedAt", new Date()));
        String sql = normalize(bound);
        assertTrue(sql.startsWith("INSERT INTO internship_progress_record"));
        assertTrue(sql.contains("BINARY ir.content = BINARY ?"));
        assertTrue(sql.contains("ir.submit_time = ?"));
        assertTrue(sql.contains("ir.status = 0 AND ir.remark IS NULL"));
        assertTrue(sql.contains("reflection_resubmitted"));
        assertTrue(sql.contains("LEFT JOIN (SELECT MAX(id) AS submission_version FROM internship_progress_record WHERE related_id = ? AND student_id = ?"));
        assertTrue(sql.contains("INNER JOIN (SELECT COUNT(*) AS failure_count FROM internship_progress_record WHERE related_id = ? AND student_id = ?"));
        assertTrue(sql.contains("event_type = 'reflection_ai_failed' AND status = CAST(? AS CHAR)) failures ON 1 = 1"));
        assertTrue(sql.contains("COALESCE(latest.submission_version, 0) = ?"));
        assertTrue(sql.contains("NOT EXISTS (SELECT 1 FROM student_reflection_evaluation evaluation WHERE evaluation.reflection_id = ir.id AND evaluation.total_score IS NOT NULL)"));
        // MySQL forbids reading the INSERT target in a WHERE subquery; aggregate derived joins cannot merge.
        String snapshotGuard = sql.substring(sql.indexOf("WHERE ir.id = ?"));
        assertFalse(snapshotGuard.contains("internship_progress_record"));
        assertFalse(sql.contains("private content"));
        assertFalse(sql.contains("UPDATE internship_reflection"));
        assertTrue(sql.endsWith("failures.failure_count < 3"));
        assertEquals(java.util.List.of("version", "failedAt", "failedAt", "snapshot.id", "snapshot.studentId",
                        "snapshot.id", "snapshot.studentId", "version", "snapshot.id", "snapshot.studentId",
                        "snapshot.content", "snapshot.submitTime", "version"),
                bound.getParameterMappings().stream().map(mapping -> mapping.getProperty()).toList());
    }

    @Test
    void batchReflectionLookupBindsEachIdAndEmptyInputCannotReadUnrelatedRows() throws Exception {
        BoundSql bound = statement("findByIds", Map.of("ids", java.util.List.of(10L, 21L, 32L)));
        assertEquals("SELECT * FROM internship_reflection WHERE deleted = 0 AND id IN ( ? , ? , ? )", normalize(bound));
        assertEquals(java.util.List.of(10L, 21L, 32L), bound.getParameterMappings().stream()
                .map(mapping -> bound.getAdditionalParameter(mapping.getProperty())).toList());
        assertEquals("SELECT * FROM internship_reflection WHERE deleted = 0 AND 1 = 0",
                normalize(statement("findByIds", Map.of("ids", java.util.List.of()))));
        Map<String, Object> nullIds = new java.util.HashMap<>();
        nullIds.put("ids", null);
        assertEquals("SELECT * FROM internship_reflection WHERE deleted = 0 AND 1 = 0",
                normalize(statement("findByIds", nullIds)));
    }

    @Test
    void analysisCompletionSetsExplicitNullAndDraftStatusWithoutGenericPartialUpdates() throws Exception {
        String accepted = normalize(statement("completeAiAnalysis", Map.of("id", 10L, "accepted", true, "updateTime", new Date())));
        String rejected = normalize(statement("completeAiAnalysis", Map.of("id", 10L, "accepted", false, "updateTime", new Date())));
        assertTrue(accepted.contains("status = 1 , remark = NULL"));
        assertTrue(rejected.contains("status = 0 , remark = '0'"));
        assertTrue(normalize(statement("lockById", Map.of("id", 10L))).endsWith("FOR UPDATE"));
    }

    @Test
    void zeroManualScoreProtectsUnpublishedReportsAndManualUpsertUsesACurrentRead() throws Exception {
        BoundSql manual = statement("hasManualEvaluation", Map.of("reflectionId", 10L));
        assertEquals("SELECT EXISTS (SELECT 1 FROM student_reflection_evaluation WHERE reflection_id = ? AND total_score IS NOT NULL)", normalize(manual));
        assertEquals(java.util.List.of("reflectionId"), manual.getParameterMappings().stream()
                .map(mapping -> mapping.getProperty()).toList());
        String resource = "com/internship/mapper/StudentReflectionEvaluationMapper.xml";
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        BoundSql locked = configuration.getMappedStatement("com.internship.mapper.StudentReflectionEvaluationMapper.lockByReflectionId")
                .getBoundSql(Map.of("reflectionId", 10L));
        assertEquals("SELECT * FROM student_reflection_evaluation WHERE reflection_id = ? FOR UPDATE", normalize(locked));
    }

    private BoundSql statement(String name, Map<String, Object> parameters) throws Exception {
        String resource = "com/internship/mapper/InternshipReflectionMapper.xml";
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return configuration.getMappedStatement("com.internship.mapper.InternshipReflectionMapper." + name).getBoundSql(parameters);
    }

    private String normalize(BoundSql bound) { return bound.getSql().replaceAll("\\s+", " ").trim(); }
}
