package com.internship.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.internship.agent.business.AgentCapabilityService;
import com.internship.entity.*;
import com.internship.entity.dto.StudentTodoItemDTO;
import com.internship.mapper.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class Perm01TeacherAuthorizationTest {
    private final ObjectMapper json = new ObjectMapper();
    private final TeacherAutoTaskMapper tasks = mock(TeacherAutoTaskMapper.class);
    private final TeacherAutoTaskLogMapper logs = mock(TeacherAutoTaskLogMapper.class);
    private final TeacherAutoTaskSendLogMapper sends = mock(TeacherAutoTaskSendLogMapper.class);
    private final TeacherUserService teachers = mock(TeacherUserService.class);
    private final ClassCounselorRelationMapper relations = mock(ClassCounselorRelationMapper.class);
    private final StudentUserMapper students = mock(StudentUserMapper.class);
    private final StudentHomeTodoService todos = mock(StudentHomeTodoService.class);
    private final ReminderService reminders = mock(ReminderService.class);
    private final EmailService emails = mock(EmailService.class);
    private final TeacherAutomationScopeMapper scopeMapper = mock(TeacherAutomationScopeMapper.class);
    private final TeacherPermissionService trackingPermissions = mock(TeacherPermissionService.class);
    private final AgentNotificationGuardService guard = mock(AgentNotificationGuardService.class);
    private final TeacherAutoTaskRunService runs = mock(TeacherAutoTaskRunService.class);
    private final SystemConfigService configs = mock(SystemConfigService.class);
    private final SystemConfig main = new SystemConfig();
    private final SystemConfig override = new SystemConfig();
    private ObjectNode root;
    private TeacherUser teacher;
    private TeacherAutoTaskService service;

    @BeforeEach void setup() throws Exception {
        root = (ObjectNode) json.readTree("{\"roleDefaults\":{\"teacher\":[\"auto_task\",\"auto_task_notify\"]},"
                + "\"autoTaskPolicy\":{\"version\":1,\"roleDefaults\":{\"teacher\":{"
                + "\"actions\":[\"SEND_REMINDER\"],\"channels\":[\"STATION_REMINDER\"],"
                + "\"scopes\":[\"OWN_CLASSES\"],\"maxTargetsPerRun\":200}}}}");
        main.setConfigValue(root.toString());
        override.setConfigValue("{}");
        when(configs.findByConfigKey(AgentCapabilityService.CONFIG_KEY)).thenReturn(main);
        when(configs.findByConfigKey(AgentCapabilityService.userOverrideConfigKey("ROLE_TEACHER_COUNSELOR:9")))
                .thenReturn(override);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        service = new TeacherAutoTaskService(tasks, logs, sends, teachers, relations, students, todos,
                reminders, emails, new AgentCapabilityService(configs, json),
                mock(TeacherAutoTaskParseService.class), redis);
        teacher = new TeacherUser();
        teacher.setId(9L);
        teacher.setTeacherType("COUNSELOR");
        teacher.setStatus(1);
        when(teachers.findById(9L)).thenAnswer(call -> teacher);
        TeacherAutoTask task = new TeacherAutoTask();
        task.setId(1L);
        task.setTeacherId(9L);
        task.setTeacherType("COUNSELOR");
        task.setStatus("ACTIVE");
        task.setEnabled(1);
        task.setTriggerCondition("OFFER_PENDING");
        task.setAction("STATION_REMINDER");
        task.setMessageTemplate("本地mock通知");
        task.setFrequencyMinutes(1440);
        when(tasks.findActiveIncludingExpired()).thenReturn(List.of(task));
        when(tasks.findById(1L)).thenReturn(task);
        ClassCounselorRelation relation = new ClassCounselorRelation();
        relation.setClassId(5L);
        when(relations.findByCounselorId(9L)).thenReturn(List.of(relation));
        StudentUser first = new StudentUser();
        first.setId(20L);
        first.setName("first");
        first.setStatus(1);
        StudentUser second = new StudentUser();
        second.setId(21L);
        second.setName("second");
        second.setStatus(1);
        when(students.findByClassId(5L)).thenReturn(List.of(first, second));
        when(students.findById(20L)).thenReturn(first);
        when(students.findById(21L)).thenReturn(second);
        when(trackingPermissions.getPermissionCodesByRoleCode(anyString())).thenReturn(List.of("view:student_tracking"));
        when(scopeMapper.studentIds(any())).thenReturn(List.of(20L,21L));
        when(scopeMapper.contains(any(),anyLong())).thenReturn(1);
        when(guard.claim(anyLong(),anyString(),eq("STATION_REMINDER"),eq(1L),anyInt()))
                .thenReturn(new AgentNotificationGuardService.Claim("local-claim","local-token",true,null));
        java.util.concurrent.atomic.AtomicLong sendId = new java.util.concurrent.atomic.AtomicLong();
        when(sends.insert(any())).thenAnswer(call -> { call.<TeacherAutoTaskSendLog>getArgument(0).setId(sendId.incrementAndGet()); return 1; });
        when(sends.updateStatus(anyLong(),eq("SENT"),anyString(),any(Date.class))).thenReturn(1);
        when(runs.claim(eq(1L),anyString(),eq(0L))).thenAnswer(call -> new TeacherAutoTaskRunService.RunView(
                "local-run",1L,call.getArgument(1),0,"RUNNING",System.currentTimeMillis(),System.currentTimeMillis(),null,null,true));
        service.configureReliability(new TeacherAutomationScopeService(scopeMapper,trackingPermissions),guard,
                new AgentStationDeliveryService(reminders,sends,guard));
        service.configureRunLedger(runs);
        when(todos.getActionableTodos(anyLong())).thenReturn(List.of(new StudentTodoItemDTO(
                "todo", "OFFER_CONFIRMATION_REQUIRED", "待办", "描述", "HIGH", null, 1, null)));
    }

    @AfterEach void close() {
        service.shutdownLockMaintenance();
        verifyNoInteractions(emails);
    }

    @ParameterizedTest
    @ValueSource(strings = {"role", "user", "action", "channel", "scope"})
    void revocationBeforeRunPreventsAllNotifications(String dimension) {
        revoke(dimension);
        service.runEnabledTasks();
        verifyNoInteractions(reminders);
        verifyNoInteractions(runs,guard);
        verify(tasks).findById(1L);
        verify(tasks).updateStatus(eq(1L), eq(9L), eq("PAUSED"), eq(0), anyString(), any(Date.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"role", "user", "action", "channel", "scope", "teacherRole"})
    void revocationDuringSendingStopsRemainingRecipientsAndKeepsReason(String dimension) {
        doAnswer(call -> {
            revoke(dimension);
            return null;
        }).when(reminders).sendReminder(20L, 9L, "本地mock通知");
        service.runEnabledTasks();
        verify(reminders).sendReminder(20L, 9L, "本地mock通知");
        verify(reminders, never()).sendReminder(eq(21L), anyLong(), anyString());
        verify(tasks).updateStatus(eq(1L), eq(9L), eq("PAUSED"), eq(0), anyString(), any(Date.class));
        verify(tasks, never()).updateBlockedReason(anyLong(), isNull(), any(Date.class));
        verify(logs).insert(argThat(record -> record.getSuccessCount() == 1
                && record.getDetail().contains("授权")));
    }

    private void revoke(String dimension) {
        ObjectNode policy = (ObjectNode) root.path("autoTaskPolicy").path("roleDefaults").path("teacher");
        switch (dimension) {
            case "role" -> ((ObjectNode) root.path("roleDefaults")).putArray("teacher");
            case "user" -> override.setConfigValue("{\"auto_task_notify\":false}");
            case "action" -> policy.putArray("actions");
            case "channel" -> policy.putArray("channels");
            case "scope" -> policy.putArray("scopes");
            case "teacherRole" -> {
                TeacherUser changed = new TeacherUser();
                changed.setId(9L);
                changed.setTeacherType("COLLEGE");
                changed.setStatus(1);
                changed.setDepartmentId("1");
                teacher = changed;
            }
            default -> fail("unknown dimension");
        }
        main.setConfigValue(root.toString());
    }
}
