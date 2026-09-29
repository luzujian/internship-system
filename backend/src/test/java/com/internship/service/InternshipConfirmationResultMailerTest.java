package com.internship.service;

import com.internship.entity.StudentUser;
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
class InternshipConfirmationResultMailerTest {

    @Mock
    private StudentUserService studentUserService;
    @Mock
    private EmailService emailService;
    @Mock
    private AgentNotificationGuardService notificationGuard;

    private InternshipConfirmationResultMailer mailer;

    @BeforeEach
    void setUp() {
        mailer = new InternshipConfirmationResultMailer();
        org.springframework.test.util.ReflectionTestUtils.setField(mailer, "studentUserService", studentUserService);
        org.springframework.test.util.ReflectionTestUtils.setField(mailer, "emailService", emailService);
        org.springframework.test.util.ReflectionTestUtils.setField(mailer, "notificationGuard", notificationGuard);
    }

    @Test
    void confirmedSendsPassEmail() {
        InternshipConfirmationResultNotification notification = notification(
                InternshipConfirmationResultNotification.CONFIRMED, null);
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
                eq("zhangsan@example.com"), subject.capture(), body.capture(),
                eq(notification.deliveryId()), eq("DeepIntern"));
        assertTrue(subject.getValue().contains("实习确认表已通过"));
        assertTrue(body.getValue().contains("测试企业"));
        assertTrue(body.getValue().contains("Java实习生"));
        assertTrue(!body.getValue().contains("拒绝原因"));
        verify(notificationGuard).finish(eq(claim), eq("SENT"), eq(null));
    }

    @Test
    void rejectedEmailCarriesRejectReason() {
        InternshipConfirmationResultNotification notification = notification(
                InternshipConfirmationResultNotification.REJECTED, "岗位名额已满");
        StudentUser student = student("张三", "zhangsan@example.com");
        when(notificationGuard.claim(anyLong(), any(), any(), any(), anyInt())).thenReturn(claim());
        when(studentUserService.findById(400L)).thenReturn(student);
        when(emailService.sendWithReceipt(any(), any(), any(), any(), any()))
                .thenReturn(new EmailService.DeliveryOutcome("SENT", "accepted", false));

        mailer.send(notification);

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendWithReceipt(
                eq("zhangsan@example.com"), subject.capture(), body.capture(), any(), any());
        assertTrue(subject.getValue().contains("实习确认表未通过"));
        assertTrue(body.getValue().contains("拒绝原因：岗位名额已满"));
    }

    @Test
    void rejectedWithoutReasonStatesMissingReason() {
        InternshipConfirmationResultNotification notification = notification(
                InternshipConfirmationResultNotification.REJECTED, "  ");
        StudentUser student = student("张三", "zhangsan@example.com");
        when(notificationGuard.claim(anyLong(), any(), any(), any(), anyInt())).thenReturn(claim());
        when(studentUserService.findById(400L)).thenReturn(student);
        when(emailService.sendWithReceipt(any(), any(), any(), any(), any()))
                .thenReturn(new EmailService.DeliveryOutcome("SENT", "accepted", false));

        mailer.send(notification);

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendWithReceipt(any(), any(), body.capture(), any(), any());
        assertTrue(body.getValue().contains("拒绝原因：企业未填写拒绝原因"));
    }

    @Test
    void missingEmailCancelsDelivery() {
        InternshipConfirmationResultNotification notification = notification(
                InternshipConfirmationResultNotification.CONFIRMED, null);
        AgentNotificationGuardService.Claim claim = claim();
        when(notificationGuard.claim(anyLong(), any(), any(), any(), anyInt())).thenReturn(claim);
        when(studentUserService.findById(400L)).thenReturn(student("张三", ""));

        mailer.send(notification);

        verify(emailService, never()).sendWithReceipt(any(), any(), any(), any(), any());
        verify(notificationGuard).finish(eq(claim), eq("CANCELLED"), eq(null));
    }

    @Test
    void duplicateGuardPreventsSecondEmail() {
        InternshipConfirmationResultNotification notification = notification(
                InternshipConfirmationResultNotification.REJECTED, "不通过");
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

    private InternshipConfirmationResultNotification notification(int status, String rejectReason) {
        return new InternshipConfirmationResultNotification(
                70L, 400L, 200L, "测试企业", 300L, "Java实习生", status, rejectReason);
    }
}
