package com.internship.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import com.internship.entity.StudentInternshipStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** SELECT-only CTE fixtures cover missing/history rows without changing any tables. */
@EnabledIfEnvironmentVariable(named = "STUDENT_STATUS_READ_ONLY_DB_TESTS", matches = "true")
class TeacherStudentStatusReadOnlyTest {
    private Configuration configuration;
    private static final String FIXTURES = """
            WITH student_users AS (
                SELECT 101 AS id, 1 AS status, 30 AS class_id, 1 AS major_id, 2024 AS grade,
                       's101' AS student_user_id, 'Student 101' AS name, 1 AS gender
                UNION ALL SELECT 102,1,30,1,2024,'s102','Student 102',1
                UNION ALL SELECT 103,1,30,1,2024,'s103','Student 103',1
                UNION ALL SELECT 104,1,30,1,2024,'s104','Student 104',1
                UNION ALL SELECT 105,1,30,1,2024,'s105','Student 105',1
                UNION ALL SELECT 106,1,30,1,2024,'s106','Student 106',1
                UNION ALL SELECT 107,1,30,1,2024,'s107','Student 107',1
                UNION ALL SELECT 108,1,30,1,2024,'s108','Student 108',1
                UNION ALL SELECT 109,1,30,1,2024,'s109','Student 109',1
                UNION ALL SELECT 201,1,40,2,2024,'s201','Other scope',1
                UNION ALL SELECT 301,0,30,1,2024,'s301','Disabled',1
            ), student_internship_status AS (
                SELECT 1 AS id, 103 AS student_id, 3 AS status, CAST('2025-01-01' AS DATETIME) AS create_time,
                       10 AS company_id, 20 AS position_id, '' AS company_name,
                       CAST('2026-09-01' AS DATE) AS internship_start_time,
                       CAST('2027-09-01' AS DATE) AS internship_end_time
                UNION ALL SELECT 2,103,0,'2026-01-01',NULL,NULL,'',NULL,NULL
                UNION ALL SELECT 3,103,0,'2026-01-01',NULL,NULL,'',NULL,NULL
                UNION ALL SELECT 4,104,2,'2026-01-01',10,20,'','2026-09-01','2027-09-01'
                UNION ALL SELECT 5,105,3,'2026-01-01',10,20,'','2026-09-01','2027-09-01'
                UNION ALL SELECT 6,106,4,'2026-01-01',10,20,'','2026-09-01','2027-09-01'
                UNION ALL SELECT 7,107,5,'2026-01-01',10,20,'',NULL,NULL
                UNION ALL SELECT 8,108,6,'2026-01-01',NULL,NULL,'',NULL,NULL
            ), major AS (
                SELECT 1 AS id, 10 AS department_id, 5 AS division_id, 'Major A' AS name
                UNION ALL SELECT 2,20,6,'Major B'
            ), class AS (
                SELECT 30 AS id, 'Class A' AS name UNION ALL SELECT 40,'Class B'
            ), company_users AS (SELECT 10 AS id, 'Company A' AS company_name),
            position AS (SELECT 20 AS id),
            student_job_application AS (
                SELECT 11 AS id, 101 AS student_id, 'interview_passed' AS status,
                       20 AS position_id, NOW() AS update_time
                UNION ALL SELECT 12,109,'pending',20,NOW()
                UNION ALL SELECT 13,201,'interview_passed',20,NOW()
                UNION ALL SELECT 14,301,'interview_passed',20,NOW()
            ), internship_stage_assignment AS (
                SELECT 103 AS student_id, NULL AS application_id, 'PENDING_CONFIRMATION' AS status
            ), interview_invitation AS (
                SELECT 0 AS student_id, 0 AS position_id, '' AS status, NOW() AS update_time WHERE 1=0
            ), internship_confirmation_record AS (
                SELECT 0 AS student_id, 0 AS position_id, 0 AS status, 0 AS recall_status,
                       NOW() AS update_time WHERE 1=0
            )
            """;

    @BeforeEach
    void readMappers() throws Exception {
        configuration = new Configuration();
        for (String resource : List.of("com/internship/mapper/JobSearchStatusMapper.xml",
                "com/internship/mapper/StudentInternshipStatusMapper.xml")) {
            try (InputStream input = Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
    }

    @Test
    void includesAdmittedAndUnplacedStudentsWithoutStatusRowsForEveryTeacherScope() throws Exception {
        try (Connection connection = connection()) {
            for (String scope : List.of("departmentIds", "divisionIds", "classIds")) {
                long id = scope.equals("departmentIds") ? 10 : scope.equals("divisionIds") ? 5 : 30;
                Map<String, Object> parameters = new HashMap<>(Map.of(scope, List.of(id)));
                assertEquals(9, students(connection, parameters, "student_id").size(), scope);
                parameters.put("status", 1);
                assertEquals(List.of("101", "103"), students(connection, parameters, "student_id").stream().sorted().toList(), scope);
                parameters.put("status", -1);
                assertEquals(List.of("102"), students(connection, parameters, "student_id"), scope);
                parameters.put("status", -2);
                assertEquals(List.of("109"), students(connection, parameters, "student_id"), scope);
            }
        }
    }

    @Test
    void latestStatusWinsAndEqualTimestampsUseTheNewestIdOnce() throws Exception {
        try (Connection connection = connection()) {
            Map<String, Object> parameters = Map.of("departmentIds", List.of(10L), "studentId", 103L);
            assertEquals(List.of("3"), students(connection, parameters, "status_id"));
            assertEquals(List.of("0"), students(connection, parameters, "status"));
            assertEquals(List.of(), students(connection, Map.of("departmentIds", List.of(10L),
                    "studentId", 103L, "status", 3), "student_id"));
        }
    }

    @Test
    void dashboardAndMonitoringAgreeForAllStatusBucketsIncludingMissingAndHistoricalRows() throws Exception {
        try (Connection connection = connection()) {
            String[] buckets = {"noOffer", "offer", "confirmed", "interning", "finished", "interrupted", "delay"};
            for (String dashboard : List.of("getDashboardStatsByClassIds", "getDashboardStatsByDivisionIds")) {
                Map<String, Object> scope = Map.of("departmentIds", List.of(10L), "divisionIds", List.of(5L),
                        "classIds", List.of(30L), "startDate", "2026-09-01", "endDate", "2027-09-01");
                assertEquals(List.of("8"), query(connection, dashboard, scope, false, "totalStudents"));
                for (int status = 0; status <= 6; status++) {
                    Map<String, Object> filter = new HashMap<>(scope);
                    filter.put("status", status);
                    int count = students(connection, filter, "student_id").size();
                    assertEquals(status <= 1 ? 2 : 1, count);
                    assertEquals(List.of(String.valueOf(count)), query(connection, dashboard, scope, false, buckets[status]), dashboard + ":" + status);
                }
            }
            for (String dashboard : List.of("getStatsByGradeByDivisionIds", "getStatsByMajorByDivisionIds",
                    "getStatsByClassByDivisionIds", "getStatsByGradeByClassIds", "getStatsByMajorByClassIds", "getStatsByClassIds")) {
                Map<String, Object> scope = Map.of("divisionIds", List.of(5L), "classIds", List.of(30L));
                assertEquals(List.of("2"), query(connection, dashboard, scope, false, "offer"), dashboard);
            }
        }
    }

    @Test
    void searchingAndFilteringNeverExposeDisabledOrOtherScopeAccounts() throws Exception {
        try (Connection connection = connection()) {
            assertEquals(List.of(), students(connection, Map.of("departmentIds", List.of(10L), "studentId", 301L), "student_id"));
            assertEquals(List.of(), students(connection, Map.of("departmentIds", List.of(10L), "studentId", 201L), "student_id"));
            assertEquals(List.of("101"), students(connection, Map.of("departmentIds", List.of(10L), "name", "s101"), "student_id"));
            assertEquals(List.of("101", "103"), students(connection, Map.of("departmentIds", List.of(10L),
                    "status", 1, "grade", "2024级", "major", "Major A", "className", "Class A"), "student_id").stream().sorted().toList());
        }
    }

    @Test
    void realMyBatisMappingPreservesEveryScopedStudentIncludingNullStatusIds() throws Exception {
        try (Connection connection = connection()) {
            long departmentId;
            try (var statement = connection.prepareStatement("SELECT department_id FROM teacher_users WHERE teacher_user_id = ?")) {
                statement.setString(1, "x001");
                try (var rows = statement.executeQuery()) {
                    org.junit.jupiter.api.Assumptions.assumeTrue(rows.next(), "x001 is required for the read-only reproduction");
                    departmentId = rows.getLong(1);
                }
            }
            List<Long> expected = new ArrayList<>();
            List<Long> missing = new ArrayList<>();
            try (var statement = connection.prepareStatement("""
                    SELECT su.id, EXISTS(SELECT 1 FROM student_internship_status sis WHERE sis.student_id=su.id) AS has_status
                    FROM student_users su JOIN major m ON m.id=su.major_id
                    WHERE su.status=1 AND m.department_id=? ORDER BY su.id
                    """)) {
                statement.setLong(1, departmentId);
                try (var rows = statement.executeQuery()) {
                    while (rows.next()) {
                        expected.add(rows.getLong(1));
                        if (!rows.getBoolean(2)) missing.add(rows.getLong(1));
                    }
                }
            }
            configuration.setEnvironment(new Environment("read-only", new JdbcTransactionFactory(),
                    new UnpooledDataSource("com.mysql.cj.jdbc.Driver", System.getenv("STUDENT_STATUS_READ_ONLY_DB_URL"),
                            "user", System.getenv("STUDENT_STATUS_READ_ONLY_DB_PASSWORD"))));
            try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession(connection)) {
                List<StudentInternshipStatus> students = session.selectList(
                        "com.internship.mapper.StudentInternshipStatusMapper.listByScope",
                        Map.of("departmentIds", List.of(departmentId)));
                assertEquals(expected, students.stream().map(StudentInternshipStatus::getStudentId).sorted().toList());
                assertEquals(missing, students.stream().filter(student -> student.getId() == null)
                        .map(StudentInternshipStatus::getStudentId).sorted().toList());
                for (StudentInternshipStatus student : students) {
                    assertEquals(student.getStudentId(), student.getStudent().getId());
                }
            }
        }
    }

    private Connection connection() throws Exception {
        Connection connection = DriverManager.getConnection(System.getenv("STUDENT_STATUS_READ_ONLY_DB_URL"),
                "user", System.getenv("STUDENT_STATUS_READ_ONLY_DB_PASSWORD"));
        connection.setReadOnly(true);
        return connection;
    }

    private List<String> students(Connection connection, Map<String, Object> parameters, String column) throws Exception {
        return query(connection, "listByScope", parameters, true, column);
    }

    private List<String> query(Connection connection, String name, Map<String, Object> parameters,
                               boolean projectStudents, String column) throws Exception {
        BoundSql bound = configuration.getMappedStatement("com.internship.mapper.StudentInternshipStatusMapper." + name).getBoundSql(parameters);
        String sql = bound.getSql();
        if (projectStudents) {
            sql = "SELECT su.id AS student_id, sis.id AS status_id, COALESCE(sis.status,0) AS status "
                    + sql.substring(sql.indexOf("FROM student_"));
        }
        try (var statement = connection.prepareStatement(FIXTURES + sql)) {
            statement.setQueryTimeout(10);
            for (int i = 0; i < bound.getParameterMappings().size(); i++) {
                String property = bound.getParameterMappings().get(i).getProperty();
                statement.setObject(i + 1, bound.hasAdditionalParameter(property)
                        ? bound.getAdditionalParameter(property) : parameters.get(property));
            }
            try (var rows = statement.executeQuery()) {
                List<String> result = new ArrayList<>();
                while (rows.next()) result.add(rows.getString(column));
                return result;
            }
        }
    }
}
