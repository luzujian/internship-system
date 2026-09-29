package com.internship.service;

import com.internship.entity.StudentUser;
import com.internship.entity.TeacherUser;
import com.internship.mapper.StudentReminderMapper;
import com.internship.mapper.StudentUserMapper;
import com.internship.mapper.TeacherUserMapper;
import com.internship.service.impl.ReminderServiceImpl;
import com.internship.websocket.AnnouncementWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ReminderServiceEmailTest {

    private StudentUserMapper students;
    private TeacherUserMapper teachers;
    private EmailService emails;
    private TeacherAutomationScopeService scope;
    private ReminderServiceImpl service;

    @BeforeEach void setup() {
        students = mock(StudentUserMapper.class);
        teachers = mock(TeacherUserMapper.class);
        emails = mock(EmailService.class);
        scope = mock(TeacherAutomationScopeService.class);
        service = new ReminderServiceImpl();
        ReflectionTestUtils.setField(service, "reminderMapper", mock(StudentReminderMapper.class));
        ReflectionTestUtils.setField(service, "webSocketHandler", mock(AnnouncementWebSocketHandler.class));
        ReflectionTestUtils.setField(service, "studentUserMapper", students);
        ReflectionTestUtils.setField(service, "teacherUserMapper", teachers);
        ReflectionTestUtils.setField(service, "emailService", emails);
        ReflectionTestUtils.setField(service, "teacherScope", scope);
        TeacherUser activeTeacher = new TeacherUser();
        activeTeacher.setId(49L);
        activeTeacher.setTeacherType("COUNSELOR");
        activeTeacher.setStatus(1);
        when(teachers.findById(49L)).thenReturn(activeTeacher);
        when(scope.contains(any(TeacherUser.class), anyLong())).thenReturn(true);
    }

    @Test void missingEmailIsReportedAndNoMailSent() {
        StudentUser student = student("张三", null);
        when(students.findById(8L)).thenReturn(student);

        ReminderService.EmailReminderResult result = service.sendEmailReminder(8L, 49L, "请尽快提交实习心得");

        assertFalse(result.sent());
        assertTrue(result.message().contains("未绑定邮箱"));
        verify(emails, never()).sendEmail(any(), any(), any(), any());
    }

    @Test void sendFailureIsReported() {
        StudentUser student = student("张三", "zhangsan@example.com");
        when(students.findById(8L)).thenReturn(student);
        when(emails.sendEmail(eq("zhangsan@example.com"), any(), any(), any())).thenReturn(false);

        ReminderService.EmailReminderResult result = service.sendEmailReminder(8L, 49L, "请尽快提交实习心得");

        assertFalse(result.sent());
        assertTrue(result.message().contains("发送失败"));
    }

    @Test void successSendsEmailWithSubjectAndTeacherSignature() {
        StudentUser student = student("张三", "zhangsan@example.com");
        when(students.findById(8L)).thenReturn(student);
        TeacherUser teacher = new TeacherUser();
        teacher.setId(49L);
        teacher.setName("辅导员1"); teacher.setTeacherType("COUNSELOR");
        when(teachers.findById(49L)).thenReturn(teacher);
        when(emails.sendEmail(eq("zhangsan@example.com"), any(), any(), any())).thenReturn(true);

        ReminderService.EmailReminderResult result = service.sendEmailReminder(8L, 49L, "请尽快提交实习心得");

        assertTrue(result.sent());
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> sender = ArgumentCaptor.forClass(String.class);
        verify(emails).sendEmail(eq("zhangsan@example.com"), subject.capture(), body.capture(), sender.capture());
        assertEquals("DeepIntern-辅导员-辅导员1", sender.getValue());
        assertEquals("实习事项提醒", subject.getValue());
        assertTrue(body.getValue().contains("请尽快提交实习心得"));
        assertTrue(body.getValue().contains("辅导员1"));
    }

    @Test void unknownOrDisabledStudentIsRejected() {
        when(students.findById(8L)).thenReturn(null);
        assertFalse(service.sendEmailReminder(8L, 49L, "内容").sent());

        StudentUser disabled = student("李四", "lisi@example.com");
        disabled.setStatus(0);
        when(students.findById(9L)).thenReturn(disabled);
        ReminderService.EmailReminderResult result = service.sendEmailReminder(9L, 49L, "内容");
        assertFalse(result.sent());
        assertTrue(result.message().contains("学生不存在或已停用"));
    }

    @Test void batchDeduplicatesAndReportsPerStudentFailures() {
        StudentUser ok = student("张三", "zhangsan@example.com");
        when(students.findById(8L)).thenReturn(ok);
        StudentUser noEmail = student("李四", null);
        noEmail.setId(9L);
        when(students.findById(9L)).thenReturn(noEmail);
        TeacherUser teacher = new TeacherUser();
        teacher.setId(49L);
        teacher.setName("辅导员1"); teacher.setTeacherType("COUNSELOR");
        when(teachers.findById(49L)).thenReturn(teacher);
        when(emails.sendEmail(eq("zhangsan@example.com"), any(), any(), any())).thenReturn(true);

        ReminderService.BatchEmailReminderResult result =
                service.sendEmailReminderBatch(49L, java.util.Arrays.asList(8L, 9L, 8L, null), "请尽快提交实习心得");

        assertEquals(2, result.total());
        assertEquals(1, result.success());
        assertEquals(1, result.fail());
        assertEquals(1, result.failures().size());
        ReminderService.EmailReminderFailure failure = result.failures().get(0);
        assertEquals(9L, failure.studentId());
        assertEquals("李四", failure.name());
        assertTrue(failure.reason().contains("未绑定邮箱"));
    }

    @Test void batchWithEmptyInputReturnsZero() {
        ReminderService.BatchEmailReminderResult result =
                service.sendEmailReminderBatch(49L, java.util.List.of(), "内容");
        assertEquals(0, result.total());
        assertEquals(0, result.success());
        assertEquals(0, result.fail());
        verify(emails, never()).sendEmail(any(), any(), any(), any());
    }

    @Test void batchRechecksScopeBeforeEachEmailAndHidesRevokedStudentName() {
        StudentUser first = student("张三", "first@example.com");
        StudentUser second = student("李四", "second@example.com");
        second.setId(9L);
        when(students.findById(8L)).thenReturn(first);
        when(students.findById(9L)).thenReturn(second);
        when(scope.contains(any(TeacherUser.class), eq(9L))).thenReturn(false);
        when(emails.sendEmail(eq("first@example.com"), any(), any(), any())).thenReturn(true);

        ReminderService.BatchEmailReminderResult result =
                service.sendEmailReminderBatch(49L, java.util.List.of(8L, 9L), "内容");

        assertEquals(1, result.success());
        assertEquals(1, result.fail());
        assertNull(result.failures().get(0).name());
        verify(students, never()).findById(9L);
        verify(emails, never()).sendEmail(eq("second@example.com"), any(), any(), any());
    }

    private StudentUser student(String name, String email) {
        StudentUser student = new StudentUser();
        student.setId(8L);
        student.setName(name);
        student.setEmail(email);
        student.setStatus(1);
        return student;
    }
}
