package com.internship.service;

import com.internship.dto.FreeInternshipReconciliationReport;
import com.internship.dto.FreeInternshipReconciliationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FreeInternshipReconciliationLivePreviewTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_LIVE_RECONCILIATION_PREVIEW", matches = "true")
    void previewsKnownLegacyStudentMappingWithoutWriting() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl("jdbc:mysql://" + required("DB_HOST") + ":" + required("DB_PORT") + "/" +
                required("DB_NAME") + "?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8" +
                "&useUnicode=true&allowPublicKeyRetrieval=true");
        dataSource.setUsername(required("DB_USERNAME"));
        dataSource.setPassword(required("DB_PASSWORD"));
        FreeInternshipReconciliationService service =
                new FreeInternshipReconciliationService(new JdbcTemplate(dataSource));

        FreeInternshipReconciliationReport report = service.preview(new FreeInternshipReconciliationRequest(
                List.of(new FreeInternshipReconciliationRequest.StudentIdMapping(1L, 7L)), null));

        assertEquals(1, report.mappings().size());
        assertTrue(report.mappings().get(0).valid(), () -> report.warnings().toString());
        assertEquals("s001", report.mappings().get(0).studentUserId());
        assertTrue(report.executable(), () -> report.warnings().toString());
        assertFalse(report.preservedSubmittedAppraisalIds().isEmpty(),
                "Submitted orphan appraisal history must be visible and preserved");
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing environment variable " + name);
        }
        return value;
    }
}
