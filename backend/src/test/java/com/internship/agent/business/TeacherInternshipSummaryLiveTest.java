package com.internship.agent.business;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.mapper.AgentReadMapper;
import com.internship.mapper.JobSearchStatusMapper;
import com.internship.service.JobSearchStatusService;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.io.InputStream;
import java.util.Set;
import static com.internship.agent.business.AgentTypes.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "DEEPINTERN_LIVE_DB", matches = "true")
class TeacherInternshipSummaryLiveTest {
    private static SqlSessionFactory factory;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeAll static void setup() throws Exception {
        Configuration configuration = new Configuration();
        configuration.setEnvironment(new Environment("live", new JdbcTransactionFactory(),
                new UnpooledDataSource("com.mysql.cj.jdbc.Driver",
                        System.getenv("DEEPINTERN_DB_URL"),
                        System.getenv("DEEPINTERN_DB_USER"),
                        System.getenv("DEEPINTERN_DB_PASSWORD"))));
        for (String resource : new String[]{
                "com/internship/mapper/AgentReadMapper.xml",
                "com/internship/mapper/JobSearchStatusMapper.xml"}) {
            try (InputStream input = Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        factory = new SqlSessionFactoryBuilder().build(configuration);
    }

    @Test void realScopesMatchDirectBusinessCounts() throws Exception {
        try (SqlSession session = factory.openSession()) {
            TeacherInternshipSummaryService service = new TeacherInternshipSummaryService(
                    session.getMapper(AgentReadMapper.class),
                    new JobSearchStatusService(session.getMapper(JobSearchStatusMapper.class)));

            Actor college = new Actor("ROLE_TEACHER_COLLEGE:47:false:", 47L, "ROLE_TEACHER_COLLEGE", "学院教师", "COLLEGE", 24L, null, false, Set.of("view:student_tracking"));
            ToolResult collegeResult = service.query(college, json.readTree("{\"scopeType\":\"COLLEGE\",\"view\":\"summary\"}"), new Session());
            assertSummary(collegeResult, 205L, 175L, 30L);

            Actor department15 = new Actor("ROLE_TEACHER_DEPARTMENT:48:false:", 48L, "ROLE_TEACHER_DEPARTMENT", "系室教师", "DEPARTMENT", 24L, 15L, false, Set.of("view:student_tracking"));
            assertSummary(service.query(department15, json.readTree("{\"scopeType\":\"DIVISION\",\"scopeName\":\"生工系\",\"view\":\"summary\"}"), new Session()), 63L, 56L, 7L);
            assertThrows(AgentException.class, () -> service.query(department15, json.readTree("{\"scopeType\":\"DIVISION\",\"scopeName\":\"信工系\",\"view\":\"summary\"}"), new Session()));

            Actor department16 = new Actor("ROLE_TEACHER_DEPARTMENT:61:false:", 61L, "ROLE_TEACHER_DEPARTMENT", "系室教师", "DEPARTMENT", 24L, 16L, false, Set.of("view:student_tracking"));
            assertSummary(service.query(department16, json.readTree("{\"scopeType\":\"CLASS\",\"scopeName\":\"23信管1班\",\"view\":\"summary\"}"), new Session()), 14L, 10L, 4L);

            Actor counselor = new Actor("ROLE_TEACHER_COUNSELOR:194:false:", 194L, "ROLE_TEACHER_COUNSELOR", "辅导员", "COUNSELOR", null, null, false, Set.of("view:student_tracking"));
            Session counselorSession = new Session();
            assertSummary(service.query(counselor, json.readTree("{\"scopeType\":\"COUNSELOR\",\"view\":\"summary\"}"), counselorSession), 39L, 33L, 6L);
            assertSummary(service.query(counselor, json.readTree("{\"scopeType\":\"CLASS\",\"scopeName\":\"23级生工7班\",\"view\":\"summary\"}"), new Session()), 8L, 6L, 2L);
            ToolResult followUp = service.query(counselor, json.readTree("{\"scopeType\":\"LAST\",\"view\":\"focus\"}"), counselorSession);
            assertEquals(6L, followUp.total());
            assertEquals(6L, followUp.facts().get("focusCount"));

            Actor noPermission = new Actor("ROLE_TEACHER_COLLEGE:47:false:", 47L, "ROLE_TEACHER_COLLEGE", "无权限", "COLLEGE", 24L, null, false, Set.of());
            assertEquals("FORBIDDEN", assertThrows(AgentException.class,
                    () -> service.query(noPermission, json.readTree("{\"scopeType\":\"COLLEGE\"}"), new Session())).getCode());
        }
    }

    private static void assertSummary(ToolResult result, long total, long placed, long withoutOffer) {
        assertEquals(total, result.facts().get("total"));
        assertEquals(placed, result.facts().get("placed"));
        assertEquals(withoutOffer, result.facts().get("withoutOffer"));
        assertEquals(withoutOffer, result.facts().get("focusCount"));
    }
}
