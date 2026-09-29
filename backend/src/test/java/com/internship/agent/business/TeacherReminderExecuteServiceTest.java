package com.internship.agent.business;

import com.internship.entity.StudentUser;
import com.internship.entity.TeacherUser;
import com.internship.mapper.StudentUserMapper;
import com.internship.mapper.TeacherUserMapper;
import com.internship.service.ReminderService;
import com.internship.service.TeacherAutomationScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.Supplier;
import static com.internship.agent.business.AgentTypes.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TeacherReminderExecuteServiceTest {
    final TeacherUserMapper teachers = mock(TeacherUserMapper.class);
    final StudentUserMapper students = mock(StudentUserMapper.class);
    final TeacherAutomationScopeService scopes = mock(TeacherAutomationScopeService.class);
    final ReminderService reminders = mock(ReminderService.class);
    final AgentActionService actions = mock(AgentActionService.class);
    final AgentFeatureGate featureGate = mock(AgentFeatureGate.class);
    final AgentCapabilityService capabilities = mock(AgentCapabilityService.class);

    final Actor teacher = new Actor("t1", 2L, "ROLE_TEACHER_COUNSELOR", "张老师", "COUNSELOR", null, null, false, Set.of("view:student_tracking"));
    final TeacherUser entity = new TeacherUser();
    TeacherReminderExecuteService service;

    @BeforeEach void setup() {
        entity.setId(2L); entity.setTeacherType("COUNSELOR"); entity.setStatus(1); entity.setName("张老师");
        when(teachers.findById(2L)).thenReturn(entity);
        when(capabilities.has(teacher, "auto_task_notify")).thenReturn(true);
        service = new TeacherReminderExecuteService(teachers, students, scopes, reminders, actions, featureGate, capabilities);
    }

    private StudentUser student(long id, String no, String name, String email) {
        StudentUser s = new StudentUser();
        s.setId(id); s.setStudentUserId(no); s.setUsername(no); s.setName(name); s.setClassName("计科2301"); s.setEmail(email); s.setStatus(1);
        return s;
    }

    @Test void draftResolvesStudentInsideScopeAndExposesEmailAvailability() {
        when(scopes.matchStudents(entity, "s001")).thenReturn(List.of(student(11L, "s001", "李四", "lisi@example.com")));
        ToolResult result = service.draft(teacher, "s001", "记得早点休息");
        assertEquals("OK", result.status());
        assertEquals("teacher_reminder_draft", result.tool());
        Map<String, Object> row = result.items().getFirst();
        assertEquals("11", String.valueOf(row.get("id")));
        assertEquals("s001", row.get("studentNo"));
        assertEquals("true", row.get("emailAvailable"));
        assertEquals("l***@example.com", row.get("emailMasked"));
        assertEquals("记得早点休息", row.get("content"));
    }

    @Test void explicitStationChannelIsRetainedEvenWhenStudentHasEmail() {
        when(scopes.matchStudents(entity, "s001")).thenReturn(List.of(student(11L, "s001", "学生1", "student@example.test")));
        var draft = service.draft(teacher, "s001", "请核对截止日期", "STATION_REMINDER");
        assertEquals("STATION_REMINDER", draft.items().getFirst().get("channel"));
        verifyNoInteractions(reminders);
    }

    @Test void draftReportsStudentOutsideScopeRatherThanGuessing() {
        when(scopes.matchStudents(entity, "s999")).thenReturn(List.of());
        assertEquals("OBJECT_UNAVAILABLE", assertThrows(AgentException.class, () -> service.draft(teacher, "s999", "内容")).getCode());
    }

    @Test void draftRefusesAmbiguousName() {
        when(scopes.matchStudents(entity, "李四")).thenReturn(List.of(student(11L, "s001", "李四", "a@x.com"), student(12L, "s002", "李四", "b@x.com")));
        assertEquals("AMBIGUOUS_STUDENT", assertThrows(AgentException.class, () -> service.draft(teacher, "李四", "内容")).getCode());
    }

    @Test void sendRequiresExplicitConfirmation() {
        assertEquals("CONFIRM_REQUIRED", assertThrows(AgentException.class,
                () -> service.send(teacher, new TeacherReminderExecuteService.SendRequest("aid", Map.of("studentId", "11"), "req-1", false))).getCode());
        verifyNoInteractions(reminders);
    }

    @Test void sendRequiresPreviewCard() {
        assertEquals("PREVIEW_REQUIRED", assertThrows(AgentException.class,
                () -> service.send(teacher, new TeacherReminderExecuteService.SendRequest(null, Map.of("studentId", "11"), "req-1", true))).getCode());
        verifyNoInteractions(reminders);
    }

    @Test void disabledFeatureSwitchStopsSendBeforeActionClaim() {
        doThrow(new AgentException("AI_DISABLED", "AI 已关闭", 503)).when(featureGate).requireEnabled();
        AgentException failure = assertThrows(AgentException.class, () -> service.send(teacher,
                new TeacherReminderExecuteService.SendRequest("aid", Map.of("studentId", "11"), "req-1", true)));
        assertEquals("AI_DISABLED", failure.getCode());
        verifyNoInteractions(actions, reminders);
    }

    @Test void disabledNotificationCapabilityStopsSendBeforeActionClaim() {
        when(capabilities.has(teacher, "auto_task_notify")).thenReturn(false);
        AgentException failure = assertThrows(AgentException.class, () -> service.send(teacher,
                new TeacherReminderExecuteService.SendRequest("aid", Map.of("studentId", "11"), "req-1", true)));
        assertEquals("CAPABILITY_DISABLED", failure.getCode());
        verifyNoInteractions(actions, reminders);
    }

    @Test void capabilityRevokedAfterConfirmationStopsDelivery() {
        when(capabilities.has(teacher, "auto_task_notify")).thenReturn(true, false);
        when(actions.executeExternal(any(), eq("aid"), eq(TeacherReminderExecuteService.ACTION), any(), any()))
                .thenAnswer(invocation -> ((Supplier<Map<String, Object>>) invocation.getArgument(4)).get());
        AgentException failure = assertThrows(AgentException.class, () -> service.send(teacher,
                new TeacherReminderExecuteService.SendRequest("aid", Map.of("studentId", "11", "channel", "EMAIL", "content", "内容"), "req-1", true)));
        assertEquals("CAPABILITY_DISABLED", failure.getCode());
        verifyNoInteractions(reminders);
    }

    @Test void featureSwitchDisabledAfterConfirmationStopsDelivery() {
        doNothing().doThrow(new AgentException("AI_DISABLED", "AI 已关闭", 503))
                .when(featureGate).requireEnabled();
        when(actions.executeExternal(any(), eq("aid"), eq(TeacherReminderExecuteService.ACTION), any(), any()))
                .thenAnswer(invocation -> ((Supplier<Map<String, Object>>) invocation.getArgument(4)).get());
        AgentException failure = assertThrows(AgentException.class, () -> service.send(teacher,
                new TeacherReminderExecuteService.SendRequest("aid", Map.of("studentId", "11", "channel", "EMAIL", "content", "内容"), "req-1", true)));
        assertEquals("AI_DISABLED", failure.getCode());
        verifyNoInteractions(reminders);
    }

    @Test void sendReChecksScopeBeforeDelivering() {
        when(scopes.contains(entity, 11L)).thenReturn(false);
        when(actions.executeExternal(any(), eq("aid"), eq(TeacherReminderExecuteService.ACTION), any(), any()))
                .thenAnswer(invocation -> ((Supplier<Map<String, Object>>) invocation.getArgument(4)).get());
        AgentException failure = assertThrows(AgentException.class, () -> service.send(teacher,
                new TeacherReminderExecuteService.SendRequest("aid", Map.of("studentId", "11", "channel", "EMAIL", "content", "记得早点休息"), "req-1", true)));
        assertEquals("SCOPE_CHANGED", failure.getCode());
        verifyNoInteractions(reminders);
    }

    @Test void sendDeliversEmailAndReturnsReceipt() {
        when(scopes.contains(entity, 11L)).thenReturn(true);
        when(reminders.sendEmailReminder(11L, 2L, "记得早点休息")).thenReturn(new ReminderService.EmailReminderResult(true, "邮件提醒已发送"));
        when(actions.executeExternal(any(), eq("aid"), eq(TeacherReminderExecuteService.ACTION), any(), any()))
                .thenAnswer(invocation -> ((Supplier<Map<String, Object>>) invocation.getArgument(4)).get());
        Map<String, Object> receipt = service.send(teacher,
                new TeacherReminderExecuteService.SendRequest("aid", Map.of("studentId", "11", "channel", "EMAIL", "content", "记得早点休息"), "req-1", true));
        assertEquals("11", receipt.get("studentId"));
        assertEquals("EMAIL", receipt.get("channel"));
        assertEquals("邮件提醒已发送", receipt.get("message"));
        verify(reminders).sendEmailReminder(11L, 2L, "记得早点休息");
    }

    @Test void sendUsesStationReminderWithoutEmail() {
        when(scopes.contains(entity, 11L)).thenReturn(true);
        when(actions.executeExternal(any(), eq("aid"), eq(TeacherReminderExecuteService.ACTION), any(), any()))
                .thenAnswer(invocation -> ((Supplier<Map<String, Object>>) invocation.getArgument(4)).get());
        Map<String, Object> receipt = service.send(teacher,
                new TeacherReminderExecuteService.SendRequest("aid", Map.of("studentId", "11", "channel", "STATION_REMINDER", "content", "记得早点休息"), "req-1", true));
        assertEquals("STATION_REMINDER", receipt.get("channel"));
        assertEquals("站内提醒已发送", receipt.get("message"));
        verify(reminders).sendReminder(11L, 2L, "记得早点休息");
        verify(reminders, never()).sendEmailReminder(anyLong(), anyLong(), anyString());
    }

    @Test void nonTeacherCannotDraft() {
        Actor studentActor = new Actor("s1", 11L, "ROLE_STUDENT", "学生", null, null, null, false, Set.of());
        assertThrows(AgentException.class, () -> service.draft(studentActor, "s001", "内容"));
    }
}
