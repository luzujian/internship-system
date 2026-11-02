package com.internship.mapper;

import com.internship.entity.ProblemFeedbackEmailReply;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Date;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in mapper verification; all temporary rows are rolled back and no mail transport is used. */
@EnabledIfEnvironmentVariable(named = "FEEDBACK_REPLY_DB_URL", matches = ".+")
class ProblemFeedbackEmailReplyDatabaseTest {
    @Test void businessAccountCanUseTheApprovedSchemaAndMapperWritesAreRolledBack() throws Exception {
        var dataSource = new DriverManagerDataSource(System.getenv("FEEDBACK_REPLY_DB_URL"), "user", "1234");
        var jdbc = new JdbcTemplate(dataSource);
        assertTrue(jdbc.queryForObject("SELECT CURRENT_USER()", String.class).startsWith("user@"));
        var configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(ProblemFeedbackEmailReplyMapper.class);
        var factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        factory.setMapperLocations(new ClassPathResource("com/internship/mapper/ProblemFeedbackMapper.xml"));
        var sessions = new SqlSessionTemplate(factory.getObject());
        var replies = sessions.getMapper(ProblemFeedbackEmailReplyMapper.class);
        var feedbacks = sessions.getMapper(ProblemFeedbackMapper.class);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        String requestId = UUID.randomUUID().toString();
        Long feedbackId = jdbc.queryForObject("SELECT id FROM problem_feedback WHERE deleted=0 ORDER BY id LIMIT 1", Long.class);
        transaction.execute(status -> {
            assertEquals(feedbackId, feedbacks.findByIdForUpdate(feedbackId).getId());
            feedbacks.findRecipientEmail(feedbackId);
            var row = new ProblemFeedbackEmailReply();
            row.setFeedbackId(feedbackId); row.setRequestId(requestId);
            row.setRecipientEmail("qa@example.invalid"); row.setSubject("数据库回滚验证");
            row.setReplyContent("临时测试记录"); row.setMailBody("临时测试记录，不触发邮件发送。");
            row.setAdminId(1L); row.setAdminName("数据库测试"); row.setFeedbackStatus("processing");
            row.setDeliveryStatus("SENDING"); row.setCreatedAt(new Date());
            assertEquals(1, replies.insert(row));
            assertNotNull(row.getId());
            assertEquals(row.getId(), replies.findByRequestId(requestId).getId());
            assertEquals(row.getId(), replies.findHistory(feedbackId).getFirst().getId());
            assertEquals(1, replies.finish(row.getId(), "FAILED", "数据库测试", new Date()));
            assertEquals("FAILED", replies.findByRequestId(requestId).getDeliveryStatus());
            assertEquals(0, replies.finish(row.getId(), "SENT", "不应重复完成", new Date()));
            assertEquals(1, jdbc.update("DELETE FROM problem_feedback_email_reply WHERE id=?", row.getId()));
            status.setRollbackOnly();
            return null;
        });
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM problem_feedback_email_reply WHERE request_id=?", Long.class, requestId));
    }
}
