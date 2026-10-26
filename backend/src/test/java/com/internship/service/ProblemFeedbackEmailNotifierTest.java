package com.internship.service;

import com.internship.config.SystemSettingsConfig;
import com.internship.entity.AdminUser;
import com.internship.entity.ProblemFeedback;
import com.internship.entity.SystemSettings;
import com.internship.mapper.AdminUserMapper;
import com.internship.mapper.ProblemFeedbackMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.event.TransactionalEventListenerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProblemFeedbackEmailNotifierTest {
    private final ProblemFeedbackMapper feedbackMapper = mock(ProblemFeedbackMapper.class);
    private final AdminUserMapper adminMapper = mock(AdminUserMapper.class);
    private final EmailService mail = mock(EmailService.class);
    private final ProblemFeedbackEmailNotifier notifier = new ProblemFeedbackEmailNotifier(feedbackMapper, adminMapper, mail);
    private SystemSettings previous;

    @BeforeEach void configure() {
        previous = SystemSettingsConfig.getCurrentSettings();
        SystemSettings settings = new SystemSettings(); settings.setEmailNotifyEnabled(1); settings.setEmailNotifyScopes("PROBLEM_FEEDBACK");
        ReflectionTestUtils.setField(SystemSettingsConfig.class, "currentSettings", settings);
    }
    @AfterEach void restore() {
        ReflectionTestUtils.setField(SystemSettingsConfig.class, "currentSettings", previous);
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    private AdminUser admin(long id, String email) {
        AdminUser admin = new AdminUser(id); admin.setEmail(email); return admin;
    }
    private ProblemFeedback feedback() {
        ProblemFeedback feedback = new ProblemFeedback(); feedback.setId(12L); feedback.setUserId(8L);
        feedback.setUserType("student"); feedback.setUserName("测试学生"); feedback.setUserAccount("test001");
        feedback.setTitle("反馈标题"); feedback.setContent("<b>反馈正文</b>"); feedback.setCreateTime(new Date(0));
        when(feedbackMapper.findById(12L)).thenReturn(feedback); return feedback;
    }

    @Test void sendsToEnabledAdminsOncePerMailboxWithFeedbackDetails() {
        feedback();
        AdminUser disabled = admin(4, "disabled@example.com"); disabled.setStatus(0);
        AdminUser wrongRole = admin(5, "student@example.com"); wrongRole.setRole("ROLE_STUDENT");
        when(adminMapper.findFeedbackEmailRecipients()).thenReturn(List.of(admin(1," admin@example.com "), admin(2,"ADMIN@example.com"),
                admin(3,""), disabled, wrongRole, admin(6,"invalid"), admin(7,"second@example.com")));
        when(mail.sendWithReceipt(any(), any(), any(), any())).thenReturn(new EmailService.DeliveryOutcome("SENT", "accepted", false));
        notifier.onSubmitted(new ProblemFeedbackSubmittedEvent(12L));
        var body = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(mail).sendWithReceipt(eq("admin@example.com"), eq("【DeepIntern】收到新的问题反馈"), body.capture(), eq("problem-feedback-12-admin-1"));
        assertTrue(body.getValue().contains("测试学生（学生）")); assertTrue(body.getValue().contains("反馈标题"));
        assertTrue(body.getValue().contains("<b>反馈正文</b>")); assertTrue(body.getValue().contains("1970-01-01 08:00:00"));
        verify(mail).sendWithReceipt(eq("second@example.com"), any(), any(), eq("problem-feedback-12-admin-7"));
        verifyNoMoreInteractions(mail);
    }

    @Test void disabledMasterOrScopeDoesNotLookUpOrSendMail() {
        SystemSettings settings = SystemSettingsConfig.getCurrentSettings(); settings.setEmailNotifyEnabled(0);
        notifier.onSubmitted(new ProblemFeedbackSubmittedEvent(12L));
        settings.setEmailNotifyEnabled(1); settings.setEmailNotifyScopes("JOB_APPLICATION");
        notifier.onSubmitted(new ProblemFeedbackSubmittedEvent(12L));
        verifyNoInteractions(feedbackMapper, adminMapper, mail);
    }

    @Test void individualFailureDoesNotPreventNextAdminDelivery() {
        feedback(); when(adminMapper.findFeedbackEmailRecipients()).thenReturn(List.of(admin(1,"first@example.com"),admin(2,"second@example.com")));
        when(mail.sendWithReceipt(eq("first@example.com"), any(), any(), any())).thenThrow(new IllegalStateException("SMTP unavailable"));
        assertDoesNotThrow(() -> notifier.onSubmitted(new ProblemFeedbackSubmittedEvent(12L)));
        verify(mail).sendWithReceipt(eq("second@example.com"), any(), any(), any());
    }

    @Test void missingOrDeletedFeedbackDoesNotNotify() {
        notifier.onSubmitted(new ProblemFeedbackSubmittedEvent(12L));
        feedback().setDeleted(1); notifier.onSubmitted(new ProblemFeedbackSubmittedEvent(12L));
        verifyNoInteractions(adminMapper, mail);
    }

    @Test void eventWaitsForCommitAndNeverSendsOnRollback() {
        feedback(); when(adminMapper.findFeedbackEmailRecipients()).thenReturn(List.of(admin(1,"admin@example.com")));
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(TransactionalEventListenerFactory.class);
            context.registerBean(ProblemFeedbackEmailNotifier.class, () -> notifier);
            context.refresh();
            TransactionSynchronizationManager.setActualTransactionActive(true);
            TransactionSynchronizationManager.initSynchronization();
            context.publishEvent(new ProblemFeedbackSubmittedEvent(12L));
            verifyNoInteractions(mail);
            for (var callback : TransactionSynchronizationManager.getSynchronizations()) callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            verifyNoInteractions(mail);
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.initSynchronization();
            context.publishEvent(new ProblemFeedbackSubmittedEvent(12L));
            verifyNoInteractions(mail);
            for (var callback : TransactionSynchronizationManager.getSynchronizations()) callback.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
            verify(mail).sendWithReceipt(eq("admin@example.com"), any(), any(), any());
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
            clearInvocations(mail);
            context.publishEvent(new ProblemFeedbackSubmittedEvent(12L));
            verify(mail).sendWithReceipt(eq("admin@example.com"), any(), any(), any());
        }
    }
}
