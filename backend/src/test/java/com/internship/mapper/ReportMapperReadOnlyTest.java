package com.internship.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** SELECT-only fixtures shadow table names with CTEs; no schema or business data is changed. */
@EnabledIfEnvironmentVariable(named = "REPORT_READ_ONLY_DB_TESTS", matches = "true")
class ReportMapperReadOnlyTest {
    private Configuration configuration;

    @BeforeEach
    void readMapper() throws Exception {
        configuration = new Configuration();
        for (String resource : List.of("com/internship/mapper/JobSearchStatusMapper.xml",
                "com/internship/mapper/TeacherMacroStatisticsMapper.xml",
                "com/internship/mapper/ReportMapper.xml")) {
            try (InputStream input = Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
    }

    @Test
    void companyTrendFallsBackToCreationDateAndKeepsApprovalDatePriority() throws Exception {
        String fixtures = """
                WITH company_users AS (
                    SELECT 1 AS id, 1 AS audit_status, CAST('2026-04-01' AS DATETIME) AS audit_time,
                           CAST('2026-01-01' AS DATETIME) AS create_time
                    UNION ALL SELECT 2,1,NULL,'2026-09-29'
                    UNION ALL SELECT 3,1,NULL,'2026-10-08'
                    UNION ALL SELECT 4,0,NULL,'2026-07-01'
                    UNION ALL SELECT 5,2,NULL,'2026-07-01'
                    UNION ALL SELECT 6,1,'2027-01-01','2026-02-01'
                    UNION ALL SELECT 7,1,'2026-10-01','2025-01-01'
                    UNION ALL SELECT 8,1,NULL,'2025-10-01'
                    UNION ALL SELECT 9,1,NULL,NULL
                )
                """;
        try (Connection connection = connection()) {
            assertEquals(List.of("1", "1", "2"), query(connection, "getCompanyTrendByQuarter",
                    Map.of("year", 2026), fixtures, "value"));
            assertEquals(List.of("第二季度", "第三季度", "第四季度"), query(connection,
                    "getCompanyTrendByQuarter", Map.of("year", 2026), fixtures, "label"));
            assertEquals(List.of("1"), query(connection, "getCompanyTrendByQuarter",
                    Map.of("year", 2027), fixtures, "value"));
            assertEquals(List.of("1"), query(connection, "getCompanyTrendByQuarter",
                    Map.of("year", 2025), fixtures, "value"));
        }
    }

    @Test
    void companyListsAndExportQueriesUseTheSameEffectiveJoinDate() throws Exception {
        try (Connection connection = connection()) {
            Map<String, Object> scope = Map.of("offset", 0, "pageSize", 10, "studentIds", List.of(101L, 102L));
            String missingAudit = companyFixtures(3, 0).replace("NOW() AS audit_time", "CAST(NULL AS DATETIME) AS audit_time");
            String existingAudit = companyFixtures(3, 0).replace("NOW() AS audit_time",
                    "CAST('2026-08-02 15:56:40' AS DATETIME) AS audit_time");
            for (String query : List.of("getCompanyDetails", "getScopedCompanyDetails")) {
                assertEquals(List.of("2026-09-29 15:52:55"), query(connection, query, scope, missingAudit, "audit_time"));
                assertEquals(List.of("2026-08-02 15:56:40"), query(connection, query, scope, existingAudit, "audit_time"));
            }
        }
    }

    @Test
    void admissionCountTracksOccupiedQuotaThroughTheWholeLifecycle() throws Exception {
        try (Connection connection = connection()) {
            Map<String, Object> scope = Map.of("offset", 0, "pageSize", 10,
                    "studentIds", List.of(101L, 102L, 103L, 104L, 105L, 106L, 107L));
            assertEquals(2, admitted(connection, scope, 1, 0));
            assertEquals(2, admitted(connection, scope, 2, 0));
            assertEquals(2, admitted(connection, scope, 3, 0));
            assertEquals(2, admitted(connection, scope, 3, 1));
            assertEquals(1, admitted(connection, scope, 3, 2));
            for (int status : List.of(0, 4, 5, 6)) {
                assertEquals(1, admitted(connection, scope, status, 0));
            }
        }
    }

    @Test
    void admissionCountsDistinctStudentsWithoutExpandingTeacherScope() throws Exception {
        try (Connection connection = connection()) {
            assertEquals(1, admitted(connection, Map.of("offset", 0, "pageSize", 10,
                    "studentIds", List.of(102L)), 3, 0));
            assertEquals(0, admitted(connection, Map.of("offset", 0, "pageSize", 10,
                    "studentIds", List.of()), 3, 0));
            assertEquals(0, admitted(connection, Map.of("offset", 0, "pageSize", 10,
                    "studentIds", List.of(103L, 104L, 105L, 106L, 107L)), 3, 0));
        }
    }

    @Test
    void schoolwideAdmissionUsesTheSamePositionsAsPlannedQuotaAndIgnoresStaleCachedCounts() throws Exception {
        Map<String, Object> parameters = Map.of("offset", 0, "pageSize", 10000);
        try (Connection connection = connection()) {
            String fixtures = companyFixtures(3, 0);
            assertEquals(List.of("12"), query(connection, "getCompanyDetails", parameters, fixtures, "position_count"));
            assertEquals(List.of("3"), query(connection, "getCompanyDetails", parameters, fixtures, "admitted_count"));
            String full = fixtures.replace("5 AS planned_recruit", "2 AS planned_recruit")
                    .replace("101, 10, 7, 99", "101, 10, 1, 99");
            assertEquals(query(connection, "getCompanyDetails", parameters, full, "position_count"),
                    query(connection, "getCompanyDetails", parameters, full, "admitted_count"));
            String overfilled = full.replace("2 AS planned_recruit", "0 AS planned_recruit");
            assertEquals(List.of("1"), query(connection, "getCompanyDetails", parameters, overfilled, "position_count"));
            assertEquals(List.of("3"), query(connection, "getCompanyDetails", parameters, overfilled, "admitted_count"));
            assertEquals(List.of("0"), query(connection, "getCompanyDetails", parameters,
                    fixtures.replace("'active'", "'draft'").replace("'paused'", "'draft'"), "admitted_count"));
            assertEquals(List.of("0"), query(connection, "getCompanyDetails", parameters,
                    fixtures.replace("'active'", "'draft'").replace("'paused'", "'draft'"), "position_count"));
        }
    }

    @Test
    void availableQuotaExcludesPausedExpiredAndDraftPositionsAndClampsOverfilledPositions() throws Exception {
        String fixtures = """
                WITH position AS (
                    SELECT 1 AS id, 10 AS planned_recruit, 'active' AS status, CURDATE() + INTERVAL 1 DAY AS internship_end_date
                    UNION ALL SELECT 2, 5, 'active', NULL
                    UNION ALL SELECT 3, 100, 'paused', CURDATE() + INTERVAL 1 DAY
                    UNION ALL SELECT 4, 100, 'active', CURDATE() - INTERVAL 1 DAY
                    UNION ALL SELECT 5, 4, 'active', CURDATE()
                    UNION ALL SELECT 6, 100, 'draft', CURDATE() + INTERVAL 1 DAY
                    UNION ALL SELECT 7, 1, 'active', NULL
                ), student_internship_status AS (
                    SELECT 101 AS student_id, 1 AS position_id, 1 AS status, 0 AS recall_status
                    UNION ALL SELECT 102, 1, 2, 0
                    UNION ALL SELECT 103, 1, 3, 0
                    UNION ALL SELECT 103, 1, 3, 0
                    UNION ALL SELECT 104, 1, 4, 0
                    UNION ALL SELECT 105, 1, 5, 0
                    UNION ALL SELECT 106, 1, 6, 0
                    UNION ALL SELECT 107, 1, 0, 0
                    UNION ALL SELECT 108, 1, 3, 2
                    UNION ALL SELECT 201, 2, 3, 1
                    UNION ALL SELECT 501, 5, 3, NULL
                    UNION ALL SELECT 701, 7, 3, 0
                    UNION ALL SELECT 702, 7, 3, 0
                )
                """;
        String statement = "com.internship.mapper.TeacherMacroStatisticsMapper.sumActivePositionRemainingQuota";
        try (Connection connection = connection()) {
            assertEquals(List.of("14"), query(connection, statement, Map.of(), fixtures, 1));
            assertEquals(List.of("0"), query(connection, statement, Map.of(),
                    fixtures.replace("'active'", "'paused'"), 1));
        }
    }

    @Test
    void retiredUnitChangeRowIsAbsentFromListsCountsAndExportQueries() throws Exception {
        String fixtures = """
                WITH student_application AS (
                    SELECT 101 AS student_id, 'selfPractice' AS application_type, 'approved' AS status
                    UNION ALL SELECT 101, 'delay', 'pending'
                    UNION ALL SELECT 101, 'unitChange', 'approved'
                    UNION ALL SELECT 999, 'selfPractice', 'rejected'
                ), company_users AS (SELECT 1 AS audit_status)
                """;
        try (Connection connection = connection()) {
            for (int pageSize : List.of(5, 1000, 10000)) {
                for (boolean includeCompany : List.of(false, true)) {
                    Map<String, Object> scope = Map.of("offset", 0, "pageSize", pageSize,
                            "studentIds", List.of(101L), "includeCompany", includeCompany);
                    List<String> expected = includeCompany
                            ? List.of("自主实习申请", "考研延迟申请", "企业注册审核")
                            : List.of("自主实习申请", "考研延迟申请");
                    assertEquals(expected, query(connection, "getScopedApprovalDetails", scope, fixtures, "type"));
                    assertEquals(List.of(String.valueOf(expected.size())),
                            query(connection, "getScopedApprovalDetailsCount", scope, fixtures, 1));
                }
                Map<String, Object> global = Map.of("offset", 0, "pageSize", pageSize);
                assertEquals(List.of("自主实习申请", "考研延迟申请", "企业注册审核"),
                        query(connection, "getApprovalDetails", global, fixtures, "type"));
                assertEquals(List.of("3"), query(connection, "getApprovalDetailsCount", global, fixtures, 1));
            }
        }
    }

    private Connection connection() throws Exception {
        Connection connection = DriverManager.getConnection(System.getenv("REPORT_READ_ONLY_DB_URL"),
                "user", System.getenv("REPORT_READ_ONLY_DB_PASSWORD"));
        connection.setReadOnly(true);
        return connection;
    }

    private int admitted(Connection connection, Map<String, Object> scope, int status, int recall) throws Exception {
        return Integer.parseInt(query(connection, "getScopedCompanyDetails", scope,
                companyFixtures(status, recall), "admitted_count").getFirst());
    }

    private String companyFixtures(int status, int recall) {
        return """
                WITH company_users AS (
                    SELECT 10 AS id, 'Company A' AS company_name, NOW() AS audit_time,
                           CAST('2026-09-29 15:52:55' AS DATETIME) AS create_time,
                           1 AS status, 1 AS audit_status, '双向' AS company_tag
                ), position AS (
                    SELECT 100 AS id, 10 AS company_id, 5 AS planned_recruit, 99 AS recruited_count, 'active' AS status
                    UNION ALL SELECT 101, 10, 7, 99, 'paused'
                    UNION ALL SELECT 102, 10, 50, 99, 'draft'
                    UNION ALL SELECT 200, 20, 20, 99, 'active'
                ), student_internship_status AS (
                    SELECT 101 AS student_id, 100 AS position_id, 2 AS status, 0 AS recall_status
                    UNION ALL SELECT 102, 101, %d, %d
                    UNION ALL SELECT 102, 101, %d, %d
                    UNION ALL SELECT 103, 100, 4, 0
                    UNION ALL SELECT 104, 100, 5, 0
                    UNION ALL SELECT 105, 102, 3, 0
                    UNION ALL SELECT 106, 200, 3, 0
                    UNION ALL SELECT 107, NULL, 3, 0
                    UNION ALL SELECT 108, 100, 3, 2
                    UNION ALL SELECT 109, 9999, 3, 0
                    UNION ALL SELECT 999, 100, 3, 0
                )
                """.formatted(status, recall, status, recall);
    }

    private List<String> query(Connection connection, String name, Map<String, Object> parameters,
                               String fixtures, Object column) throws Exception {
        BoundSql sql = configuration.getMappedStatement(name.contains(".") ? name : "com.internship.mapper.ReportMapper." + name)
                .getBoundSql(parameters);
        try (var statement = connection.prepareStatement(fixtures + sql.getSql())) {
            statement.setQueryTimeout(10);
            for (int i = 0; i < sql.getParameterMappings().size(); i++) {
                String property = sql.getParameterMappings().get(i).getProperty();
                statement.setObject(i + 1, sql.hasAdditionalParameter(property)
                        ? sql.getAdditionalParameter(property) : parameters.get(property));
            }
            try (var rows = statement.executeQuery()) {
                List<String> result = new ArrayList<>();
                while (rows.next()) result.add(column instanceof Integer index
                        ? rows.getString(index) : rows.getString((String) column));
                return result;
            }
        }
    }
}
