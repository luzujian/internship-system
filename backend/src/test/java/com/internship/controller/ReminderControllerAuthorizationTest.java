package com.internship.controller;

import com.internship.entity.StudentReminder;
import com.internship.entity.TeacherUser;
import com.internship.entity.User;
import com.internship.service.ReminderService;
import com.internship.service.TeacherAutomationScopeService;
import com.internship.service.TeacherUserService;
import com.internship.utils.CurrentHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReminderControllerAuthorizationTest {
    private ReminderService reminders;
    private TeacherUserService teachers;
    private TeacherAutomationScopeService scope;
    private ReminderController controller;

    @BeforeEach
    void setUp() {
        reminders = mock(ReminderService.class);
        teachers = mock(TeacherUserService.class);
        scope = mock(TeacherAutomationScopeService.class);
        ReminderController target = new ReminderController();
        ReflectionTestUtils.setField(target, "reminderService", reminders);
        ReflectionTestUtils.setField(target, "teacherUserService", teachers);
        ReflectionTestUtils.setField(target, "teacherScope", scope);
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvisor(AuthorizationManagerBeforeMethodInterceptor.preAuthorize());
        controller = (ReminderController) proxyFactory.getProxy();
    }

    @AfterEach
    void clearIdentity() {
        CurrentHolder.remove();
        SecurityContextHolder.clearContext();
    }

    @Test
    void studentCannotSendReminderEvenWithMatchingTeacherId() {
        identity("ROLE_STUDENT", 7L);
        assertThrows(AccessDeniedException.class, () -> controller.sendReminder(20L, 7L, "内容"));
        assertThrows(AccessDeniedException.class,
                () -> controller.sendBatchReminder(new ReminderController.BatchSendRequest(List.of(20L), 7L, "内容")));
        verifyNoInteractions(reminders, teachers, scope);
    }

    @Test
    void teacherSenderComesFromPrincipalAndSingleRecipientMustBeInScope() {
        TeacherUser teacher = currentTeacher();
        when(scope.contains(teacher, 20L)).thenReturn(true);
        when(reminders.sendEmailReminder(20L, 7L, "内容"))
                .thenReturn(new ReminderService.EmailReminderResult(true, "已发送"));

        assertEquals(200, controller.sendReminder(20L, 99L, "内容").getCode());
        verify(reminders).sendEmailReminder(20L, 7L, "内容");
        assertThrows(AccessDeniedException.class, () -> controller.sendReminder(21L, 99L, "内容"));
        verify(reminders, never()).sendEmailReminder(21L, 7L, "内容");
    }

    @Test
    void batchChecksEveryRecipientBeforeAnyEmailIsSent() {
        TeacherUser teacher = currentTeacher();
        when(scope.studentIds(teacher)).thenReturn(List.of(20L));

        assertThrows(AccessDeniedException.class, () -> controller.sendBatchReminder(
                new ReminderController.BatchSendRequest(List.of(20L, 21L), 99L, "内容")));
        verify(reminders, never()).sendEmailReminderBatch(any(), any(), any());
    }

    @Test
    void inScopeBatchUsesCurrentTeacherId() {
        TeacherUser teacher = currentTeacher();
        when(scope.studentIds(teacher)).thenReturn(List.of(20L, 21L));
        when(reminders.sendEmailReminderBatch(7L, List.of(20L, 21L), "内容"))
                .thenReturn(new ReminderService.BatchEmailReminderResult(2, 2, 0, List.of()));

        assertEquals(200, controller.sendBatchReminder(
                new ReminderController.BatchSendRequest(List.of(20L, 21L), 99L, "内容")).getCode());
        verify(reminders).sendEmailReminderBatch(7L, List.of(20L, 21L), "内容");
    }

    @Test
    void studentPendingIgnoresCallerSuppliedIdAndUsesPrincipal() {
        identity("ROLE_STUDENT", 20L);
        StudentReminder reminder = new StudentReminder();
        reminder.setId(50L);
        reminder.setStudentId(20L);
        when(reminders.getPendingReminders(20L)).thenReturn(List.of(reminder));

        assertEquals(200, controller.getPendingReminders("21").getCode());
        verify(reminders).getPendingReminders(20L);
        verify(reminders, never()).getPendingReminders(21L);
    }

    @Test
    void teacherCannotReadOrConfirmStudentReminder() {
        identity("ROLE_TEACHER_COUNSELOR", 7L);
        assertThrows(AccessDeniedException.class, () -> controller.getPendingReminders("7"));
        assertThrows(AccessDeniedException.class, () -> controller.confirmReminder(50L));
        verifyNoInteractions(reminders);
    }

    private TeacherUser currentTeacher() {
        identity("ROLE_TEACHER_COUNSELOR", 7L);
        TeacherUser teacher = new TeacherUser();
        teacher.setId(7L);
        teacher.setTeacherType("COUNSELOR");
        teacher.setRole("ROLE_TEACHER_COUNSELOR");
        when(teachers.findById(7L)).thenReturn(teacher);
        return teacher;
    }

    private void identity(String role, Long id) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        CurrentHolder.setUser(user);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "test", null, List.of(new SimpleGrantedAuthority(role))));
    }
}
