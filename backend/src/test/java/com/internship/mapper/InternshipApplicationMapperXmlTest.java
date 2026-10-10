package com.internship.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class InternshipApplicationMapperXmlTest {

    private String sql(String method, Map<String, Object> params) throws Exception {
        String resource = "com/internship/mapper/InternshipApplicationMapper.xml";
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return configuration.getMappedStatement("com.internship.mapper.InternshipApplicationMapper." + method)
                .getBoundSql(params).getSql().replaceAll("\\s+", " ");
    }

    @Test void activityPagingIncludesHistoricalRecordsAndStableBusinessTimeOrder() throws Exception {
        String query = sql("findActivityPage", Map.of("companyId", 7L, "unreadOnly", true, "offset", 10, "limit", 10));
        assertTrue(query.contains("WHERE company_id = ? AND COALESCE(viewed, 0) = 0"));
        assertTrue(query.contains("ORDER BY COALESCE(update_time, apply_time, create_time) DESC, id DESC LIMIT ? OFFSET ?"));
        assertFalse(query.contains("JOIN position"));
    }

    @Test void readingDoesNotChangeBusinessTimestampAndGuardsConcurrentStatusUpdates() throws Exception {
        String query = sql("readActivityVersion", Map.of("id", 31L, "companyId", 7L,
                "status", "pending", "updateTime", new java.util.Date()));
        assertTrue(query.contains("update_time = update_time"));
        assertTrue(query.contains("company_id = ? AND status = ? AND update_time <=> ?"));
        assertFalse(query.contains("update_time = NOW()"));
        assertFalse(sql("updateViewed", Map.of("id", 31L, "viewed", true)).contains("update_time = NOW()"));
    }

    @Test void statusChangesCreateUnreadUpdateButDuplicateSynchronizationDoesNot() throws Exception {
        String query = sql("updateLatestStatusByStudentAndPosition", Map.of("studentId", 5L, "positionId", 3L, "status", "withdrawn"));
        assertTrue(query.contains("viewed = CASE WHEN status = ? THEN viewed ELSE 0 END"));
        assertTrue(query.contains("update_time = CASE WHEN status = ? THEN update_time ELSE NOW() END"));
    }

    @Test
    void onlyInterviewStageApplicationCanTransitionToInterviewResult() throws Exception {
        String resource = "com/internship/mapper/InternshipApplicationMapper.xml";
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }

        String sql = configuration
                .getMappedStatement("com.internship.mapper.InternshipApplicationMapper.updateStatus")
                .getBoundSql(Map.of("id", 10L, "status", "interview_passed"))
                .getSql()
                .replaceAll("\\s+", " ");

        assertTrue(sql.contains(
                "? IN ('interview_passed', 'interview_failed') AND status IN ('approved', 'interview', 'pending_interview')"));
    }
}
