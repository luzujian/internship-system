package com.internship.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.config.SystemSettingsConfig;
import com.internship.entity.AdminUser;
import com.internship.entity.SystemSettings;
import com.internship.entity.User;
import com.internship.mapper.AdminUserMapper;
import com.internship.mapper.ProblemFeedbackMapper;
import com.internship.service.EmailService;
import com.internship.service.ProblemFeedbackEmailNotifier;
import com.internship.service.impl.AdminUserServiceImpl;
import com.internship.utils.CurrentHolder;
import com.internship.websocket.AnnouncementWebSocketHandler;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.event.TransactionalEventListenerFactory;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Opt-in local-only integration. All temporary DML is restored and SMTP is replaced by a capture. */
@EnabledIfEnvironmentVariable(named = "RUN_LOCAL_DB_TESTS", matches = "true")
class AdminFeedbackEmailDatabaseTest {
    @org.springframework.context.annotation.Configuration
    @EnableAsync
    static class AsyncConfig {
        @Bean ThreadPoolTaskExecutor taskExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1); executor.setMaxPoolSize(1);
            executor.setThreadNamePrefix("feedback-mail-test-");
            executor.setWaitForTasksToCompleteOnShutdown(true);
            return executor;
        }
    }

    @Test void profilePersistenceAndSubmittedFeedbackReachIsolatedMailbox() throws Exception {
        String password = System.getenv("LOCAL_DB_PASSWORD"); assertNotNull(password);
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:mysql://127.0.0.1:3306/internship?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true", "user", password);
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        assertTrue(jdbc.queryForObject("SELECT CURRENT_USER()", String.class).startsWith("user@"));
        Map<String,Object> original = jdbc.queryForMap("SELECT * FROM admin_users WHERE status=1 ORDER BY id LIMIT 1");
        long adminId = ((Number) original.get("id")).longValue();
        String username = original.get("username").toString();
        String title = "邮箱提醒本地验收-" + UUID.randomUUID();
        String recipient = "feedback-admin@example.test";
        SystemSettings previous = SystemSettingsConfig.getCurrentSettings();
        List<Map<String,String>> captured = new CopyOnWriteArrayList<>();
        CountDownLatch delivered = new CountDownLatch(1);
        EmailService mail = mock(EmailService.class);
        when(mail.sendWithReceipt(any(),any(),any(),any())).thenAnswer(call -> {
            captured.add(Map.of("to",call.getArgument(0),"subject",call.getArgument(1),
                    "body",call.getArgument(2),"deliveryId",call.getArgument(3),"thread",Thread.currentThread().getName()));
            delivered.countDown(); return new EmailService.DeliveryOutcome("SENT","local test capture",false);
        });
        SystemSettings settings = new SystemSettings(); settings.setEmailNotifyEnabled(1); settings.setEmailNotifyScopes("PROBLEM_FEEDBACK");
        ReflectionTestUtils.setField(SystemSettingsConfig.class,"currentSettings",settings);
        try (var context = new AnnotationConfigApplicationContext()) {
            Configuration mybatis = new Configuration(); mybatis.setMapUnderscoreToCamelCase(true);
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean(); factory.setDataSource(ds); factory.setConfiguration(mybatis);
            factory.setMapperLocations(new ClassPathResource("com/internship/mapper/AdminUserMapper.xml"),
                    new ClassPathResource("com/internship/mapper/ProblemFeedbackMapper.xml"));
            SqlSessionTemplate session = new SqlSessionTemplate(factory.getObject());
            AdminUserMapper admins = session.getMapper(AdminUserMapper.class);
            ProblemFeedbackMapper feedbacks = session.getMapper(ProblemFeedbackMapper.class);
            context.register(AsyncConfig.class);
            context.registerBean(TransactionalEventListenerFactory.class);
            context.registerBean(AdminUserMapper.class, () -> admins);
            context.registerBean(ProblemFeedbackMapper.class, () -> feedbacks);
            context.registerBean(EmailService.class, () -> mail);
            context.registerBean(ProblemFeedbackEmailNotifier.class);
            context.refresh();
            AdminUserServiceImpl adminService = new AdminUserServiceImpl();
            ReflectionTestUtils.setField(adminService,"adminUserMapper",admins);
            ReflectionTestUtils.setField(adminService,"passwordEncoder",mock(PasswordEncoder.class));
            AdminUserController profiles = new AdminUserController();
            ReflectionTestUtils.setField(profiles,"adminUserService",adminService);
            ProblemFeedbackController submissions = new ProblemFeedbackController();
            ReflectionTestUtils.setField(submissions,"problemFeedbackMapper",feedbacks);
            ReflectionTestUtils.setField(submissions,"eventPublisher",context);
            ReflectionTestUtils.setField(submissions,"webSocketHandler",mock(AnnouncementWebSocketHandler.class));
            var mvc = MockMvcBuilders.standaloneSetup(profiles,submissions).build();
            ObjectMapper json = new ObjectMapper();
            User admin = new User(); admin.setId(adminId); admin.setRole("ROLE_ADMIN"); CurrentHolder.setUser(admin);
            String profileJson = json.writeValueAsString(Map.of("username",username,
                    "phone",original.get("phone")==null?"":original.get("phone").toString(),"email","  "+recipient+"  "));
            var save = mvc.perform(put("/api/admin/admins/me").contentType(MediaType.APPLICATION_JSON).content(profileJson)).andReturn();
            assertEquals(200,json.readTree(save.getResponse().getContentAsString()).get("code").asInt());
            var profile = mvc.perform(get("/api/admin/admins/me")).andReturn();
            assertEquals(recipient,json.readTree(profile.getResponse().getContentAsString()).path("data").path("email").asText());
            assertEquals(original.get("password"),jdbc.queryForObject("SELECT password FROM admin_users WHERE id=?",String.class,adminId));
            assertEquals(original.get("admin_level"),jdbc.queryForMap("SELECT admin_level FROM admin_users WHERE id=?",adminId).get("admin_level"));
            var invalid = mvc.perform(put("/api/admin/admins/me").contentType(MediaType.APPLICATION_JSON)
                    .content(profileJson.replace(recipient,"invalid-email"))).andReturn();
            assertEquals(400,invalid.getResponse().getStatus());
            assertEquals(recipient,admins.findById(adminId).getEmail());

            Map<String,Object> student = jdbc.queryForMap("SELECT id,name,student_user_id FROM student_users WHERE status=1 ORDER BY id LIMIT 1");
            User actor = new User(); actor.setId(((Number)student.get("id")).longValue()); actor.setRole("ROLE_STUDENT");
            actor.setName(student.get("name").toString()); actor.setUsername(student.get("student_user_id").toString()); CurrentHolder.setUser(actor);
            TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(ds));
            transaction.executeWithoutResult(tx -> {
                try {
                    var submit = mvc.perform(post("/api/problem-feedback").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("title",title,"content","本地反馈提醒验收正文","userId",99999,"userType","company")))).andReturn();
                    assertEquals(200,json.readTree(submit.getResponse().getContentAsString()).path("code").asInt());
                    assertEquals(0,captured.size(),"Mail must wait for commit");
                } catch (Exception failure) { throw new RuntimeException(failure); }
            });
            assertTrue(delivered.await(10,TimeUnit.SECONDS),"Missing isolated mail receipt");
            assertEquals(1,captured.size());
            Map<String,String> receipt=captured.getFirst(); assertEquals(recipient,receipt.get("to"));
            assertTrue(receipt.get("body").contains(title)); assertTrue(receipt.get("body").contains("本地反馈提醒验收正文"));
            assertTrue(receipt.get("thread").startsWith("feedback-mail-test-"));
            Map<String,Object> persisted=jdbc.queryForMap("SELECT id,user_type,user_id FROM problem_feedback WHERE title=?",title);
            assertEquals("student",persisted.get("user_type")); assertEquals(actor.getId().longValue(),((Number)persisted.get("user_id")).longValue());
            Path artifact=Path.of("..",".codex","admin-feedback-email-20261026","local-mail-receipt.json");
            Files.createDirectories(artifact.getParent());
            Files.writeString(artifact,json.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                    "target","127.0.0.1:3306/internship","account","user","transport","isolated test capture",
                    "profileReadWriteVerified",true,"afterCommitVerified",true,"asyncVerified",true,"receipt",receipt,"productionModified",false)));
        } finally {
            CurrentHolder.remove();
            ReflectionTestUtils.setField(SystemSettingsConfig.class,"currentSettings",previous);
            jdbc.update("DELETE FROM problem_feedback WHERE title=?",title);
            jdbc.update("UPDATE admin_users SET username=?,phone=?,email=?,update_time=? WHERE id=?",
                    original.get("username"),original.get("phone"),original.get("email"),original.get("update_time"),adminId);
            assertEquals(original,jdbc.queryForMap("SELECT * FROM admin_users WHERE id=?",adminId));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM problem_feedback WHERE title=?",Integer.class,title));
        }
    }
}
