package com.internship.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.DriverManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TeacherStudentDetailMapperTest {
    private Configuration configuration;
    private static final String STATEMENT = "com.internship.mapper.StudentInternshipStatusMapper.findTeacherDetailExtrasByStudentIds";

    @BeforeEach void readMapper() throws Exception {
        configuration = new Configuration();
        for (String resource : List.of("com/internship/mapper/JobSearchStatusMapper.xml", "com/internship/mapper/StudentInternshipStatusMapper.xml")) {
            try (var input = Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(input,configuration,resource,configuration.getSqlFragments()).parse();
            }
        }
    }

    @Test void supplementalReadRequiresExplicitAuthorizedStudentIdsAndKeepsSourceIdentity() {
        String empty = configuration.getMappedStatement(STATEMENT).getBoundSql(Map.of("studentIds",List.of())).getSql();
        assertTrue(empty.contains("AND 1 = 0"));
        var bound = configuration.getMappedStatement(STATEMENT).getBoundSql(Map.of("studentIds",List.of(101L,102L)));
        assertEquals(2,bound.getParameterMappings().size());
        String sql = bound.getSql().replaceAll("\\s+"," ");
        assertTrue(sql.contains("source_assignment.source_type = 'SELF_PRACTICE'"));
        assertTrue(sql.contains("source_assignment.source_assignment_id"));
        assertTrue(sql.contains("sa.status = 'approved'"));
        assertFalse(sql.contains("su.password"));
    }

    /** SELECT-only fixtures reproduce the different collations of the live historical tables. */
    @Test
    @EnabledIfEnvironmentVariable(named="TEACHER_DETAIL_READ_ONLY_DB_TESTS",matches="true")
    void mysqlReadsCurrentPlatformAndAutonomousContactsWithoutTakingLaterOrRejectedApplications() throws Exception {
        String fixtures = """
                WITH student_users AS (
                    SELECT 1 id,1 status,1 major_id,'10' department,'13800000001' phone,'one@example.test' email
                    UNION ALL SELECT 2,1,1,'10','13800000002','two@example.test'
                    UNION ALL SELECT 3,1,1,'10','13800000003','three@example.test'
                    UNION ALL SELECT 4,1,1,'10',NULL,NULL
                    UNION ALL SELECT 5,1,1,'10',NULL,NULL
                ), student_internship_status AS (
                    SELECT 11 id,1 student_id,3 status,10 company_id,
                        _utf8mb4'' COLLATE utf8mb4_0900_ai_ci company_name,
                        _utf8mb4'平台岗位' COLLATE utf8mb4_0900_ai_ci position_name,CAST('2026-01-01' AS DATETIME) create_time
                    UNION ALL SELECT 12,2,3,NULL,'合作单位','自主岗位','2026-01-01'
                    UNION ALL SELECT 13,3,3,NULL,'系统外单位','自主岗位','2026-01-01'
                    UNION ALL SELECT 15,5,0,NULL,'旧单位','自主岗位','2026-01-01'
                ), major AS (SELECT 1 id,10 department_id), department AS (SELECT 10 id,'测试学院' name),
                company_users AS (
                    SELECT 10 id,'医疗设备研发' introduction,'市辖区' city,'北京市' province,'企业联系人' contact_person,'010-12345678' contact_phone
                ), internship_stage_assignment AS (
                    SELECT 71 id,2 student_id,'SELF_PRACTICE' source_type,201 source_assignment_id,1 current_flag,
                        'IN_PROGRESS' status,_utf8mb4'合作单位' COLLATE utf8mb4_0900_ai_ci company_name,
                        _utf8mb4'自主岗位' COLLATE utf8mb4_0900_ai_ci position_name,CAST('2026-01-01' AS DATETIME) create_time
                ), student_application AS (
                    SELECT 201 id,2 student_id,'selfPractice' application_type,'approved' status,
                        _utf8mb4'合作单位' COLLATE utf8mb4_unicode_ci company,
                        _utf8mb4'自主岗位' COLLATE utf8mb4_unicode_ci position_name,
                        10 company_id,'申请城市' company_city,'当前人事经理' hr_manager_name,'020-11111111' hr_manager_phone,CAST('2026-01-01' AS DATETIME) review_time
                    UNION ALL SELECT 202,2,'selfPractice','approved','合作单位','自主岗位',10,'后来城市','后来联系人','020-22222222','2026-02-01'
                    UNION ALL SELECT 203,2,'selfPractice','pending','合作单位','自主岗位',10,'待审城市','待审联系人','020-33333333','2026-03-01'
                    UNION ALL SELECT 301,3,'selfPractice','approved','系统外单位','自主岗位',NULL,'广州市','系统外人事经理','020-44444444','2026-01-01'
                    UNION ALL SELECT 302,3,'selfPractice','rejected','系统外单位','自主岗位',NULL,'驳回城市','驳回联系人','020-55555555','2026-02-01'
                    UNION ALL SELECT 501,5,'selfPractice','approved','旧单位','自主岗位',10,'历史城市','历史联系人','020-66666666','2026-01-01'
                )
                """;
        var bound = configuration.getMappedStatement(STATEMENT).getBoundSql(Map.of("studentIds",List.of(1L,2L,3L,4L,5L)));
        try (var connection = DriverManager.getConnection(System.getenv("TEACHER_DETAIL_READ_ONLY_DB_URL"),"user","1234")) {
            connection.setReadOnly(true);
            try (var statement = connection.prepareStatement(fixtures + bound.getSql())) {
                for (int i=1;i<=5;i++) statement.setLong(i,i);
                Map<Long,Map<String,String>> rows = new HashMap<>();
                try (var result = statement.executeQuery()) {
                    while (result.next()) {
                        Map<String,String> fields = new HashMap<>();
                        for (String key : List.of("department_name","student_phone","company_business","company_city","hr_manager_name","hr_manager_phone")) {
                            fields.put(key,result.getString(key));
                        }
                        rows.put(result.getLong("student_id"),fields);
                    }
                }
                assertEquals(5,rows.size());
                assertEquals("企业联系人",rows.get(1L).get("hr_manager_name"));
                assertEquals("北京市",rows.get(1L).get("company_city"));
                assertEquals("医疗设备研发",rows.get(1L).get("company_business"));
                assertEquals("当前人事经理",rows.get(2L).get("hr_manager_name"));
                assertEquals("020-11111111",rows.get(2L).get("hr_manager_phone"));
                assertEquals("申请城市",rows.get(2L).get("company_city"));
                assertEquals("医疗设备研发",rows.get(2L).get("company_business"));
                assertEquals("系统外人事经理",rows.get(3L).get("hr_manager_name"));
                assertEquals("广州市",rows.get(3L).get("company_city"));
                assertNull(rows.get(3L).get("company_business"));
                assertEquals("测试学院",rows.get(4L).get("department_name"));
                assertNull(rows.get(4L).get("hr_manager_phone"));
                assertNull(rows.get(5L).get("hr_manager_name"));
            }
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named="TEACHER_DETAIL_READ_ONLY_DB_TESTS",matches="true")
    void realTablesSupportBothSingleStudentDetailsAndBatchExportReads() throws Exception {
        var dataSource = new DriverManagerDataSource(System.getenv("TEACHER_DETAIL_READ_ONLY_DB_URL"),"user","1234");
        configuration.setEnvironment(new Environment("read-only-detail-check", new JdbcTransactionFactory(), dataSource));
        try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession()) {
            var connection = session.getConnection();
            connection.setReadOnly(true);
            var ids = new java.util.ArrayList<Long>();
            try (var query = connection.prepareStatement("SELECT id FROM student_users WHERE status=1 ORDER BY id LIMIT 100");
                 var result = query.executeQuery()) {
                while (result.next()) ids.add(result.getLong(1));
            }
            assertFalse(ids.isEmpty(), "Live verification needs at least one active student");
            var mapper = session.getMapper(StudentInternshipStatusMapper.class);
            var single = mapper.findTeacherDetailExtrasByStudentIds(List.of(ids.getFirst()));
            assertEquals(1, single.size());
            assertEquals(ids.getFirst(), single.getFirst().getStudentId());
            var batch = mapper.findTeacherDetailExtrasByStudentIds(ids);
            assertEquals(ids.size(), batch.size());
            assertEquals(new java.util.HashSet<>(ids), batch.stream()
                    .map(com.internship.entity.dto.TeacherStudentDetailExtras::getStudentId)
                    .collect(java.util.stream.Collectors.toSet()));
            assertTrue(mapper.findTeacherDetailExtrasByStudentIds(List.of()).isEmpty());
        }
    }
}
