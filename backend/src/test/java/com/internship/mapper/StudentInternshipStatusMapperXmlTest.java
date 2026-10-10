package com.internship.mapper;

import com.internship.entity.StudentInternshipStatus;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class StudentInternshipStatusMapperXmlTest {

    @ParameterizedTest
    @ValueSource(strings = {"getDashboardStats", "getDashboardStatsByClassIds", "getDashboardStatsByDivisionIds",
            "getStatsByGrade", "getStatsByMajor", "getStatsByClass", "getStatsByClassIds",
            "getStatsByGradeByClassIds", "getStatsByMajorByClassIds", "getStatsByGradeByDivisionIds",
            "getStatsByMajorByDivisionIds", "getStatsByClassByDivisionIds", "getStatsByDivisionByDivisionIds"})
    void dashboardCalendarUsesPlacementDatesAndKeepsStudentsAwaitingPlacement(String statement) throws Exception {
        String resource = "com/internship/mapper/StudentInternshipStatusMapper.xml";
        Configuration configuration = configurationWithSharedFragments();
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        BoundSql boundSql = configuration.getMappedStatement("com.internship.mapper.StudentInternshipStatusMapper." + statement)
                .getBoundSql(Map.of("startDate", "2026-09-01", "endDate", "2027-09-01",
                        "classIds", List.of(1L), "divisionIds", List.of(2L)));
        String sql = boundSql.getSql().replaceAll("\\s+", " ").toLowerCase();
        assertDoesNotThrow(() -> net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(sql));

        assertFalse(sql.contains("sis.update_time"), "建档/编辑时间不得从当前学生分母排除人员");
        assertTrue(sql.contains("su.status = 1"));
        assertTrue(sql.contains("coalesce(sis.status, 0) in (0, 1, 5, 6) or sis.internship_end_time is null or sis.internship_end_time >= ?"));
        assertTrue(sql.contains("coalesce(sis.status, 0) in (0, 1, 5, 6) or sis.internship_start_time is null or sis.internship_start_time < date_add(?, interval 1 day)"));
    }

    private Configuration configurationWithSharedFragments() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "com/internship/mapper/JobSearchStatusMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    @Test
    void placementSwitchExplicitlyClearsLegacyRecallDelayAndInterruptionFlags() throws Exception {
        String resource = "com/internship/mapper/StudentInternshipStatusMapper.xml";
        Configuration configuration = configurationWithSharedFragments();
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        StudentInternshipStatus status = new StudentInternshipStatus();
        status.setId(5L);
        String sql = configuration
                .getMappedStatement("com.internship.mapper.StudentInternshipStatusMapper"
                        + ".updateCurrentPlacement")
                .getBoundSql(status)
                .getSql()
                .replaceAll("\\s+", " ")
                .toLowerCase();

        assertTrue(sql.contains("recall_status = ?"));
        assertTrue(sql.contains("recall_reason = null"));
        assertTrue(sql.contains("recall_apply_time = null"));
        assertTrue(sql.contains("is_delayed = ?"));
        assertTrue(sql.contains("is_interrupted = ?"));
    }

    @Test
    void failedApplicationRollbackIsScopedToPendingStatusAndSamePosition() throws Exception {
        String resource = "com/internship/mapper/StudentInternshipStatusMapper.xml";
        Configuration configuration = configurationWithSharedFragments();
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }

        BoundSql boundSql = configuration
                .getMappedStatement("com.internship.mapper.StudentInternshipStatusMapper"
                        + ".rollbackPendingConfirmationForFailedApplication")
                .getBoundSql(Map.of("studentId", 400L, "positionId", 300L));
        String sql = boundSql.getSql().replaceAll("\\s+", " ").toLowerCase();

        assertTrue(sql.contains("set status = 0"));
        assertTrue(sql.contains("company_confirm_status = 0"));
        assertTrue(sql.contains("company_id = null"));
        assertTrue(sql.contains("position_id = null"));
        assertTrue(sql.contains("where student_id = ? and status = 1 and position_id = ?"));
        assertEquals(List.of("studentId", "positionId"), boundSql.getParameterMappings().stream()
                .map(mapping -> mapping.getProperty())
                .toList());
    }

    @Test
    void teacherCompanyFilterFallsBackToStoredCompanyNameForSelfPractice() throws Exception {
        String resource = "com/internship/mapper/StudentInternshipStatusMapper.xml";
        Configuration configuration = configurationWithSharedFragments();
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }

        BoundSql boundSql = configuration
                .getMappedStatement("com.internship.mapper.StudentInternshipStatusMapper.list")
                .getBoundSql(Map.of("companyName", "望海康信"));
        String sql = boundSql.getSql().replaceAll("\\s+", " ").toLowerCase();

        assertTrue(sql.contains(
                "coalesce(nullif(c.company_name, ''), sis.company_name) like concat('%', ?, '%')"));
    }

    @Test
    void companyOptionsUseStoredNamesAndRespectTeacherScope() throws Exception {
        String resource = "com/internship/mapper/StudentInternshipStatusMapper.xml";
        Configuration configuration = configurationWithSharedFragments();
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }

        BoundSql boundSql = configuration
                .getMappedStatement("com.internship.mapper.StudentInternshipStatusMapper"
                        + ".findDistinctCompanyNamesByScope")
                .getBoundSql(Map.of("divisionIds", List.of(10L), "classIds", List.of(20L)));
        String sql = boundSql.getSql().replaceAll("\\s+", " ").toLowerCase();

        assertTrue(sql.contains("select distinct coalesce(nullif(trim(c.company_name), ''),"
                + " nullif(trim(sis.company_name), '')) as company_name"));
        assertTrue(sql.contains("m.division_id in ( ? )"));
        assertTrue(sql.contains("su.class_id in ( ? )"));
    }
}
