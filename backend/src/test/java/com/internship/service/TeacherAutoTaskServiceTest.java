package com.internship.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.agent.business.AgentCapabilityService;
import com.internship.agent.business.AgentCapabilityService.AutomationPolicy;
import com.internship.agent.business.AgentFeatureGate;
import com.internship.entity.ClassCounselorRelation;
import com.internship.entity.StudentUser;
import com.internship.entity.TeacherAutoTask;
import com.internship.entity.TeacherAutoTaskSendLog;
import com.internship.entity.TeacherUser;
import com.internship.entity.dto.StudentTodoItemDTO;
import com.internship.exception.BusinessException;
import com.internship.mapper.ClassCounselorRelationMapper;
import com.internship.mapper.StudentUserMapper;
import com.internship.mapper.TeacherAutoTaskLogMapper;
import com.internship.mapper.TeacherAutoTaskMapper;
import com.internship.mapper.TeacherAutoTaskSendLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TeacherAutoTaskServiceTest {
    @Test void historicalCustomReflectionRemindersArePausedWithoutSending() {
        var task = dueScheduledTask("STATION_REMINDER");
        task.setMessageTemplate("请提交本周心得");
        org.springframework.test.util.ReflectionTestUtils.setField(service, "reflectionFeature", mock(ReflectionFeatureService.class));
        service.runEnabledTasks();
        verifyNoInteractions(reminders, guard);
        verify(tasks).updateStatus(eq(1L), eq(9L), eq("PAUSED"), eq(0), contains("已停用"), any());
    }
    @Test void disabledReflectionCannotBeReintroducedByAnAiDraft() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "reflectionFeature", mock(ReflectionFeatureService.class));
        when(parser.parse("定期催交心得")).thenReturn(Map.of("triggerCondition", TeacherAutoTaskService.TRIGGER_REFLECTION));
        BusinessException failure = assertThrows(BusinessException.class, () -> service.preview("定期催交心得", counselor));
        assertTrue(failure.getMessage().contains("已停用"));
        verifyNoInteractions(reminders);
        verify(tasks, never()).insert(any());
    }
    private TeacherAutoTaskMapper tasks;
    private TeacherAutoTaskLogMapper logs;
    private TeacherAutoTaskSendLogMapper sends;
    private TeacherUserService teacherUsers;
    private ClassCounselorRelationMapper relations;
    private StudentUserMapper students;
    private StudentHomeTodoService todos;
    private ReminderService reminders;
    private EmailService emails;
    private AgentCapabilityService capabilities;
    private TeacherAutoTaskParseService parser;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> redisValues;
    private TeacherAutoTaskService service;
    private TeacherUser counselor;
    private TeacherAutomationScopeService scopes;
    private AgentNotificationGuardService guard;
    private AgentStationDeliveryService station;
    private TeacherAutoTaskRunService runs;

    @BeforeEach void setup() {
        tasks = mock(TeacherAutoTaskMapper.class);
        logs = mock(TeacherAutoTaskLogMapper.class);
        sends = mock(TeacherAutoTaskSendLogMapper.class);
        teacherUsers = mock(TeacherUserService.class);
        relations = mock(ClassCounselorRelationMapper.class);
        students = mock(StudentUserMapper.class);
        todos = mock(StudentHomeTodoService.class);
        reminders = mock(ReminderService.class);
        emails = mock(EmailService.class);
        capabilities = mock(AgentCapabilityService.class);
        parser = mock(TeacherAutoTaskParseService.class);
        redis = mock(StringRedisTemplate.class);
        redisValues = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(redisValues);
        when(redisValues.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(capabilities.effectiveAutomationPolicy(any())).thenReturn(fullPolicy());
        service = new TeacherAutoTaskService(tasks, logs, sends, teacherUsers, relations,
                students, todos, reminders, emails, capabilities, parser, redis);
        com.internship.support.ReflectionFeatureTestSupport.enable(service);
        counselor = new TeacherUser();
        counselor.setId(9L);
        counselor.setTeacherType("COUNSELOR");
        counselor.setName("辅导员");
        scopes = mock(TeacherAutomationScopeService.class);
        guard = mock(AgentNotificationGuardService.class);
        station = mock(AgentStationDeliveryService.class);
        when(scopes.label(any())).thenReturn("本人负责班级");
        when(scopes.contains(any(), anyLong())).thenReturn(true);
        when(scopes.studentIds(any())).thenAnswer(inv -> relations.findByCounselorId(9L).stream()
                .flatMap(r -> students.findByClassId(r.getClassId()).stream()).map(StudentUser::getId).distinct().toList());
        when(students.findById(anyLong())).thenAnswer(inv -> relations.findByCounselorId(9L).stream()
                .flatMap(r -> students.findByClassId(r.getClassId()).stream())
                .filter(s -> s.getId().equals(inv.getArgument(0))).findFirst().orElse(null));
        when(tasks.findById(anyLong())).thenAnswer(inv -> tasks.findActiveIncludingExpired().stream()
                .filter(t -> t.getId().equals(inv.getArgument(0))).findFirst().orElse(null));
        when(tasks.updateByIdAndTeacher(any())).thenReturn(1);
        when(guard.claim(anyLong(), anyString(), anyString(), anyLong(), anyInt()))
                .thenReturn(new AgentNotificationGuardService.Claim("k", "t", true, null));
        doAnswer(inv -> { reminders.sendReminder(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)); return null; })
                .when(station).send(anyLong(), anyLong(), anyString(), nullable(Long.class), any());
        service.configureReliability(scopes, guard, station);
        runs = mock(TeacherAutoTaskRunService.class);
        when(runs.claim(anyLong(), anyString(), anyLong())).thenAnswer(inv -> new TeacherAutoTaskRunService.RunView(
                "run-test", inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), "RUNNING", 1, 1, null, null, true));
        service.configureRunLedger(runs);
    }

    private AutomationPolicy fullPolicy() {
        return new AutomationPolicy(true, true, true,
                Set.of("SEND_REMINDER"), Set.of("STATION_REMINDER", "EMAIL"),
                Set.of("OWN_CLASSES"), 200);
    }

    @Test void previewAddsLabelsAndChannelAvailability() {
        when(teacherUsers.findById(9L)).thenReturn(counselor);
        when(parser.parse("提醒鉴定没交的同学")).thenReturn(Map.of(
                "triggerCondition", "APPRAISAL_MISSING",
                "triggerDays", 2,
                "action", "EMAIL",
                "frequencyMinutes", 1440,
                "questions", List.of()));
        when(emails.isConfigured()).thenReturn(true);
        Map<String, Object> preview = service.preview("提醒鉴定没交的同学", counselor);
        assertEquals("APPRAISAL_MISSING", preview.get("triggerCondition"));
        assertEquals("鉴定缺交", preview.get("triggerLabel"));
        assertEquals("邮件提醒", preview.get("actionLabel"));
        assertEquals("每天", preview.get("frequencyLabel"));
        assertEquals(true, preview.get("channelAvailable"));
    }

    @Test void createValidatesNewFieldsAndEmailChannel() {
        when(capabilities.has(any(), eq("auto_task"))).thenReturn(true);
        when(capabilities.has(any(), eq("auto_task_notify"))).thenReturn(true);
        when(emails.isConfigured()).thenReturn(false);
        assertThrows(BusinessException.class, () -> service.create(counselor, "t",
                "APPRAISAL_MISSING", 2, "EMAIL", "记得交", 1440, null));
        when(emails.isConfigured()).thenReturn(true);
        assertThrows(BusinessException.class, () -> service.create(counselor, "t",
                "APPRAISAL_MISSING", 99, "STATION_REMINDER", "记得交", 1440, null));
        assertThrows(BusinessException.class, () -> service.create(counselor, "t",
                "APPRAISAL_MISSING", 2, "STATION_REMINDER", "记得交", 30, null));
        TeacherAutoTask task = service.create(counselor, "t",
                "APPRAISAL_MISSING", 2, "STATION_REMINDER", "记得交", 1440, null);
        assertEquals("PAUSED", task.getStatus());
        assertEquals(2, task.getTriggerDays());
        assertEquals(1440, task.getFrequencyMinutes());
        verify(tasks).insert(task);
    }

    @Test void updateRejectsEndedAndEndIsTerminal() {
        TeacherAutoTask task = task(1L, "ACTIVE", "APPRAISAL_MISSING", 2, "STATION_REMINDER");
        task.setStatus("ENDED");
        doReturn(task).when(tasks).findById(1L);
        assertThrows(BusinessException.class, () -> service.update(counselor, 1L, "n",
                "APPRAISAL_MISSING", 3, "STATION_REMINDER", "m", 1440, null));

        TeacherAutoTask active = task(2L, "ACTIVE", "APPRAISAL_MISSING", 2, "STATION_REMINDER");
        doReturn(active).when(tasks).findById(2L);
        TeacherAutoTask ended = service.end(counselor, 2L);
        assertEquals("ENDED", ended.getStatus());
        verify(tasks).updateStatus(2L, 9L, "ENDED", 0, null, active.getUpdateTime());
    }

    @Test void deletePreservesNotificationAndRunReceipts() {
        TeacherAutoTask task = task(1L, "PAUSED", "APPRAISAL_MISSING", 2, "STATION_REMINDER");
        doReturn(task).when(tasks).findById(1L);

        service.delete(counselor, 1L);

        verify(sends, never()).deleteByTaskId(anyLong());
        verify(logs, never()).deleteByTaskId(anyLong());
        verify(tasks).deleteByIdAndTeacher(1L, 9L);
    }

    @Test void runSendsWhenDueAndWithinWindow() {
        TeacherAutoTask task = task(1L, "ACTIVE", "APPRAISAL_MISSING", 2, "STATION_REMINDER");
        StudentUser student = student(20L, "张三");
        when(tasks.findActiveIncludingExpired()).thenReturn(List.of(task));
        when(teacherUsers.findById(9L)).thenReturn(counselor);
        when(capabilities.has(any(), eq("auto_task"))).thenReturn(true);
        ClassCounselorRelation relation = new ClassCounselorRelation();
        relation.setClassId(5L);
        when(relations.findByCounselorId(9L)).thenReturn(List.of(relation));
        when(students.findByClassId(5L)).thenReturn(List.of(student));
        when(todos.getActionableTodos(20L)).thenReturn(List.of(todo(
                "APPRAISAL_SUBMISSION_REQUIRED", OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).plusDays(1))));
        when(sends.findLastSuccess(1L, 20L, "STATION_REMINDER")).thenReturn(null);

        service.runEnabledTasks();

        verify(reminders).sendReminder(20L, 9L, "请交材料");
        verify(sends).insert(argThat(record -> "PENDING".equals(record.getStatus())));
        verify(tasks).updateLastRun(eq(1L), any(Date.class));
    }

    @Test void emailWithoutAddressRecordsOnlyFailedState() {
        TeacherAutoTask task = task(1L, "ACTIVE", "OFFER_PENDING", null, "EMAIL");
        StudentUser student = student(20L, "张三");
        when(tasks.findActiveIncludingExpired()).thenReturn(List.of(task));
        when(teacherUsers.findById(9L)).thenReturn(counselor);
        ClassCounselorRelation relation = new ClassCounselorRelation();
        relation.setClassId(5L);
        when(relations.findByCounselorId(9L)).thenReturn(List.of(relation));
        when(students.findByClassId(5L)).thenReturn(List.of(student));
        when(todos.getActionableTodos(20L)).thenReturn(List.of(todo("OFFER_CONFIRMATION_REQUIRED", null)));
        when(sends.findLastSuccess(1L, 20L, "EMAIL")).thenReturn(null);

        service.runEnabledTasks();

        ArgumentCaptor<TeacherAutoTaskSendLog> captor = ArgumentCaptor.forClass(TeacherAutoTaskSendLog.class);
        verify(sends).insert(captor.capture());
        assertEquals("FAILED", captor.getValue().getStatus());
        assertEquals("学生未绑定邮箱。", captor.getValue().getDetail());
        verify(sends, never()).updateStatus(anyLong(), anyString(), anyString(), any(Date.class));
        verify(emails, never()).sendEmail(anyString(), anyString(), anyString(), anyString());
    }

    @Test void runSkipsOutsideWindowAndInsideCooldown() {
        TeacherAutoTask task = task(1L, "ACTIVE", "APPRAISAL_MISSING", 2, "STATION_REMINDER");
        StudentUser student = student(20L, "张三");
        when(tasks.findActiveIncludingExpired()).thenReturn(List.of(task));
        when(teacherUsers.findById(9L)).thenReturn(counselor);
        when(capabilities.has(any(), eq("auto_task"))).thenReturn(true);
        ClassCounselorRelation relation = new ClassCounselorRelation();
        relation.setClassId(5L);
        when(relations.findByCounselorId(9L)).thenReturn(List.of(relation));
        when(students.findByClassId(5L)).thenReturn(List.of(student));
        when(todos.getActionableTodos(20L)).thenReturn(List.of(todo(
                "APPRAISAL_SUBMISSION_REQUIRED", OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).plusDays(10))));
        service.runEnabledTasks();
        verify(reminders, never()).sendReminder(anyLong(), anyLong(), anyString());

        when(todos.getActionableTodos(20L)).thenReturn(List.of(todo(
                "APPRAISAL_SUBMISSION_REQUIRED", OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).plusDays(1))));
        when(sends.findLastSuccess(1L, 20L, "STATION_REMINDER"))
                .thenReturn(new Date(System.currentTimeMillis() - 60_000L));
        service.runEnabledTasks();
        verify(reminders, never()).sendReminder(anyLong(), anyLong(), anyString());
        verify(sends).insert(argThat(record -> "SKIPPED_COOLDOWN".equals(record.getStatus())));
    }

    @Test void capabilityRevocationWritesBlockedReasonAndLog() {
        TeacherAutoTask task = task(1L, "ACTIVE", "OFFER_PENDING", null, "STATION_REMINDER");
        when(tasks.findActiveIncludingExpired()).thenReturn(List.of(task));
        when(teacherUsers.findById(9L)).thenReturn(counselor);
        when(capabilities.has(any(), eq("auto_task"))).thenReturn(false);
        when(capabilities.effectiveAutomationPolicy(any())).thenReturn(AutomationPolicy.closed(false));
        service.runEnabledTasks();
        verify(tasks).updateStatus(eq(1L), eq(9L), eq("PAUSED"), eq(0),
                eq("AI 能力 auto_task 已被管理员关闭"), any(Date.class));
        verify(logs).insert(argThat(record -> record.getDetail().contains("auto_task")));
        verify(reminders, never()).sendReminder(anyLong(), anyLong(), anyString());
    }

    @Test void stalePendingSendBecomesUnknownAndDoesNotResend() {
        TeacherAutoTask task = task(1L, "ACTIVE", "OFFER_PENDING", null, "STATION_REMINDER");
        StudentUser student = student(20L, "张三");
        TeacherAutoTaskSendLog pending = new TeacherAutoTaskSendLog();
        pending.setId(99L);
        pending.setSentAt(new Date(System.currentTimeMillis() - 16 * 60_000L));
        when(tasks.findActiveIncludingExpired()).thenReturn(List.of(task));
        when(teacherUsers.findById(9L)).thenReturn(counselor);
        ClassCounselorRelation relation = new ClassCounselorRelation();
        relation.setClassId(5L);
        when(relations.findByCounselorId(9L)).thenReturn(List.of(relation));
        when(students.findByClassId(5L)).thenReturn(List.of(student));
        when(todos.getActionableTodos(20L)).thenReturn(List.of(todo("OFFER_CONFIRMATION_REQUIRED", null)));
        when(sends.findPending(1L, 20L, "STATION_REMINDER")).thenReturn(pending);

        service.runEnabledTasks();

        verify(sends).updateStatus(eq(99L), eq("UNKNOWN"), anyString(), any(Date.class));
        verify(reminders, never()).sendReminder(anyLong(), anyLong(), anyString());
    }

    @Test void successfulRunClearsBlockedReason() {
        TeacherAutoTask task = task(1L, "ACTIVE", "OFFER_PENDING", null, "STATION_REMINDER");
        task.setBlockedReason("旧原因");
        StudentUser student = student(20L, "张三");
        when(tasks.findActiveIncludingExpired()).thenReturn(List.of(task));
        when(teacherUsers.findById(9L)).thenReturn(counselor);
        when(capabilities.has(any(), eq("auto_task"))).thenReturn(true);
        ClassCounselorRelation relation = new ClassCounselorRelation();
        relation.setClassId(5L);
        when(relations.findByCounselorId(9L)).thenReturn(List.of(relation));
        when(students.findByClassId(5L)).thenReturn(List.of(student));
        when(todos.getActionableTodos(20L)).thenReturn(List.of(todo(
                "OFFER_CONFIRMATION_REQUIRED", null)));
        when(sends.findLastSuccess(1L, 20L, "STATION_REMINDER")).thenReturn(null);
        service.runEnabledTasks();
        verify(tasks).updateBlockedReason(eq(1L), isNull(), any(Date.class));
    }

    @Test void createRejectsTeacherWithoutAuthorizedScope() {
        TeacherUser college = new TeacherUser();
        college.setId(10L);
        college.setTeacherType("COLLEGE");
        doThrow(new BusinessException("教师负责范围未配置")).when(scopes).requireAuthorization(college);
        when(capabilities.has(any(), eq("auto_task"))).thenReturn(true);
        assertThrows(BusinessException.class, () -> service.create(college, "t",
                "REFLECTION_NOT_SUBMITTED", 2, "STATION_REMINDER", "记得交", 1440, null));
    }

    @Test void staleDraftCannotOverwriteNewVersionAndActiveTaskMustPauseBeforeEditing() {
        TeacherAutoTask task = task(8L, "PAUSED", "OFFER_PENDING", null, "STATION_REMINDER"); task.setVersion(5);
        doReturn(task).when(tasks).findById(8L);
        assertThrows(BusinessException.class, () -> service.update(counselor,8L,"old","OFFER_PENDING",null,"STATION_REMINDER","m",1440,null,null,4L));
        task.setStatus("ACTIVE"); task.setEnabled(1);
        assertThrows(BusinessException.class, () -> service.update(counselor,8L,"new","OFFER_PENDING",null,"STATION_REMINDER","m",1440,null,null,5L));
        verify(tasks, never()).updateByIdAndTeacher(any());
    }
    @Test void unresolvedRunPreventsBothEnablePreviewAndEnableMutation() {
        TeacherAutoTask task=task(8L,"PAUSED","OFFER_PENDING",null,"STATION_REMINDER");
        task.setBlockedReason(TeacherAutoTaskRunService.UNCERTAIN_REASON);
        doReturn(task).when(tasks).findById(8L);
        doThrow(new com.internship.agent.business.AgentException("RUN_PENDING",TeacherAutoTaskRunService.UNCERTAIN_REASON,409)).when(runs).requireResolved(8L);
        assertThrows(com.internship.agent.business.AgentException.class,()->service.enableImpact(counselor,task));
        assertThrows(com.internship.agent.business.AgentException.class,()->service.setEnabled(counselor,8L,true));
        verify(runs).reconcilePending(8L);
        verify(tasks,never()).updateStatus(anyLong(),anyLong(),anyString(),anyInt(),nullable(String.class),any());
        assertEquals("PAUSED",task.getStatus());
        assertEquals(TeacherAutoTaskRunService.UNCERTAIN_REASON,task.getBlockedReason());
    }

    @Test void authorizedDepartmentTeacherCreatesTaskForOwnDivision() {
        TeacherUser teacher = new TeacherUser(); teacher.setId(11L); teacher.setTeacherType("DEPARTMENT");
        teacher.setStatus(1); teacher.setDivisionId("3");
        when(capabilities.effectiveAutomationPolicy(any())).thenReturn(new AutomationPolicy(true, true, true,
                Set.of("SEND_REMINDER"), Set.of("STATION_REMINDER"), Set.of("OWN_DIVISION"), 200));
        var result = service.create(teacher, "本系提醒", "OFFER_PENDING", null, "STATION_REMINDER", "请核对", 1440, null);
        assertEquals("OWN_DIVISION", result.getTargetScope());
        assertEquals("PAUSED", result.getStatus());
    }

    @Test void fixedOnceUsesConfiguredClockAndNeverRepeatsSameOccurrence() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        TeacherAutoTask task = task(1L, "ACTIVE", "OFFER_PENDING", null, "STATION_REMINDER");
        task.setScheduleConfig("{\"mode\":\"ONCE\",\"date\":\"" + now.toLocalDate()
                + "\",\"time\":\"" + now.format(DateTimeFormatter.ofPattern("HH:mm")) + "\"}");
        StudentUser student = student(20L, "张三");
        when(tasks.findActiveIncludingExpired()).thenReturn(List.of(task));
        when(teacherUsers.findById(9L)).thenReturn(counselor);
        ClassCounselorRelation relation = new ClassCounselorRelation();
        relation.setClassId(5L);
        when(relations.findByCounselorId(9L)).thenReturn(List.of(relation));
        when(students.findByClassId(5L)).thenReturn(List.of(student));
        when(todos.getActionableTodos(20L)).thenReturn(List.of(todo("OFFER_CONFIRMATION_REQUIRED", null)));
        when(sends.findLastSuccess(1L, 20L, "STATION_REMINDER")).thenReturn(null);

        service.runEnabledTasks();
        verify(reminders).sendReminder(20L, 9L, "请交材料");
        task.setLastRunAt(new Date());
        service.runEnabledTasks();
        verify(reminders, times(1)).sendReminder(anyLong(), anyLong(), anyString());
    }

    @Test void globalAiSwitchBlocksExecutionWithoutPausingTask() {
        TeacherAutoTask task = task(1L, "ACTIVE", "OFFER_PENDING", null, "STATION_REMINDER");
        when(tasks.findActiveIncludingExpired()).thenReturn(List.of(task));
        TeacherAutoTaskService disabled = new TeacherAutoTaskService(tasks, logs, sends, teacherUsers,
                relations, students, todos, reminders, emails, capabilities, parser, redis,
                new AgentFeatureGate(null, "false"), new ObjectMapper(), "Asia/Shanghai", 60, false, "");
        disabled.runEnabledTasks();
        verify(tasks).updateBlockedReason(eq(1L), contains("AI总开关"), any(Date.class));
        verify(reminders, never()).sendReminder(anyLong(), anyLong(), anyString());
        verify(tasks, never()).updateStatus(anyLong(), anyLong(), eq("PAUSED"), anyInt(), anyString(), any(Date.class));
    }

    @Test void testModeBlocksNonAllowlistedEmailRecipient() {
        TeacherAutoTask task = task(1L, "ACTIVE", "OFFER_PENDING", null, "EMAIL");
        StudentUser student = student(20L, "张三");
        student.setEmail("real-student@example.com");
        when(tasks.findActiveIncludingExpired()).thenReturn(List.of(task));
        when(teacherUsers.findById(9L)).thenReturn(counselor);
        ClassCounselorRelation relation = new ClassCounselorRelation();
        relation.setClassId(5L);
        when(relations.findByCounselorId(9L)).thenReturn(List.of(relation));
        when(students.findByClassId(5L)).thenReturn(List.of(student));
        when(todos.getActionableTodos(20L)).thenReturn(List.of(todo("OFFER_CONFIRMATION_REQUIRED", null)));
        when(sends.findLastSuccess(1L, 20L, "EMAIL")).thenReturn(null);
        TeacherAutoTaskService guarded = new TeacherAutoTaskService(tasks, logs, sends, teacherUsers,
                relations, students, todos, reminders, emails, capabilities, parser, redis,
                new AgentFeatureGate(null, "true"), new ObjectMapper(), "Asia/Shanghai", 60, true,
                "allowed@example.com");
        guarded.configureReliability(scopes, guard, station);
        guarded.configureRunLedger(runs);
        guarded.runEnabledTasks();
        verify(emails, never()).sendEmail(anyString(), anyString(), anyString(), anyString());
        verify(sends).insert(argThat(record -> "FAILED".equals(record.getStatus())
                && record.getDetail().contains("白名单")));
    }

    private StudentUser scopedStudent() {
        StudentUser student = student(20L, "张三"); student.setUsername("s001"); student.setEmail("student@example.test");
        ClassCounselorRelation relation = new ClassCounselorRelation(); relation.setClassId(5L);
        when(relations.findByCounselorId(9L)).thenReturn(List.of(relation));
        when(students.findByClassId(5L)).thenReturn(List.of(student, student(21L, "李四")));
        when(teacherUsers.findById(9L)).thenReturn(counselor);
        return student;
    }

    private Map<String, Object> scheduledDraft() {
        return Map.of("triggerCondition", "SCHEDULED_REMINDER", "action", "EMAIL", "frequencyMinutes", 1440,
                "scheduleConfig", "{\"mode\":\"DAILY\",\"time\":\"14:42\"}", "targetQueries", List.of("s001"),
                "targetMode", "SELECTED", "messageTemplate", "请返岗", "questions", List.of());
    }

    @Test void scheduledPreviewResolvesOnlyTheRequestedStudentWithoutQueryingBusinessTodos() {
        var student = scopedStudent();
        when(scopes.matchStudents(counselor, "s001")).thenReturn(List.of(student));
        when(parser.parse(anyString())).thenReturn(scheduledDraft());
        when(emails.isConfigured()).thenReturn(true);
        var preview = service.preview("每天14:42给学生s001发邮件请返岗", counselor);
        assertEquals(List.of(20L), preview.get("targetStudentIds"));
        assertEquals("张三 · s001", preview.get("targetLabel"));
        assertEquals(1, preview.get("targetCount"));
        assertEquals("定时提醒", preview.get("triggerLabel"));
        assertEquals(true, preview.get("needsConfirm"));
        verifyNoInteractions(todos, reminders);
        verify(tasks, never()).insert(any());
    }

    @Test void unknownOrAmbiguousStudentNeverBecomesTheFullScope() {
        scopedStudent();
        when(parser.parse(anyString())).thenReturn(scheduledDraft());
        when(emails.isConfigured()).thenReturn(true);
        var unknown = service.preview("学生s001", counselor);
        assertEquals(false, unknown.get("needsConfirm"));
        assertNull(unknown.get("targetCount"));
        assertTrue(unknown.get("questions").toString().contains("负责范围内未找到"));
        StudentUser one = student(20L, "重名"); one.setUsername("one");
        StudentUser two = student(21L, "重名"); two.setUsername("two");
        when(scopes.matchStudents(counselor, "s001")).thenReturn(List.of(one, two));
        var ambiguous = service.preview("学生s001", counselor);
        assertEquals(false, ambiguous.get("needsConfirm"));
        assertNull(ambiguous.get("targetCount"));
        assertTrue(ambiguous.get("questions").toString().contains("多名学生"));
    }

    @Test void missingMailChannelBlocksConfirmationButKeepsCorrectScheduleAndTarget() {
        var student = scopedStudent();
        when(scopes.matchStudents(counselor, "s001")).thenReturn(List.of(student));
        when(parser.parse(anyString())).thenReturn(scheduledDraft());
        var preview = service.preview("学生s001", counselor);
        assertEquals(false, preview.get("needsConfirm"));
        assertTrue(preview.get("questions").toString().contains("邮件通道"));
        assertEquals("SCHEDULED_REMINDER", preview.get("triggerCondition"));
        assertEquals(1, preview.get("targetCount"));
    }

    @Test void scheduledCreationRequiresConcreteTimeAndRechecksStudentScope() {
        scopedStudent();
        assertThrows(BusinessException.class, () -> service.create(counselor, "返岗", "SCHEDULED_REMINDER",
                null, "STATION_REMINDER", "请返岗", 1440, null, null, List.of(20L)));
        String schedule = "{\"mode\":\"DAILY\",\"time\":\"14:42\"}";
        when(scopes.contains(counselor, 20L)).thenReturn(false);
        assertThrows(BusinessException.class, () -> service.create(counselor, "返岗", "SCHEDULED_REMINDER",
                null, "STATION_REMINDER", "请返岗", 1440, null, schedule, List.of(20L)));
        verify(tasks, never()).insert(any());
        when(scopes.contains(counselor, 20L)).thenReturn(true);
        var created = service.create(counselor, "返岗", "SCHEDULED_REMINDER", 2, "STATION_REMINDER", "请返岗",
                1440, null, schedule, List.of(20L));
        assertNull(created.getTriggerDays());
        assertEquals("PAUSED", created.getStatus());
        assertEquals(List.of(20L), TeacherAutoTaskTargets.decode(created.getTargetScope()));
        verifyNoInteractions(reminders);
    }

    private TeacherAutoTask dueScheduledTask(String action) {
        scopedStudent();
        var task = task(1L, "ACTIVE", "SCHEDULED_REMINDER", null, action);
        task.setMessageTemplate("请返岗");
        task.setTargetScope(TeacherAutoTaskTargets.encode("OWN_CLASSES", List.of(20L)));
        var now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        task.setScheduleConfig("{\"mode\":\"ONCE\",\"date\":\"" + now.toLocalDate() + "\",\"time\":\""
                + now.format(DateTimeFormatter.ofPattern("HH:mm")) + "\"}");
        when(tasks.findActiveIncludingExpired()).thenReturn(List.of(task));
        return task;
    }

    @Test void scheduledRunDeliversOnlyToSelectedStudentEvenWithoutAnyBusinessTodo() {
        dueScheduledTask("STATION_REMINDER");
        service.runEnabledTasks();
        verify(reminders).sendReminder(20L, 9L, "请返岗");
        verify(reminders, never()).sendReminder(eq(21L), anyLong(), anyString());
        verify(guard).claim(eq(20L), eq(TeacherAutoTaskService.scheduledIdentity("请返岗")), eq("STATION_REMINDER"), eq(1L), eq(1440));
        verifyNoInteractions(todos);
    }

    @Test void scheduledRunDoesNotSendBeforeTheSpecifiedTime() {
        var task = dueScheduledTask("STATION_REMINDER");
        var tomorrow = LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusDays(1);
        task.setScheduleConfig("{\"mode\":\"ONCE\",\"date\":\"" + tomorrow.toLocalDate() + "\",\"time\":\"14:42\"}");
        service.runEnabledTasks();
        verifyNoInteractions(reminders, guard);
    }

    @Test void manualOnceWaitsForExplicitRunAndEndsAfterExecution() {
        var task = dueScheduledTask("STATION_REMINDER"); task.setScheduleConfig("{\"mode\":\"MANUAL\"}");
        service.runEnabledTasks();
        verifyNoInteractions(reminders, guard);
        when(capabilities.has(any(), eq("auto_task"))).thenReturn(true);
        when(runs.find(anyLong(), anyString())).thenReturn(new TeacherAutoTaskRunService.RunView(
                null, 1L, "manual:test-manual-0001", 0, "NOT_FOUND", 0, 0, null, null, false));
        service.runNow(counselor, 1L, "test-manual-0001");
        verify(reminders).sendReminder(20L, 9L, "请返岗");
        verify(tasks).updateStatus(eq(1L), eq(9L), eq("ENDED"), eq(0), isNull(), any());
    }

    @Test void scopeWideReminderHonorsSendLimitAndReportsRemainingStudents() {
        var task = dueScheduledTask("STATION_REMINDER"); task.setTargetScope("OWN_CLASSES");
        when(students.findByClassId(5L)).thenReturn(List.of(student(20L, "一"), student(21L, "二"), student(22L, "三")));
        when(capabilities.effectiveAutomationPolicy(any())).thenReturn(new AutomationPolicy(true, true, true,
                Set.of("SEND_REMINDER"), Set.of("STATION_REMINDER"), Set.of("OWN_CLASSES"), 2));
        service.runEnabledTasks();
        verify(reminders, times(2)).sendReminder(anyLong(), eq(9L), eq("请返岗"));
        verify(runs).complete(anyString(), eq("PARTIAL"), argThat(r -> Integer.valueOf(1).equals(r.get("unprocessedCount"))), isNull());
    }

    @Test void selectionLeavingTeacherScopePausesRemainingExecution() {
        dueScheduledTask("STATION_REMINDER");
        when(scopes.contains(counselor, 20L)).thenReturn(true, false);
        service.runEnabledTasks();
        verifyNoInteractions(reminders, guard);
        verify(tasks).updateStatus(eq(1L), eq(9L), eq("PAUSED"), eq(0), contains("负责范围"), any());
    }

    @Test void scheduledEmailUnknownKeepsUncertainReceiptInsteadOfSuccessOrRetry() {
        dueScheduledTask("EMAIL");
        when(emails.sendEmail(anyString(), anyString(), anyString(), anyString())).thenReturn(false);
        service.runEnabledTasks();
        verify(emails, times(1)).sendEmail(eq("student@example.test"), anyString(), eq("请返岗"), eq("DeepIntern-辅导员-辅导员"));
        verify(guard).finish(any(), eq("UNKNOWN"), nullable(Long.class));
        verify(runs).complete(anyString(), eq("UNKNOWN"), anyMap(), isNull());
    }

    @Test void legacyEditPreservesSelectedStudentsAndStillRequiresScope() {
        var task = dueScheduledTask("STATION_REMINDER"); task.setStatus("PAUSED"); task.setEnabled(0);
        var updated = service.update(counselor, 1L, "提醒", "SCHEDULED_REMINDER", null, "STATION_REMINDER",
                "请返岗", 1440, null, "{\"mode\":\"DAILY\",\"time\":\"14:42\"}", 0L);
        assertEquals(List.of(20L), TeacherAutoTaskTargets.decode(updated.getTargetScope()));
    }

    private TeacherAutoTask task(Long id, String status, String trigger, Integer days, String action) {
        TeacherAutoTask task = new TeacherAutoTask();
        task.setId(id);
        task.setTeacherId(9L);
        task.setTeacherType("COUNSELOR");
        task.setStatus(status);
        task.setEnabled("ACTIVE".equals(status) ? 1 : 0);
        task.setTriggerCondition(trigger);
        task.setTriggerDays(days);
        task.setFrequencyMinutes(1440);
        task.setAction(action);
        task.setMessageTemplate("请交材料");
        task.setUpdateTime(new Date());
        return task;
    }

    private StudentUser student(Long id, String name) {
        StudentUser student = new StudentUser();
        student.setId(id);
        student.setName(name);
        student.setStatus(1);
        return student;
    }

    private StudentTodoItemDTO todo(String key, OffsetDateTime dueAt) {
        return new StudentTodoItemDTO("todo:1", key, "待办", "描述", "HIGH", dueAt, 1, null);
    }
}
