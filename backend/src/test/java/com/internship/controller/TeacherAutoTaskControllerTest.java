package com.internship.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.agent.business.*;
import com.internship.entity.*;
import com.internship.service.*;
import com.internship.utils.CurrentHolder;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TeacherAutoTaskControllerTest {
    TeacherAutoTaskService tasks;
    AgentActionService actions;
    AgentIdentityService identities;
    TeacherAutoTaskController controller;
    AgentTypes.Actor actor = new AgentTypes.Actor("teacher:9", 9L, "ROLE_TEACHER_COUNSELOR", "老师", "COUNSELOR", null, null, false, Set.of("view:student_tracking"));
    @BeforeEach void setup() {
        tasks = mock(TeacherAutoTaskService.class); actions = mock(AgentActionService.class); identities = mock(AgentIdentityService.class);
        var users = mock(TeacherUserService.class);
        var user = new User(); user.setId(9L); CurrentHolder.setUser(user);
        var teacher = new TeacherUser(); teacher.setId(9L); teacher.setTeacherType("COUNSELOR");
        when(users.findById(9L)).thenReturn(teacher);
        when(identities.resolve("Bearer test")).thenReturn(actor);
        when(actions.preview(any(), anyString(), anyString(), anyMap(), nullable(String.class))).thenReturn(
                new AgentActionService.ActionView("preview", "TEACHER_TASK_ENABLE", "teacher-task:8", Map.of(), "4", "READY", 1, 2, null, null, null));
        controller = new TeacherAutoTaskController(tasks, users, new ObjectMapper(), actions, identities);
    }
    @AfterEach void cleanup() { CurrentHolder.remove(); }
    @Test void creationRequiresExplicitConfirmationBeforeBusinessMutation() {
        assertEquals("CONFIRMATION_REQUIRED", assertThrows(AgentException.class, () -> controller.create("Bearer test", Map.of("name", "提醒"))).getCode());
        verifyNoInteractions(actions);
    }
    @Test void permissionIsReResolvedInsideActionTransaction() {
        when(identities.resolve("Bearer test")).thenReturn(actor).thenThrow(AgentIdentityService.forbidden());
        when(actions.execute(any(), anyString(), anyString(), anyMap(), any())).thenAnswer(inv -> inv.<Supplier<Map<String,Object>>>getArgument(4).get());
        assertThrows(AgentException.class, () -> controller.create("Bearer test", Map.of("actionId", "action-1", "confirmed", true)));
        verify(tasks, never()).create(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }
    @Test void enabledPreviewBindsTaskVersionAndDoesNotEnable() {
        var task = new TeacherAutoTask(); task.setId(8L); task.setVersion(4);
        when(tasks.capability(any())).thenReturn(Map.of("canManage", true, "canExecute", true));
        when(tasks.get(any(), eq(8L))).thenReturn(task);
        controller.enablePreview("Bearer test", 8L);
        verify(actions).preview(eq(actor), eq("TEACHER_TASK_ENABLE"), eq("teacher-task:8"), eq(Map.of("taskId", "8", "enabled", true)), eq("4"));
        verify(tasks, never()).setEnabled(any(), any(), anyBoolean());
    }
    @Test void manualExecutionPassesStableRequestToDurableRunner() {
        controller.run("Bearer test", 8L, Map.of("clientRequestId", "request-stable-0001"));
        verify(tasks).runNow(any(), eq(8L), eq("request-stable-0001"));
        verifyNoInteractions(actions);
    }
    @Test void editRequiresVersionFromTheOpenedDraft() {
        assertEquals("VERSION_REQUIRED", assertThrows(AgentException.class, () -> controller.update(8L, Map.of("name", "old draft"))).getCode());
        controller.update(8L, Map.of("name", "draft", "version", 4));
        verify(tasks).update(any(), eq(8L), eq("draft"), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(4L), isNull());
    }

    @Test void previewBindsExplicitRecipientsAndIncompleteDraftCannotBeConfirmed() {
        when(tasks.preview(anyString(), any())).thenReturn(Map.of("triggerCondition", "SCHEDULED_REMINDER",
                "targetStudentIds", List.of(20L), "needsConfirm", true, "questions", List.of()));
        controller.preview("Bearer test", Map.of("message", "每天提醒学生s001"));
        verify(actions).preview(eq(actor), eq("TEACHER_TASK_CREATE"), anyString(),
                argThat(p -> p.get("targetStudentIds").equals(List.of(20L))), isNull());
        clearInvocations(actions);
        when(tasks.preview(anyString(), any())).thenReturn(Map.of("needsConfirm", false, "questions", List.of("学生待核对")));
        controller.preview("Bearer test", Map.of("message", "每天提醒学生s001"));
        verifyNoInteractions(actions);
    }
    @Test void unresolvedRunDoesNotMintNewEnableConfirmation() {
        var task=new TeacherAutoTask();task.setId(8L);task.setVersion(4);
        when(tasks.capability(any())).thenReturn(Map.of("canManage",true,"canExecute",true));
        when(tasks.get(any(),eq(8L))).thenReturn(task);
        when(tasks.enableImpact(any(),eq(task))).thenThrow(new AgentException("RUN_PENDING",TeacherAutoTaskRunService.UNCERTAIN_REASON,409));
        assertEquals("RUN_PENDING",assertThrows(AgentException.class,()->controller.enablePreview("Bearer test",8L)).getCode());
        verify(actions,never()).preview(any(),anyString(),anyString(),anyMap(),nullable(String.class));
    }
}
