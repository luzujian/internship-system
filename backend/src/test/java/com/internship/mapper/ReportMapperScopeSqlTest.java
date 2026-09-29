package com.internship.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportMapperScopeSqlTest {
    private Configuration configuration;

    @BeforeEach
    void readMapper() throws Exception {
        String resource = "com/internship/mapper/ReportMapper.xml";
        configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource,
                    configuration.getSqlFragments()).parse();
        }
    }

    @Test
    void allTeacherStudentQueriesConstrainIdsAndEmptyScopeIsImpossibleToExpand() {
        for (String statement : List.of("getScopedStudentInternshipDetails",
                "getScopedStudentInternshipDetailsCount", "countScopedStudents",
                "countScopedInternshipStudents", "countScopedApplications",
                "getScopedCompanyDetails", "getScopedApprovalDetails",
                "getScopedApprovalDetailsCount")) {
            Map<String, Object> permitted = Map.of("offset", 0, "pageSize", 10,
                    "studentIds", List.of(101L, 102L), "includeCompany", false);
            Map<String, Object> empty = Map.of("offset", 0, "pageSize", 10,
                    "studentIds", List.of(), "includeCompany", false);
            String boundedSql = sql(statement, permitted);
            String emptySql = sql(statement, empty);
            assertTrue(boundedSql.contains("student_id in") || boundedSql.contains("su.id in"), statement);
            assertTrue(emptySql.contains("1 = 0"), statement);
        }
    }

    @Test
    void companyAdmissionMetricCountsOnlyScopedConfirmedCurrentAssignments() {
        String sql = sql("getScopedCompanyDetails", Map.of("offset", 0, "pageSize", 10,
                "studentIds", List.of(101L), "includeCompany", false));
        assertTrue(sql.contains("a.status = 'confirmed'"));
        assertTrue(sql.contains("a.current_flag = 1"));
        assertTrue(sql.contains("a.student_id in"));
        assertFalse(sql.contains("sum(p.recruited_count)"));
    }

    @Test
    void teacherApprovalRowsOmitCompanyAuditWithoutExistingPermission() {
        Map<String, Object> params = Map.of("offset", 0, "pageSize", 10,
                "studentIds", List.of(101L), "includeCompany", false);
        assertFalse(sql("getScopedApprovalDetails", params).contains("from company_users"));
        assertFalse(sql("getScopedApprovalDetailsCount", params).contains("from company_users"));
        assertTrue(sql("getScopedApprovalDetails", Map.of("offset", 0, "pageSize", 10,
                "studentIds", List.of(101L), "includeCompany", true)).contains("from company_users"));
        assertTrue(sql("getScopedApprovalDetailsCount", Map.of("studentIds", List.of(101L),
                "includeCompany", true)).contains("from company_users"));
    }

    private String sql(String statement, Map<String, Object> parameters) {
        return configuration.getMappedStatement("com.internship.mapper.ReportMapper." + statement)
                .getBoundSql(parameters).getSql().replaceAll("\\s+", " ").toLowerCase();
    }
}
