package com.internship.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Read-only MySQL verification: the CTE shadows the business table; no business rows are changed. */
@EnabledIfEnvironmentVariable(named = "FEEDBACK_WORKFLOW_DB_URL", matches = ".+")
class ProblemFeedbackWorkflowDatabaseTest {
    private static final String FIXTURE = """
            WITH problem_feedback AS (
              SELECT 1 id, 'student' user_type, '同学' user_name, '问题' title, 'pending' status,
                     'high' priority, 'bug' feedback_type, 0 deleted, TIMESTAMP('2026-11-01 10:00:00') create_time
              UNION ALL SELECT 2, 'student', '同学', '问题', 'processing', 'low', 'other', 0, TIMESTAMP('2026-11-01 11:00:00')
              UNION ALL SELECT 3, 'student', '同学', '问题', 'resolved', 'high', 'bug', 0, TIMESTAMP('2026-11-01 12:00:00')
              UNION ALL SELECT 4, 'student', '同学', '问题', 'closed', 'low', 'other', 0, TIMESTAMP('2026-11-01 13:00:00')
              UNION ALL SELECT 5, 'student', '同学', '问题', 'processing', 'high', 'bug', 1, TIMESTAMP('2026-11-01 14:00:00')
            )
            """;

    @Test void pageCountAndStatisticsUseTheSameTwoStatesAndIgnoreHistoricalPriority() throws Exception {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(System.getenv("FEEDBACK_WORKFLOW_DB_URL"), "user", "1234"));
        assertTrue(jdbc.queryForObject("SELECT CURRENT_USER()", String.class).startsWith("user@"));
        var configuration = new Configuration();
        var resource = new ClassPathResource("com/internship/mapper/ProblemFeedbackMapper.xml");
        try (var input = resource.getInputStream()) {
            new XMLMapperBuilder(input, configuration, resource.getPath(), configuration.getSqlFragments()).parse();
        }
        var params = new HashMap<String, Object>();
        params.put("userType", "student"); params.put("userName", "同学"); params.put("title", "问题");
        params.put("offset", 0); params.put("pageSize", 10);
        for (String state : List.of("processing", "pending", "resolved", "closed")) {
            params.put("status", state);
            List<Long> expected = List.of("processing", "pending").contains(state) ? List.of(2L, 1L) : List.of(4L, 3L);
            for (String query : List.of("list", "findPageWithConditions")) {
                BoundSql sql = bound(configuration, query, params);
                assertEquals(expected, jdbc.query(FIXTURE + sql.getSql(), (rs, row) -> rs.getLong("id"), args(sql, params)));
            }
            for (String query : List.of("countWithConditions", "countByStatus")) {
                BoundSql sql = bound(configuration, query, params);
                assertEquals(2L, jdbc.queryForObject(FIXTURE + sql.getSql(), Long.class, args(sql, params)));
            }
        }
        BoundSql all = bound(configuration, "findAll", params);
        assertEquals(List.of(2L, 1L, 4L, 3L), jdbc.query(FIXTURE + all.getSql(), (rs, row) -> rs.getLong("id")));
        var stats = jdbc.queryForMap(FIXTURE + bound(configuration, "getStatistics", params).getSql());
        assertEquals(4L, ((Number) stats.get("total")).longValue());
        assertEquals(2L, ((Number) stats.get("processing")).longValue());
        assertEquals(2L, ((Number) stats.get("resolved")).longValue());
    }

    private BoundSql bound(Configuration configuration, String query, Map<String, Object> params) {
        return configuration.getMappedStatement("com.internship.mapper.ProblemFeedbackMapper." + query).getBoundSql(params);
    }

    private Object[] args(BoundSql sql, Map<String, Object> params) {
        return sql.getParameterMappings().stream().map(mapping -> params.get(mapping.getProperty())).toArray();
    }
}
