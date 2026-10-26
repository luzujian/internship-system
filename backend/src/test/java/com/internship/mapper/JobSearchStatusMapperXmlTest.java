package com.internship.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobSearchStatusMapperXmlTest {
    @Test
    void parsesAllMappersThatUseDerivedStatusFragments() {
        List<String> resources = List.of(
                "com/internship/mapper/JobSearchStatusMapper.xml",
                "com/internship/mapper/TeacherMacroStatisticsMapper.xml",
                "com/internship/mapper/StudentInternshipStatusMapper.xml",
                "com/internship/mapper/StudentUserMapper.xml",
                "com/internship/mapper/ReportMapper.xml"
        );

        assertDoesNotThrow(() -> {
            Configuration configuration = new Configuration();
            for (String resource : resources) {
                try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
                    new XMLMapperBuilder(
                            inputStream,
                            configuration,
                            resource,
                            configuration.getSqlFragments()
                    ).parse();
                }
            }

            String notFoundSql = configuration
                    .getMappedStatement("com.internship.mapper.StudentInternshipStatusMapper.list")
                    .getBoundSql(Map.of("status", -1))
                    .getSql();
            assertTrue(notFoundSql.contains("student_job_application"));
            assertTrue(notFoundSql.contains("interview_invitation"));
            assertTrue(notFoundSql.contains("PENDING_CONFIRMATION"));

            String exportSql = configuration
                    .getMappedStatement("com.internship.mapper.StudentInternshipStatusMapper.findAllWithDetails")
                    .getBoundSql(Map.of("status", 1))
                    .getSql();
            assertTrue(exportSql.contains("PENDING_CONFIRMATION"));
            assertTrue(exportSql.contains("pendingApplicationText"));
            assertTrue(exportSql.contains("student_application"));
            assertFalse(exportSql.contains("sis.status = ?"));

            String emptyScopeSql = configuration
                    .getMappedStatement("com.internship.mapper.ReportMapper.countScopedWithoutOfferStudents")
                    .getBoundSql(Map.of("studentIds", List.of()))
                    .getSql();
            assertTrue(emptyScopeSql.contains("1 = 0"));
            String scopedSql = configuration
                    .getMappedStatement("com.internship.mapper.ReportMapper.countScopedWithoutOfferStudents")
                    .getBoundSql(Map.of("studentIds", List.of(1L, 2L)))
                    .getSql();
            assertTrue(scopedSql.contains("su.id IN"));
            assertTrue(scopedSql.contains("PENDING_CONFIRMATION"));

            String collegeWithoutOfferSql = configuration
                    .getMappedStatement("com.internship.mapper.JobSearchStatusMapper.countWithoutOfferByDepartmentId")
                    .getBoundSql(Map.of("departmentId", 10L))
                    .getSql();
            assertTrue(collegeWithoutOfferSql.contains("LEFT JOIN division"));
            assertTrue(collegeWithoutOfferSql.contains("COALESCE(m.department_id, d.department_id) = ?"));
            assertTrue(collegeWithoutOfferSql.contains("PENDING_CONFIRMATION"));

            String quotaSql = configuration
                    .getMappedStatement("com.internship.mapper.TeacherMacroStatisticsMapper.sumActivePositionRemainingQuota")
                    .getBoundSql(null)
                    .getSql();
            assertTrue(quotaSql.contains("planned_recruit"));
            assertTrue(quotaSql.contains("COUNT(DISTINCT sis.student_id)"));
            assertTrue(quotaSql.contains("sis.status IN (1, 2, 3)"));
            assertTrue(quotaSql.contains("COALESCE(sis.recall_status, 0) != 2"));
            assertFalse(quotaSql.contains("p.remaining_quota"));
        });
    }
}
