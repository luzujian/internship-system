package com.internship.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PositionContactsMapperTest {
    private Configuration configuration() throws Exception {
        Configuration c = new Configuration();
        for (String mapper : List.of("PositionMapper", "StudentJobApplicationMapper", "InterviewInvitationMapper", "CompanyUserMapper")) {
            String resource = "com/internship/mapper/" + mapper + ".xml";
            try (var input = Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(input, c, resource, c.getSqlFragments()).parse();
            }
        }
        return c;
    }
    @Test void contactIdCanBeClearedExplicitlyButOmittedFieldPreservesOldBinding() throws Exception {
        Configuration c = configuration();
        com.internship.entity.Position request = new com.internship.entity.Position(); request.setId(1L);
        String sql = c.getMappedStatement("com.internship.mapper.PositionMapper.update").getBoundSql(request).getSql();
        assertFalse(sql.contains("contact_id ="));
        request.setContactId(null);
        sql = c.getMappedStatement("com.internship.mapper.PositionMapper.update").getBoundSql(request).getSql();
        assertTrue(sql.contains("contact_id ="));
    }
    @Test void companyImportCannotOverwriteAccountOrApprovalFields() throws Exception {
        var company = new com.internship.entity.CompanyUser(); company.setId(100L); company.setContactPerson("甲"); company.setContactPhone("13800000001");
        company.setUsername("must-preserve"); company.setPassword("must-preserve"); company.setAuditStatus(0); company.setContactEmail("");
        String sql = configuration().getMappedStatement("com.internship.mapper.CompanyUserMapper.updateImportedProfile").getBoundSql(company).getSql();
        assertFalse(sql.contains("username")); assertFalse(sql.contains("password")); assertFalse(sql.contains("audit_status"));
        assertFalse(sql.contains("company_name")); assertFalse(sql.contains("contact_email")); assertTrue(sql.contains("WHERE id ="));
    }
    @Test @EnabledIfEnvironmentVariable(named = "COMPANY_CONTACT_LOCAL_TESTS", matches = "true")
    void twoPositionsUseDifferentContactsAndLegacyApplicationsAndInvitationsUseTheSameRule() throws Exception {
        Configuration c = configuration();
        try (Connection connection = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/internship?serverTimezone=Asia/Shanghai", "user", System.getenv("LOCAL_DB_PASSWORD"))) {
            connection.setReadOnly(true);
            for (long id : List.of(1L, 2L, 3L)) {
                String person = id == 1 ? "联系人甲" : id == 2 ? "联系人乙" : "企业原联系人";
                String phone = id == 1 ? "13800000001" : id == 2 ? "13800000002" : "13800000000";
                String email = id == 1 ? "one@example.test" : id == 2 ? "" : "legacy@example.test";
                assertContact(connection, c, "PositionMapper.findById", Map.of("id", id), person, phone, email, "contact_");
                assertContact(connection, c, "StudentJobApplicationMapper.findById", Map.of("id", id), person, phone, email, "contact_");
                assertContact(connection, c, "InterviewInvitationMapper.findById", Map.of("id", id), person, phone, email, "resolved_contact_");
            }
            assertContact(connection, c, "InterviewInvitationMapper.findByStudentAndPosition", Map.of("studentId", 77L, "positionId", 2L), "联系人乙", "13800000002", "", "resolved_contact_");
        }
    }
    private void assertContact(Connection connection, Configuration c, String name, Map<String,Object> params, String person, String phone, String email, String prefix) throws Exception {
        BoundSql sql = c.getMappedStatement("com.internship.mapper." + name).getBoundSql(params);
        try (PreparedStatement stmt = connection.prepareStatement(FIXTURES + sql.getSql())) {
            int index = 1;
            for (var parameter : sql.getParameterMappings()) stmt.setObject(index++, params.get(parameter.getProperty()));
            try (ResultSet rows = stmt.executeQuery()) {
                assertTrue(rows.next(), name);
                assertEquals(person, rows.getString(prefix + "person"), name);
                assertEquals(phone, rows.getString(prefix + "phone"), name);
                assertEquals(email, rows.getString(prefix + "email"), name);
                assertFalse(rows.next(), "contact joins must not duplicate rows");
            }
        }
    }
    private static final String FIXTURES = """
        WITH company_users AS (
            SELECT 100 AS id, '企业原联系人' AS contact_person, '13800000000' AS contact_phone,
                   'legacy@example.test' AS contact_email, '测试企业' AS company_name, '' AS website, '' AS industry
        ), company_contact AS (
            SELECT 10 AS id, 100 AS company_id, '联系人甲' AS name, '13800000001' AS phone, 'one@example.test' AS email
            UNION ALL SELECT 20, 100, '联系人乙', '13800000002', ''
        ), position AS (
            SELECT 1 AS id, 100 AS company_id, 10 AS contact_id, '岗位甲' AS position_name,
                   '实习' AS position_type, '' AS description, '' AS requirements, 0 AS view_count,
                   NOW() AS publish_date, 1 AS category_id, '广东' AS province, '深圳' AS city,
                   '' AS district, '' AS detail_address
            UNION ALL SELECT 2, 100, 20, '岗位乙', '实习', '', '', 0, NOW(), 1, '广东', '深圳', '', ''
            UNION ALL SELECT 3, 100, NULL, '旧岗位', '实习', '', '', 0, NOW(), 1, '广东', '深圳', '', ''
        ), position_category AS (SELECT 1 AS id, '工程' AS name),
        student_job_application AS (
            SELECT 1 AS id, 77 AS student_id, 1 AS position_id, 100 AS company_id, NOW() AS create_time
            UNION ALL SELECT 2, 77, 2, 100, NOW()
            UNION ALL SELECT 3, 77, 3, 100, NOW()
        ), interview_invitation AS (
            SELECT 1 AS id, 77 AS student_id, 1 AS position_id, 100 AS company_id,
                   '历史快照' AS contact_person, '10000000000' AS contact_phone,
                   NOW() AS create_time, NOW() AS update_time
            UNION ALL SELECT 2, 77, 2, 100, '历史快照', '10000000000', NOW(), NOW()
            UNION ALL SELECT 3, 77, 3, 100, '历史快照', '10000000000', NOW(), NOW()
        )
        """;
}
