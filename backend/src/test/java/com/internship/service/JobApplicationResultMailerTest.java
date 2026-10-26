package com.internship.service;

import com.internship.config.SystemSettingsConfig;
import com.internship.entity.SystemSettings;
import com.internship.entity.StudentUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobApplicationResultMailerTest {

    @Mock
    private StudentUserService studentUserService;
    @Mock
    private EmailService emailService;
    @Mock
    private AgentNotificationGuardService notificationGuard;

    private JobApplicationResultMailer mailer;
    private SystemSettings previousSettings;

    @BeforeEach
    void setUp() {
        previousSettings = SystemSettingsConfig.getCurrentSettings();
        SystemSettings settings = new SystemSettings();
        settings.setEmailNotifyEnabled(1);
        org.springframework.test.util.ReflectionTestUtils.setField(SystemSettingsConfig.class, "currentSettings", settings);
        mailer = new JobApplicationResultMailer();
        org.springframework.test.util.ReflectionTestUtils.setField(mailer, "studentUserService", studentUserService);
        org.springframework.test.util.ReflectionTestUtils.setField(mailer, "emailService", emailService);
        org.springframework.test.util.ReflectionTestUtils.setField(mailer, "notificationGuard", notificationGuard);
    }

    @AfterEach
    void restoreSettings() {
        org.springframework.test.util.ReflectionTestUtils.setField(SystemSettingsConfig.class, "currentSettings", previousSettings);
    }

    @Test
    void directHireSendsOfferEmailAndWritesReceipt() {
        JobApplicationResultNotification notification = notification("interview_passed", true);
        StudentUser student = student("张三", "zhangsan@example.com");
        AgentNotificationGuardService.Claim claim = claim();
        when(notificationGuard.claim(eq(400L), eq(notification.todoId()), eq("EMAIL"), eq(null), anyInt()))
                .thenReturn(claim);
        when(studentUserService.findById(400L)).thenReturn(student);
        when(emailService.sendWithReceipt(
                eq("zhangsan@example.com"), any(), any(), eq(notification.deliveryId()), eq("DeepIntern")))
                .thenReturn(new EmailService.DeliveryOutcome("SENT", "accepted", false));

        mailer.send(notification);

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendWithReceipt(
                eq("zhangsan@example.com"), subject.capture(), body.capture(),
                eq(notification.deliveryId()), eq("DeepIntern"));
        assertTrue(subject.getValue().contains("直接录取"));
        assertTrue(body.getValue().contains("测试企业"));
        assertTrue(body.getValue().contains("Java实习生"));
        assertTrue(body.getValue().contains("实习确认表"));
        verify(notificationGuard).finish(eq(claim), eq("SENT"), eq(null));
    }

    @Test
    void approvedUsesInterviewWordingInsteadOfHired() {
        JobApplicationResultNotification notification = notification("approved", false);
        StudentUser student = student("张三", "zhangsan@example.com");
        AgentNotificationGuardService.Claim claim = claim();
        when(notificationGuard.claim(anyLong(), any(), any(), any(), anyInt())).thenReturn(claim);
        when(studentUserService.findById(400L)).thenReturn(student);
        when(emailService.sendWithReceipt(any(), any(), any(), any(), any()))
                .thenReturn(new EmailService.DeliveryOutcome("SENT", "accepted", false));

        mailer.send(notification);

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendWithReceipt(
                eq("zhangsan@example.com"), subject.capture(), body.capture(), any(), any());
        assertTrue(subject.getValue().contains("通过初审"));
        assertTrue(body.getValue().contains("面试安排"));
        assertTrue(!body.getValue().contains("已录用"));
    }

    @Test
    void rejectionEmailIncludesEnterpriseReason() {
        JobApplicationResultNotification notification = new JobApplicationResultNotification(
                100L, 400L, 200L, "测试企业", 300L, "Java实习生",
                "rejected", false, "专业方向与岗位要求不匹配");
        StudentUser student = student("张三", "zhangsan@example.com");
        AgentNotificationGuardService.Claim claim = claim();
        when(notificationGuard.claim(anyLong(), any(), any(), any(), anyInt())).thenReturn(claim);
        when(studentUserService.findById(400L)).thenReturn(student);
        when(emailService.sendWithReceipt(any(), any(), any(), any(), any()))
                .thenReturn(new EmailService.DeliveryOutcome("SENT", "accepted", false));

        mailer.send(notification);

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendWithReceipt(
                eq("zhangsan@example.com"), any(), body.capture(), any(), any());
        assertTrue(body.getValue().contains("拒绝理由：专业方向与岗位要求不匹配"));
    }

    @Test
    void missingEmailCancelsDelivery() {
        JobApplicationResultNotification notification = notification("rejected", false);
        AgentNotificationGuardService.Claim claim = claim();
        when(notificationGuard.claim(anyLong(), any(), any(), any(), anyInt())).thenReturn(claim);
        when(studentUserService.findById(400L)).thenReturn(student("张三", " "));

        mailer.send(notification);

        verify(emailService, never()).sendWithReceipt(any(), any(), any(), any(), any());
        verify(notificationGuard).finish(eq(claim), eq("CANCELLED"), eq(null));
    }

    @Test
    void duplicateGuardPreventsSecondEmail() {
        JobApplicationResultNotification notification = notification("interview_failed", false);
        AgentNotificationGuardService.Claim duplicate =
                new AgentNotificationGuardService.Claim("key", null, false, "duplicate");
        when(notificationGuard.claim(anyLong(), any(), any(), any(), anyInt())).thenReturn(duplicate);

        mailer.send(notification);

        verify(studentUserService, never()).findById(anyLong());
        verify(emailService, never()).sendWithReceipt(any(), any(), any(), any(), any());
        verify(notificationGuard, never()).finish(any(), any(), any());
    }

    private StudentUser student(String name, String email) {
        StudentUser student = new StudentUser();
        student.setId(400L);
        student.setName(name);
        student.setEmail(email);
        student.setStatus(1);
        return student;
    }

    private AgentNotificationGuardService.Claim claim() {
        return new AgentNotificationGuardService.Claim("key", "token", true, null);
    }

    private JobApplicationResultNotification notification(String status, boolean directHire) {
        return new JobApplicationResultNotification(
                100L, 400L, 200L, "测试企业", 300L, "Java实习生", status, directHire);
    }
}
