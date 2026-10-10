package com.internship.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class CompanyInternshipStudentMapperTest {
    private BoundSql sql(String statement, long companyId, String keyword) throws Exception {
        Configuration configuration = new Configuration();
        String resource = "com/internship/mapper/CompanyInternshipStudentMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        Map<String, Object> params = new HashMap<>();
        params.put("companyId", companyId);
        params.put("keyword", keyword);
        params.put("offset", 0L);
        params.put("pageSize", 100);
        return configuration.getMappedStatement("com.internship.mapper.CompanyInternshipStudentMapper." + statement).getBoundSql(params);
    }

    @Test
    void listAndCountHaveTheSameScopeAndAllInputUsesBoundParameters() throws Exception {
        String keyword = "x' OR 1=1 --";
        String count = sql("countCurrentStudents", 100L, keyword).getSql().replaceAll("\\s+", " ").trim();
        String list = sql("findCurrentStudents", 100L, keyword).getSql().replaceAll("\\s+", " ").trim();
        assertEquals(count.substring(count.indexOf("FROM student_internship_status")),
                list.substring(list.indexOf("FROM student_internship_status"), list.indexOf("ORDER BY sis.internship_start_time")).trim());
        assertFalse(list.contains(keyword));
        assertFalse(list.contains("su.password"));
        assertDoesNotThrow(() -> net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(list));
        assertDoesNotThrow(() -> net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(count));
    }

    /** MySQL CTEs supply synthetic rows for this SELECT only; no schema or real data is changed. */
    @Test
    @EnabledIfEnvironmentVariable(named = "COMPANY_INTERNSHIP_ROSTER_LOCAL_TESTS", matches = "true")
    void currentRosterIncludesSelfPracticeAndExcludesOtherCompaniesAndHistoricalPlacements() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:mysql://127.0.0.1:3306/internship?serverTimezone=Asia/Shanghai&connectTimeout=2000&socketTimeout=5000",
                "user", System.getenv("LOCAL_DB_PASSWORD"))) {
            connection.setReadOnly(true);
            assertEquals(List.of(1L, 2L, 11L), students(connection, 100L, null, false));
            assertEquals(3L, count(connection, 100L, null, false));
            assertEquals(List.of(5L, 6L), students(connection, 200L, null, false));
            assertEquals(0L, count(connection, 999L, null, false));
            assertEquals(List.of(2L), students(connection, 100L, "TEST0002", false));
            assertEquals(1L, count(connection, 100L, "自主岗位", false));
            assertEquals(List.of(1L, 11L), students(connection, 100L, null, true));
            assertEquals(2L, count(connection, 100L, null, true));
        }
    }

    private List<Long> students(Connection connection, long companyId, String keyword, boolean duplicateName) throws Exception {
        try (PreparedStatement statement = prepare(connection, "findCurrentStudents", companyId, keyword, duplicateName);
             var rows = statement.executeQuery()) {
            List<Long> ids = new ArrayList<>();
            while (rows.next()) ids.add(rows.getLong("student_id"));
            return ids.stream().sorted().toList();
        }
    }

    private long count(Connection connection, long companyId, String keyword, boolean duplicateName) throws Exception {
        try (PreparedStatement statement = prepare(connection, "countCurrentStudents", companyId, keyword, duplicateName);
             var rows = statement.executeQuery()) {
            assertTrue(rows.next());
            return rows.getLong(1);
        }
    }

    private PreparedStatement prepare(Connection connection, String name, long companyId, String keyword, boolean duplicateName) throws Exception {
        BoundSql query = sql(name, companyId, keyword);
        PreparedStatement statement = connection.prepareStatement(fixtures(duplicateName) + query.getSql());
        Map<?, ?> params = (Map<?, ?>) query.getParameterObject();
        for (int i = 0; i < query.getParameterMappings().size(); i++) {
            statement.setObject(i + 1, params.get(query.getParameterMappings().get(i).getProperty()));
        }
        return statement;
    }

    private String fixtures(boolean duplicateName) {
        String numbers = IntStream.rangeClosed(1, 16).mapToObj(i -> "SELECT " + i + " AS id").collect(Collectors.joining(" UNION ALL "));
        return "WITH numbers AS (" + numbers + "), " + "company_users AS (SELECT 100 AS id, '本企业' AS company_name, 1 AS status, 1 AS audit_status, 0 AS recall_status UNION ALL SELECT 200, '其他企业', 1, 1, 0"
                + (duplicateName ? " UNION ALL SELECT 300, '本企业', 1, 1, 0" : "") + "), " + """
                student_users AS (
                    SELECT id, CONCAT('测试学生', id) AS name, CONCAT('TEST', LPAD(id, 4, '0')) AS student_user_id,
                           CASE WHEN id = 10 THEN 0 ELSE 1 END AS status, '2023' AS grade,
                           1 AS major_id, 1 AS class_id, '13800000000' AS phone, 'synthetic@example.test' AS email FROM numbers
                ),
                major AS (SELECT 1 AS id, '测试专业' AS name),
                class AS (SELECT 1 AS id, '测试班级' AS name),
                position AS (SELECT 500 AS id, '平台岗位' AS position_name),
                student_internship_status AS (
                    SELECT id AS student_id, CASE WHEN id = 3 THEN 2 WHEN id = 4 THEN 4 WHEN id = 13 THEN 0 ELSE 3 END AS status,
                           CASE WHEN id IN (2, 7, 8) THEN NULL WHEN id IN (5, 6) THEN 200 ELSE 100 END AS company_id,
                           CASE WHEN id IN (2, 7, 8) THEN NULL ELSE 500 END AS position_id,
                           CASE WHEN id = 15 THEN 0 ELSE 1 END AS company_confirm_status,
                           CASE WHEN id IN (3, 9) THEN DATE_ADD(NOW(), INTERVAL 1 DAY) ELSE DATE_SUB(NOW(), INTERVAL 1 DAY) END AS internship_start_time,
                           CASE WHEN id IN (4, 12) THEN DATE_SUB(NOW(), INTERVAL 1 HOUR) ELSE DATE_ADD(NOW(), INTERVAL 1 DAY) END AS internship_end_time,
                           CASE WHEN id IN (5, 6) THEN '其他企业' WHEN id = 7 THEN '本企业科技有限公司' WHEN id = 8 THEN '外部企业' ELSE '本企业' END AS company_name,
                           '自主岗位' AS position_name, '' AS student_name, '' AS grade, '' AS major_name, '' AS class_name, '' AS contact_phone, '' AS email
                    FROM numbers
                ),
                internship_stage_assignment AS (
                    SELECT id + 100 AS id, id AS student_id,
                           CASE WHEN id IN (2, 7, 8) THEN NULL WHEN id IN (5, 6, 14) THEN 200 ELSE 100 END AS company_id,
                           CASE WHEN id IN (5, 6, 14) THEN '其他企业' WHEN id = 7 THEN '本企业科技有限公司' WHEN id = 8 THEN '外部企业' ELSE '本企业' END AS company_name,
                           CASE WHEN id IN (2, 7, 8) THEN 'SELF_PRACTICE' ELSE 'JOB_APPLICATION' END AS source_type,
                           '自主岗位' AS position_name, CASE WHEN id = 13 THEN 0 ELSE 1 END AS current_flag,
                           CASE WHEN id = 3 THEN 'CONFIRMED' WHEN id = 4 THEN 'ENDED' WHEN id = 13 THEN 'WITHDRAWN' WHEN id = 16 THEN 'INTERRUPTED' ELSE 'IN_PROGRESS' END AS status
                    FROM numbers WHERE id != 11
                    UNION ALL SELECT 600, 6, 100, '本企业', 'JOB_APPLICATION', '旧岗位', 0, 'REPLACED'
                    UNION ALL SELECT 999, 1, 100, '本企业', 'JOB_APPLICATION', '平台岗位', 1, 'IN_PROGRESS'
                )
                """;
    }
}
