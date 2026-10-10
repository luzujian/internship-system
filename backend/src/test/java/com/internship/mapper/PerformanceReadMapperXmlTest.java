package com.internship.mapper;

import com.internship.entity.dto.StudentPositionKey;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerformanceReadMapperXmlTest {
    @Test
    void batchFlagsBindBothKeysAndKeepTheLatestInvitationOrdering() throws Exception {
        Configuration configuration = parse("com/internship/mapper/InterviewInvitationMapper.xml");
        BoundSql boundSql = configuration.getMappedStatement(
                        "com.internship.mapper.InterviewInvitationMapper.findLatestForStudentPositions")
                .getBoundSql(Map.of("pairs", List.of(
                        new StudentPositionKey(400L, 300L), new StudentPositionKey(401L, 301L))));
        String sql = normalized(boundSql.getSql());

        assertTrue(sql.contains("SELECT ? AS student_id, ? AS position_id UNION ALL SELECT ? AS student_id, ? AS position_id"));
        assertTrue(sql.contains("latest.student_id = requested.student_id"));
        assertTrue(sql.contains("latest.position_id = requested.position_id"));
        assertTrue(sql.contains("ORDER BY COALESCE(latest.update_time, latest.create_time) DESC, latest.id DESC LIMIT 1"));
        assertTrue(sql.contains("LEFT JOIN interview_invitation ii ON ii.id = ("));
        assertFalse(sql.contains("WHERE ii.interview_type"), "Choose the latest invitation before examining its method");
        assertFalse(sql.contains("SELECT ii.*"), "The flag query must not materialize full interview history");
        List<Object> parameterValues = boundSql.getParameterMappings().stream()
                .map(ParameterMapping::getProperty)
                .map(boundSql::getAdditionalParameter).toList();
        assertEquals(List.of(400L, 300L, 401L, 301L), parameterValues);
        assertTrue(configuration.getResultMap("com.internship.mapper.InterviewInvitationMapper.BaseResultMap")
                .getResultMappings().stream().anyMatch(mapping ->
                        "interview_type".equals(mapping.getColumn()) && "interviewMethod".equals(mapping.getProperty())));
    }

    @Test
    void countRetainsTheCompanyFilterAndAllPositionStatusesWithoutQuotaQueries() throws Exception {
        Configuration configuration = parse("com/internship/mapper/PositionMapper.xml");
        BoundSql boundSql = configuration.getMappedStatement(
                        "com.internship.mapper.PositionMapper.countByCompanyId")
                .getBoundSql(Map.of("companyId", 20L));

        assertEquals("SELECT COUNT(*) FROM position WHERE company_id = ?", normalized(boundSql.getSql()));
        assertEquals("companyId", boundSql.getParameterMappings().getFirst().getProperty());
    }

    private Configuration parse(String resource) throws Exception {
        Configuration configuration = new Configuration();
        if (!"com/internship/mapper/PositionMapper.xml".equals(resource)) {
            String positions = "com/internship/mapper/PositionMapper.xml";
            try (InputStream input = Resources.getResourceAsStream(positions)) {
                new XMLMapperBuilder(input, configuration, positions, configuration.getSqlFragments()).parse();
            }
        }
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    private String normalized(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
