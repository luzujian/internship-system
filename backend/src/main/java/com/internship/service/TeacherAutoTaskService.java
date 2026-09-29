package com.internship.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.agent.business.AgentCapabilityService;
import com.internship.agent.business.AgentCapabilityService.AutomationPolicy;
import com.internship.agent.business.AgentFeatureGate;
import com.internship.agent.business.AgentTypes.Actor;
import com.internship.entity.ClassCounselorRelation;
import com.internship.entity.StudentUser;
import com.internship.entity.TeacherAutoTask;
import com.internship.entity.TeacherAutoTaskLog;
import com.internship.entity.TeacherAutoTaskSendLog;
import com.internship.entity.TeacherUser;
import com.internship.entity.dto.StudentTodoItemDTO;
import com.internship.exception.BusinessException;
import com.internship.mapper.ClassCounselorRelationMapper;
import com.internship.mapper.StudentUserMapper;
import com.internship.mapper.TeacherAutoTaskLogMapper;
import com.internship.mapper.TeacherAutoTaskMapper;
import com.internship.mapper.TeacherAutoTaskSendLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import jakarta.annotation.PreDestroy;

/**
 * 教师条件触发自动化（阶段五）。
 * 流程：模型解析预览 → 确认创建 → 启用 → 定时检查最新状态 → 按频率去重执行 → 记录结果。
 * 范围：当前教师授权的班级、系或学院，可进一步限定指定学生；业务催交发送前重查待办。
 */
@Slf4j
@Service
public class TeacherAutoTaskService {

    public static final String TRIGGER_REFLECTION = "REFLECTION_NOT_SUBMITTED";
    public static final String TRIGGER_APPRAISAL = "APPRAISAL_MISSING";
    public static final String TRIGGER_OFFER = "OFFER_PENDING";
    public static final String TRIGGER_SCHEDULED = "SCHEDULED_REMINDER";
    public static final Set<String> TRIGGERS = Set.of(TRIGGER_REFLECTION, TRIGGER_APPRAISAL, TRIGGER_OFFER, TRIGGER_SCHEDULED);

    public static final String ACTION_STATION = "STATION_REMINDER";
    public static final String ACTION_EMAIL = "EMAIL";
    public static final Set<String> ACTIONS = Set.of(ACTION_STATION, ACTION_EMAIL);

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_PAUSED = "PAUSED";
    public static final String STATUS_ENDED = "ENDED";
    public static final String SEND_STATUS_PENDING = "PENDING";
    public static final String SEND_STATUS_UNKNOWN = "UNKNOWN";

    /** 单次执行最多触达学生数（防误配刷屏） */
    private static final int MAX_TARGETS_PER_RUN = 200;
    private static final String TASK_LOCK_PREFIX = "internship:teacher-auto-task:run:";
    private static final Duration TASK_LOCK_TTL = Duration.ofMinutes(15);
    private static final long PENDING_TIMEOUT_MS = 15 * 60_000L;
    private static final DefaultRedisScript<Long> RELEASE_LOCK = new DefaultRedisScript<>(
            "if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end", Long.class);
    private static final DefaultRedisScript<Long> RENEW_LOCK = new DefaultRedisScript<>(
            "if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('pexpire',KEYS[1],ARGV[2]) else return 0 end", Long.class);

    private final TeacherAutoTaskMapper tasks;
    private final TeacherAutoTaskLogMapper logs;
    private final TeacherAutoTaskSendLogMapper sends;
    private final TeacherUserService teacherUsers;
    private final ClassCounselorRelationMapper relations;
    private final StudentUserMapper students;
    private final StudentHomeTodoService todos;
    private final ReminderService reminderService;
    private final EmailService emailService;
    private final AgentCapabilityService capabilities;
    private final TeacherAutoTaskParseService parser;
    private final StringRedisTemplate redis;
    private final AgentFeatureGate featureGate;
    private final ObjectMapper json;
    private final ZoneId zone;
    private final Duration missedGrace;
    private final boolean testMode;
    private final Set<String> recipientAllowlist;
    private TeacherAutomationScopeService scopeService;
    private AgentNotificationGuardService notificationGuard;
    private AgentStationDeliveryService stationDelivery;
    private TeacherAutoTaskRunService runLedger;

    @Autowired
    public void configureRunLedger(TeacherAutoTaskRunService runLedger) { this.runLedger = runLedger; }

    @Autowired
    public void configureReliability(TeacherAutomationScopeService scopeService,
                                     AgentNotificationGuardService notificationGuard,
                                     AgentStationDeliveryService stationDelivery) {
        this.scopeService = scopeService;
        this.notificationGuard = notificationGuard;
        this.stationDelivery = stationDelivery;
    }
    private final ScheduledExecutorService lockMaintenance = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "teacher-auto-task-lock-renewer");
        thread.setDaemon(true);
        return thread;
    });

    public TeacherAutoTaskService(TeacherAutoTaskMapper tasks,
                                  TeacherAutoTaskLogMapper logs,
                                  TeacherAutoTaskSendLogMapper sends,
                                  TeacherUserService teacherUsers,
                                  ClassCounselorRelationMapper relations,
                                  StudentUserMapper students,
                                  StudentHomeTodoService todos,
                                  ReminderService reminderService,
                                  EmailService emailService,
                                  AgentCapabilityService capabilities,
                                  TeacherAutoTaskParseService parser,
                                  StringRedisTemplate redis) {
        this(tasks, logs, sends, teacherUsers, relations, students, todos, reminderService,
                emailService, capabilities, parser, redis, new AgentFeatureGate(null, "true"),
                new ObjectMapper(), "Asia/Shanghai", 60, false, "");
    }

    @Autowired
    public TeacherAutoTaskService(TeacherAutoTaskMapper tasks,
                                  TeacherAutoTaskLogMapper logs,
                                  TeacherAutoTaskSendLogMapper sends,
                                  TeacherUserService teacherUsers,
                                  ClassCounselorRelationMapper relations,
                                  StudentUserMapper students,
                                  StudentHomeTodoService todos,
                                  ReminderService reminderService,
                                  EmailService emailService,
                                  AgentCapabilityService capabilities,
                                  TeacherAutoTaskParseService parser,
                                  StringRedisTemplate redis,
                                  AgentFeatureGate featureGate,
                                  ObjectMapper json,
                                  @Value("${teacher.auto-task.zone:Asia/Shanghai}") String zone,
                                  @Value("${teacher.auto-task.missed-grace-minutes:60}") long missedGraceMinutes,
                                  @Value("${teacher.auto-task.test-mode:false}") boolean testMode,
                                  @Value("${teacher.auto-task.recipient-allowlist:}") String recipientAllowlist) {
        this.tasks = tasks;
        this.logs = logs;
        this.sends = sends;
        this.teacherUsers = teacherUsers;
        this.relations = relations;
        this.students = students;
        this.todos = todos;
        this.reminderService = reminderService;
        this.emailService = emailService;
        this.capabilities = capabilities;
        this.parser = parser;
        this.redis = redis;
        this.featureGate = featureGate;
        this.json = json;
        this.zone = ZoneId.of(zone == null || zone.isBlank() ? "Asia/Shanghai" : zone.trim());
        this.missedGrace = Duration.ofMinutes(Math.max(1, missedGraceMinutes));
        this.testMode = testMode;
        this.recipientAllowlist = parseAllowlist(recipientAllowlist);
    }

    /** 模型解析自然语言为结构化草稿；所有枚举和范围由解析服务白名单校验。 */
    public Map<String, Object> preview(String naturalLanguage, TeacherUser teacher) {
        featureGate.requireEnabled();
        requireCounselor(teacher);
        AutomationPolicy policy = automationPolicy(teacher);
        if (!policy.canManage()) {
            throw new BusinessException("当前身份未开通自动化任务能力，请联系管理员。");
        }
        Map<String, Object> draft = new LinkedHashMap<>(parser.parse(naturalLanguage));
        String trigger = (String) draft.get("triggerCondition");
        String action = (String) draft.get("action");
        Integer frequency = (Integer) draft.get("frequencyMinutes");
        draft.put("triggerLabel", triggerLabel(trigger));
        draft.put("actionLabel", actionLabel(action));
        draft.put("frequencyLabel", frequencyLabel(frequency));
        TeacherAutoTaskSchedule schedule = TeacherAutoTaskSchedule.parse(
                Objects.toString(draft.get("scheduleConfig"), null), json);
        draft.put("scheduleConfig", schedule.toJson(json));
        draft.put("scheduleLabel", scheduleLabel(schedule));
        List<String> questions = new ArrayList<>();
        if (draft.get("questions") instanceof List<?> values) values.forEach(q -> questions.add(String.valueOf(q)));
        boolean channelAvailable = action != null && ACTIONS.contains(action) && policy.allowsChannel(action)
                && (!ACTION_EMAIL.equals(action) || emailService.isConfigured());
        draft.put("channelAvailable", channelAvailable);
        if (!channelAvailable) questions.add(ACTION_EMAIL.equals(action) && !emailService.isConfigured()
                ? "邮件通道尚未配置，配置完成后才能创建邮件提醒。" : "当前授权或系统配置不支持该提醒渠道。");
        List<Long> selected = new ArrayList<>();
        boolean selectionValid = true;
        if (draft.get("targetQueries") instanceof List<?> queries) {
            for (Object value : queries) {
                String query = String.valueOf(value).trim();
                List<StudentUser> found = scopeService.matchStudents(teacher, query);
                if (found.isEmpty()) {
                    questions.add("在你的负责范围内未找到学生“" + query + "”，请核对用户名、学号或姓名。");
                    selectionValid = false;
                } else if (found.size() > 1) {
                    questions.add("“" + query + "”对应多名学生，请用用户名明确选择："
                            + String.join("、", found.stream().limit(5).map(StudentUser::getUsername).toList()) + "。");
                    selectionValid = false;
                } else if (!selected.contains(found.getFirst().getId())) selected.add(found.getFirst().getId());
            }
        }
        if ("SELECTED".equals(draft.get("targetMode")) && selected.isEmpty()) selectionValid = false;
        if (selected.size() > 20) { selectionValid = false; questions.add("一次最多指定20名学生，请减少选择。"); }
        String targetScope = selectionValid ? TeacherAutoTaskTargets.encode(
                TeacherAutomationScopeService.scopeOf(teacher.getTeacherType()), selected) : null;
        String targetLabel = selectionValid ? targetLabel(teacher, selected) : "提醒对象待核对";
        draft.put("targetStudentIds", selectionValid ? TeacherAutoTaskTargets.ids(selected) : List.of());
        draft.put("targetLabel", targetLabel);
        draft.put("scopeLabel", targetLabel);
        draft.put("targetScope", targetScope);
        TeacherAutoTask sample = new TeacherAutoTask();
        sample.setTriggerCondition(trigger); sample.setTriggerDays((Integer)draft.get("triggerDays"));
        sample.setTargetScope(targetScope);
        draft.put("targetCount", selectionValid && trigger != null && TRIGGERS.contains(trigger)
                ? resolveTargets(teacher, sample, Integer.MAX_VALUE).size() : null);
        draft.put("effectiveFrequencyMinutes", Math.max(frequency == null ? 1440 : frequency, policy.globalCooldownMinutes()));
        draft.put("questions", questions);
        draft.put("needsConfirm", questions.isEmpty() && selectionValid);
        return draft;
    }

    /** 管理端修改授权后立即收敛任务状态；定时执行也使用同一套策略判断。 */
    public List<TeacherAutoTask> pauseTasksWithoutEffectivePolicy() {
        List<TeacherAutoTask> active = tasks.findActive();
        List<TeacherAutoTask> paused = new ArrayList<>();
        if (active == null || active.isEmpty()) return paused;
        for (TeacherAutoTask task : active) {
            TeacherUser teacher = teacherUsers.findById(task.getTeacherId());
            String reason = policyBlockReason(teacher, task);
            if (reason == null) continue;
            Date now = new Date();
            pauseTask(task, reason, now);
            task.setStatus(STATUS_PAUSED);
            task.setEnabled(0);
            task.setBlockedReason(reason);
            task.setUpdateTime(now);
            paused.add(task);
        }
        return paused;
    }

    public Map<String, Object> capability(TeacherUser teacher) {
        AutomationPolicy policy = teacher == null ? AutomationPolicy.closed(false) : automationPolicy(teacher);
        Map<String, Object> result = new LinkedHashMap<>(policy.toMap());
        result.put("teacherType", teacher == null ? null : teacher.getTeacherType());
        result.put("emailConfigured", emailService.isConfigured());
        result.put("aiEnabled", featureGate.enabled());
        result.put("schedulerCron", "每分钟扫描；固定时间由任务配置决定");
        result.put("timezone", zone.getId());
        result.put("missedGraceMinutes", missedGrace.toMinutes());
        return result;
    }

    public TeacherAutoTask create(TeacherUser teacher, String name, String triggerCondition, Integer triggerDays,
                                  String action, String messageTemplate, Integer frequencyMinutes, String endAt,
                                  String scheduleConfig) {
        return create(teacher, name, triggerCondition, triggerDays, action, messageTemplate,
                frequencyMinutes, endAt, scheduleConfig, List.of());
    }

    public TeacherAutoTask create(TeacherUser teacher, String name, String triggerCondition, Integer triggerDays,
                                  String action, String messageTemplate, Integer frequencyMinutes, String endAt,
                                  String scheduleConfig, List<Long> targetStudentIds) {
        featureGate.requireEnabled();
        requireCounselor(teacher);
        AutomationPolicy policy = requireManagePolicy(teacher);
        String trigger = requireTrigger(triggerCondition);
        Integer days = requireTriggerDays(trigger, triggerDays);
        String act = requireAction(action);
        if (!policy.allowsChannel(act)) {
            throw new BusinessException("当前授权不允许使用该提醒渠道。");
        }
        if (ACTION_EMAIL.equals(act)) {
            requireEmailConfigured();
        }
        String message = requireMessage(messageTemplate);
        Integer frequency = requireFrequency(frequencyMinutes);
        Date end = requireEndAt(endAt);
        String scheduleJson = requireSchedule(scheduleConfig);
        requireScheduledTime(trigger, scheduleJson);
        List<Long> selected = requireSelectedTargets(teacher, targetStudentIds);

        TeacherAutoTask task = new TeacherAutoTask();
        task.setTeacherId(teacher.getId());
        task.setTeacherType(teacher.getTeacherType());
        task.setName(name == null || name.isBlank()
                ? triggerLabel(trigger) + "自动" + actionLabel(act) : name.trim());
        task.setTriggerCondition(trigger);
        task.setTriggerDays(days);
        task.setFrequencyMinutes(frequency);
        task.setEndAt(end);
        task.setScheduleConfig(scheduleJson);
        task.setStatus(STATUS_PAUSED);
        task.setEnabled(0);
        task.setAction(act);
        task.setMessageTemplate(message);
        task.setTargetScope(TeacherAutoTaskTargets.encode(TeacherAutomationScopeService.scopeOf(teacher.getTeacherType()), selected));
        Date now = new Date();
        task.setCreateTime(now);
        task.setUpdateTime(now);
        tasks.insert(task);
        describeTargets(teacher, task);
        return task;
    }

    public TeacherAutoTask create(TeacherUser teacher, String name, String triggerCondition, Integer triggerDays,
                                  String action, String messageTemplate, Integer frequencyMinutes, String endAt) {
        return create(teacher, name, triggerCondition, triggerDays, action, messageTemplate,
                frequencyMinutes, endAt, null);
    }

    public List<TeacherAutoTask> list(TeacherUser teacher) {
        requireCounselor(teacher);
        List<TeacherAutoTask> result = tasks.findByTeacherId(teacher.getId());
        result.forEach(task -> describeTargets(teacher, task));
        return result;
    }

    public TeacherAutoTask get(TeacherUser teacher, Long id) {
        requireCounselor(teacher);
        TeacherAutoTask task = tasks.findById(id);
        if (task == null || !teacher.getId().equals(task.getTeacherId())) {
            throw new BusinessException("任务不存在或无权访问。");
        }
        describeTargets(teacher, task);
        return task;
    }

    public TeacherAutoTask update(TeacherUser teacher, Long id, String name, String triggerCondition,
                                  Integer triggerDays, String action, String messageTemplate,
                                  Integer frequencyMinutes, String endAt, String scheduleConfig) {
        return update(teacher,id,name,triggerCondition,triggerDays,action,messageTemplate,frequencyMinutes,endAt,scheduleConfig,null);
    }

    public TeacherAutoTask update(TeacherUser teacher, Long id, String name, String triggerCondition,
                                  Integer triggerDays, String action, String messageTemplate,
                                  Integer frequencyMinutes, String endAt, String scheduleConfig, Long expectedVersion) {
        return update(teacher, id, name, triggerCondition, triggerDays, action, messageTemplate,
                frequencyMinutes, endAt, scheduleConfig, expectedVersion, null);
    }

    public TeacherAutoTask update(TeacherUser teacher, Long id, String name, String triggerCondition,
                                  Integer triggerDays, String action, String messageTemplate,
                                  Integer frequencyMinutes, String endAt, String scheduleConfig, Long expectedVersion,
                                  List<Long> targetStudentIds) {
        featureGate.requireEnabled();
        TeacherAutoTask task = get(teacher, id);
        if (expectedVersion != null && task.getVersion() != expectedVersion) {
            throw new BusinessException("任务已被修改，请刷新后重新核对，当前草稿尚未保存。");
        }
        if (task.isActive()) throw new BusinessException("请先暂停任务，再修改和重新核对启用，避免后台继续按旧条件发送。");
        if (STATUS_ENDED.equals(task.getStatus())) {
            throw new BusinessException("已结束的任务不能修改，请新建任务。");
        }
        String trigger = requireTrigger(triggerCondition);
        Integer days = requireTriggerDays(trigger, triggerDays);
        String act = requireAction(action);
        AutomationPolicy policy = requireManagePolicy(teacher);
        if (!policy.allowsChannel(act)) {
            throw new BusinessException("当前授权不允许使用该提醒渠道。");
        }
        if (ACTION_EMAIL.equals(act)) {
            requireEmailConfigured();
        }
        String message = requireMessage(messageTemplate);
        Integer frequency = requireFrequency(frequencyMinutes);
        String scheduleJson = requireSchedule(scheduleConfig);
        requireScheduledTime(trigger, scheduleJson);
        List<Long> selected = requireSelectedTargets(teacher, targetStudentIds == null
                ? TeacherAutoTaskTargets.decode(task.getTargetScope()) : targetStudentIds);
        Date end = requireEndAt(endAt);
        task.setName(name == null || name.isBlank() ? task.getName() : name.trim());
        task.setTriggerCondition(trigger);
        task.setTriggerDays(days);
        task.setAction(act);
        task.setMessageTemplate(message);
        task.setFrequencyMinutes(frequency);
        task.setEndAt(end);
        task.setScheduleConfig(scheduleJson);
        task.setTargetScope(TeacherAutoTaskTargets.encode(TeacherAutomationScopeService.scopeOf(teacher.getTeacherType()), selected));
        task.setUpdateTime(new Date());
        if (tasks.updateByIdAndTeacher(task) != 1) throw new BusinessException("任务已被修改，请刷新后重新核对。");
        task.setVersion(task.getVersion() + 1);
        describeTargets(teacher, task);
        return task;
    }

    public TeacherAutoTask update(TeacherUser teacher, Long id, String name, String triggerCondition,
                                  Integer triggerDays, String action, String messageTemplate,
                                  Integer frequencyMinutes, String endAt) {
        return update(teacher, id, name, triggerCondition, triggerDays, action, messageTemplate,
                frequencyMinutes, endAt, null);
    }

    @Transactional
    public TeacherAutoTask enableConfirmed(TeacherUser teacher, Long id, String expectedVersion) {
        TeacherAutoTask locked = tasks.lockById(id);
        if (locked == null || !teacher.getId().equals(locked.getTeacherId())) throw new BusinessException("任务不存在或无权访问。");
        if (!Long.toString(locked.getVersion()).equals(expectedVersion)) throw new BusinessException("任务已经变化，请重新预览后启用。");
        return setEnabled(teacher, id, true);
    }

    public Map<String,Object> enableImpact(TeacherUser teacher,TeacherAutoTask task) {
        requireCounselor(teacher);
        runLedger.reconcilePending(task.getId());
        runLedger.requireResolved(task.getId());
        AutomationPolicy policy = requireManagePolicy(teacher);
        List<Long> selected = requireSelectedTargets(teacher, TeacherAutoTaskTargets.decode(task.getTargetScope()));
        requireScheduledTime(task.getTriggerCondition(), task.getScheduleConfig());
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("task",task);
        result.put("scopeLabel",targetLabel(teacher, selected));
        result.put("scopeCount",selected.isEmpty() ? scopeService.studentIds(teacher).size() : selected.size());
        result.put("matchingCount",resolveTargets(teacher,task,Integer.MAX_VALUE).size());
        result.put("effectiveFrequencyMinutes",Math.max(task.getFrequencyMinutes(),policy.globalCooldownMinutes()));
        result.put("maxTargetsPerRun",Math.min(MAX_TARGETS_PER_RUN,policy.maxTargetsPerRun()));
        result.put("observedAt",OffsetDateTime.now(zone).toString());
        return result;
    }

    public TeacherAutoTask setEnabled(TeacherUser teacher, Long id, boolean enabled) {
        TeacherAutoTask task = get(teacher, id);
        if (STATUS_ENDED.equals(task.getStatus())) {
            throw new BusinessException("已结束的任务不能再次启用。");
        }
        if (enabled) {
            runLedger.requireResolved(id);
            AutomationPolicy policy = requireManagePolicy(teacher);
            if (!policy.allowsChannel(task.getAction())) {
                throw new BusinessException("当前授权不允许使用该提醒渠道。");
            }
            if (ACTION_EMAIL.equals(task.getAction())) requireEmailConfigured();
            requireSelectedTargets(teacher, TeacherAutoTaskTargets.decode(task.getTargetScope()));
            requireTrigger(task.getTriggerCondition());
            requireScheduledTime(task.getTriggerCondition(), task.getScheduleConfig());
            task.setStatus(STATUS_ACTIVE);
            task.setEnabled(1);
            task.setBlockedReason(null);
        } else {
            task.setStatus(STATUS_PAUSED);
            task.setEnabled(0);
        }
        task.setUpdateTime(new Date());
        tasks.updateStatus(id, teacher.getId(), task.getStatus(), task.getEnabled(), task.getBlockedReason(), task.getUpdateTime());
        task.setVersion(task.getVersion() + 1);
        return task;
    }

    public TeacherAutoTask end(TeacherUser teacher, Long id) {
        TeacherAutoTask task = get(teacher, id);
        if (STATUS_ENDED.equals(task.getStatus())) return task;
        task.setStatus(STATUS_ENDED);
        task.setEnabled(0);
        task.setUpdateTime(new Date());
        tasks.updateStatus(id, teacher.getId(), STATUS_ENDED, 0, task.getBlockedReason(), task.getUpdateTime());
        return task;
    }

    @Transactional
    public void delete(TeacherUser teacher, Long id) {
        TeacherAutoTask task = get(teacher, id);
        if (task.isActive()) throw new BusinessException("请先暂停或结束任务，再删除任务。");
        // Retain send/audit records and global notification claims after removing a task.
        tasks.deleteByIdAndTeacher(id, teacher.getId());
    }

    public List<TeacherAutoTaskLog> runLogs(TeacherUser teacher, Long taskId, int limit) {
        get(teacher, taskId);
        return logs.findByTaskId(taskId, Math.min(Math.max(limit, 1), 50));
    }

    public List<TeacherAutoTaskSendLog> sendLogs(TeacherUser teacher, Long taskId, int limit) {
        get(teacher, taskId);
        return sends.findByTaskId(taskId, Math.min(Math.max(limit, 1), 100));
    }

    /** 定时入口：遍历 ACTIVE 且未到期任务逐个执行，单任务异常不影响其他任务。 */
    public void runEnabledTasks() {
        List<TeacherAutoTask> active = tasks.findActiveIncludingExpired();
        if (active == null || active.isEmpty()) return;
        log.info("教师自动化调度开始 aiEnabled={} tasks={}", featureGate.enabled(), active.size());
        int processed = 0;
        for (TeacherAutoTask task : active) {
            try {
                if (runOne(task, true)) processed++;
            } catch (Exception e) {
                log.error("自动化任务执行失败 task={}: {}", task.getId(), e.getMessage(), e);
            }
        }
        log.info("教师自动化调度结束 tasks={} processed={}", active.size(), processed);
    }

    /** 手动执行一次：本人任务、能力校验与定时执行完全一致，也遵守冷却去重。 */
    public TeacherAutoTaskLog runNow(TeacherUser teacher, Long id) {
        runNow(teacher, id, UUID.randomUUID().toString());
        List<TeacherAutoTaskLog> records = logs.findByTaskId(id, 1);
        return records.isEmpty() ? null : records.get(0);
    }

    public TeacherAutoTaskRunService.RunView runNow(TeacherUser teacher, Long id, String clientRequestId) {
        String occurrence = TeacherAutoTaskRunService.manualKey(clientRequestId);
        TeacherAutoTask task = get(teacher, id);
        TeacherAutoTaskRunService.RunView existing = runLedger.find(id, occurrence);
        if (!"NOT_FOUND".equals(existing.status())) return existing;
        featureGate.requireEnabled();
        if (!task.isActive()) {
            throw new BusinessException("请先核对并启用任务，再立即执行。");
        }
        requireCapability(teacher, "auto_task");
        AutomationPolicy policy = automationPolicy(teacher);
        if (!policy.canManage() || !policy.allowsChannel(task.getAction())) {
            throw new BusinessException("当前授权不允许执行该任务，请联系管理员。");
        }
        if (!runOne(task, false, occurrence)) {
            throw new BusinessException("任务正在执行或执行锁不可用，请稍后重试。");
        }
        return runLedger.find(id, occurrence);
    }

    public TeacherAutoTaskRunService.RunView findRun(TeacherUser teacher, Long id, String clientRequestId) {
        get(teacher, id);
        return runLedger.find(id, TeacherAutoTaskRunService.manualKey(clientRequestId));
    }

    public TeacherAutoTaskRunService.RunView pendingRun(TeacherUser teacher, Long id) {
        get(teacher,id);
        return runLedger.reconcilePending(id);
    }

    private boolean runOne(TeacherAutoTask task, boolean scheduled) {
        return runOne(task, scheduled, null);
    }

    private boolean runOne(TeacherAutoTask task, boolean scheduled, String requestedOccurrence) {
        String key = TASK_LOCK_PREFIX + task.getId();
        String token = UUID.randomUUID().toString();
        Boolean acquired;
        try {
            acquired = redis.opsForValue().setIfAbsent(key, token, TASK_LOCK_TTL);
        } catch (Exception e) {
            log.warn("自动化任务执行锁不可用，任务跳过 task={}: {}", task.getId(), e.getMessage());
            return false;
        }
        if (!Boolean.TRUE.equals(acquired)) {
            log.info("自动化任务正在执行，跳过本次重复触发 task={}", task.getId());
            return false;
        }
        ScheduledFuture<?> renewal = lockMaintenance.scheduleAtFixedRate(
                () -> renewLock(key, token), 5, 5, TimeUnit.MINUTES);
        try {
            return doRun(task, scheduled, requestedOccurrence);
        } finally {
            renewal.cancel(false);
            releaseLock(key, token);
        }
    }

    private boolean doRun(TeacherAutoTask task, boolean scheduled, String requestedOccurrence) {
        Date now = new Date();
        TeacherAutoTask latest = tasks.findById(task.getId());
        if (latest == null || !latest.isActive() || latest.getVersion() != task.getVersion()) return false;
        if (task.getEndAt() != null && !task.getEndAt().after(now)) {
            writeAggregateLog(task, 0, 0, "任务已到达结束时间，不再执行。");
            endTask(task, now);
            return false;
        }
        if (!featureGate.enabled()) {
            String reason = "AI总开关已关闭，自动化暂不执行";
            if (!reason.equals(task.getBlockedReason())) {
                writeAggregateLog(task, 0, 0, reason + "，任务和日志已保留。");
                tasks.updateBlockedReason(task.getId(), reason, now);
            }
            return false;
        }
        TeacherUser teacher = teacherUsers.findById(task.getTeacherId());
        String reason = policyBlockReason(teacher, task);
        if (reason != null) {
            writeAggregateLog(task, 0, 0, reason + "，本次跳过。");
            pauseTask(task, reason, now);
            return false;
        }

        String occurrence = requestedOccurrence;
        if (scheduled) {
            TeacherAutoTaskSchedule schedule = TeacherAutoTaskSchedule.parse(task.getScheduleConfig(), json);
            OffsetDateTime nowOffset = OffsetDateTime.ofInstant(now.toInstant(), zone);
            OffsetDateTime lastRun = task.getLastRunAt() == null ? null
                    : OffsetDateTime.ofInstant(task.getLastRunAt().toInstant(), zone);
            TeacherAutoTaskSchedule.DueEvaluation evaluation = schedule.evaluate(nowOffset, lastRun, missedGrace);
            if (evaluation.state() == TeacherAutoTaskSchedule.DueState.NOT_DUE
                    || evaluation.state() == TeacherAutoTaskSchedule.DueState.ALREADY_DONE) {
                return false;
            }
            if (evaluation.state() == TeacherAutoTaskSchedule.DueState.MISSED) {
                writeAggregateLog(task, 0, 0, "已错过计划时间" + evaluation.occurrence()
                        + "，超过补跑窗口，本次跳过。");
                tasks.updateLastRun(task.getId(), now);
                if (schedule.mode() == TeacherAutoTaskSchedule.Mode.ONCE) endTask(task, now);
                return false;
            }
            occurrence = "scheduled:" + evaluation.occurrence().toInstant();
        }

        TeacherAutoTaskRunService.RunView run = runLedger.claim(task.getId(), occurrence, task.getVersion());
        if (!run.claimed()) return true;
        String runId = run.runId();
        try {
        int maxTargets = Math.min(MAX_TARGETS_PER_RUN,
                automationPolicy(teacher).maxTargetsPerRun());
        List<StudentUser> candidates = resolveTargets(teacher, task, Integer.MAX_VALUE);
        int sent = 0;
        int skipped = 0;
        int failed = 0;
        int unknown = 0;
        List<String> skipReasons = new ArrayList<>();
        int attempts = 0;
        int processed = 0;
        for (StudentUser student : candidates) {
            if (attempts >= maxTargets) break;
            processed++;
            runLedger.heartbeat(runId);
            AgentNotificationGuardService.Claim claim = null;
            TeacherAutoTaskSendLog sending = null;
            boolean externalAttempted = false;
            boolean stationAttempted = false;
            Date lastSuccess = sends.findLastSuccess(task.getId(), student.getId(), task.getAction());
            if (lastSuccess != null && withinCooldown(lastSuccess, now, Math.max(task.getFrequencyMinutes(), automationPolicy(teacher).globalCooldownMinutes()))) {
                skipped++;
                writeSendLog(task, student, "SKIPPED_COOLDOWN", "冷却期内，未重复发送。", now);
                continue;
            }
            try {
                TeacherAutoTaskSendLog pending = sends.findPending(task.getId(), student.getId(), task.getAction());
                if (pending != null) {
                    boolean stale = pending.getSentAt() == null
                            || now.getTime() - pending.getSentAt().getTime() > PENDING_TIMEOUT_MS;
                    if (stale) {
                        sends.updateStatus(pending.getId(), SEND_STATUS_UNKNOWN,
                                "发送结果未确认，已停止自动重发。", now);
                        unknown++;
                        skipReasons.add(student.getName() + "发送结果未确认");
                    } else {
                        skipped++;
                        writeSendLog(task, student, "SKIPPED_COOLDOWN", "上一次发送仍在确认中。", now);
                    }
                    continue;
                }

                String email = null;
                if (ACTION_EMAIL.equals(task.getAction())) {
                    email = student.getEmail() == null ? "" : student.getEmail().trim();
                    if (email.isEmpty()) {
                        failed++;
                        writeSendLog(task, student, "FAILED", "学生未绑定邮箱。", now);
                        skipReasons.add(student.getName() + "无邮箱");
                        continue;
                    }
                    if (!testRecipientAllowed(email)) {
                        failed++;
                        writeSendLog(task, student, "FAILED", "测试模式收件人不在白名单，已阻止发送。", now);
                        skipReasons.add(student.getName() + "收件人未入测试白名单");
                        continue;
                    }
                }

                TeacherUser currentTeacher = teacherUsers.findById(task.getTeacherId());
                TeacherAutoTask currentTask = tasks.findById(task.getId());
                if (currentTask == null || !currentTask.isActive() || currentTask.getVersion() != task.getVersion()
                        || (currentTask.getEndAt() != null && !currentTask.getEndAt().after(new Date()))) {
                    writeAggregateLog(task, candidates.size(), sent, "任务已暂停、结束或修改，剩余发送已停止；已发送" + sent + "人。");
                    runLedger.complete(runId, unknown > 0 ? "UNKNOWN" : sent > 0 ? "PARTIAL" : "CANCELLED",
                            Map.of("targetCount", candidates.size(), "successCount", sent, "unknownCount", unknown, "detail", "任务已变化，剩余发送已停止。"), null);
                    return true;
                }
                String currentReason = policyBlockReason(currentTeacher, currentTask);
                if (currentReason != null) {
                    Date stoppedAt = new Date();
                    pauseTask(task, currentReason, stoppedAt);
                    writeAggregateLog(task, candidates.size(), sent,
                            "授权已变化，停止剩余发送：" + currentReason + "；已发送" + sent + "人。");
                    tasks.updateLastRun(task.getId(), stoppedAt);
                    runLedger.complete(runId, unknown > 0 ? "UNKNOWN" : sent > 0 ? "PARTIAL" : "CANCELLED",
                            Map.of("targetCount", candidates.size(), "successCount", sent, "unknownCount", unknown, "detail", currentReason), null);
                    return true;
                }
                List<Long> currentSelected = TeacherAutoTaskTargets.decode(currentTask.getTargetScope());
                if (!scopeService.contains(currentTeacher, student.getId())
                        || (!currentSelected.isEmpty() && !currentSelected.contains(student.getId()))) {
                    skipped++;
                    writeSendLog(task, student, "SKIPPED_SCOPE", "学生已不在当前负责范围。", now);
                    continue;
                }
                boolean customReminder = TRIGGER_SCHEDULED.equals(currentTask.getTriggerCondition());
                StudentTodoItemDTO currentTodo = customReminder ? null : matchingTodo(student.getId(), currentTask, OffsetDateTime.now(zone));
                if (!customReminder && currentTodo == null) {
                    skipped++;
                    writeSendLog(task, student, "SKIPPED_DONE", "学生已完成或当前不再满足提醒条件。", now);
                    continue;
                }
                String todoId = customReminder ? scheduledIdentity(currentTask.getMessageTemplate()) : todoIdentity(currentTodo);
                int cooldown = Math.max(task.getFrequencyMinutes(), automationPolicy(currentTeacher).globalCooldownMinutes());
                claim = notificationGuard.claim(student.getId(), todoId, task.getAction(), task.getId(), cooldown);
                if (!claim.acquired()) {
                    skipped++;
                    writeSendLog(task, student, "SKIPPED_SHARED", claim.reason(), now);
                    continue;
                }
                attempts++;
                sending = writeSendLog(task, student, SEND_STATUS_PENDING, "等待发送结果。", now, todoId, runId);
                if (ACTION_EMAIL.equals(task.getAction())) {
                    externalAttempted = true;
                    boolean ok = emailService.sendEmail(email, "实习事项提醒", task.getMessageTemplate(), EmailService.senderDisplayName(currentTeacher == null ? teacher.getTeacherType() : currentTeacher.getTeacherType(), currentTeacher == null ? teacher.getName() : currentTeacher.getName()));
                    String state = ok ? "SENT" : SEND_STATUS_UNKNOWN;
                    sends.updateStatus(sending.getId(), state, ok ? "邮件服务已接受发送，收件情况尚未核实。" : "邮件发送结果待核实，已阻止自动重发。", now);
                    notificationGuard.finish(claim, state, sending.getId());
                    if (ok) sent++; else unknown++;
                } else {
                    stationAttempted = true;
                    stationDelivery.send(student.getId(), teacher.getId(), task.getMessageTemplate(), sending.getId(), claim);
                    sent++;
                }
            } catch (Exception e) {
                boolean uncertain = externalAttempted || (stationAttempted &&
                        (e instanceof org.springframework.transaction.TransactionException
                         || e instanceof org.springframework.dao.DataAccessResourceFailureException));
                String state = uncertain ? SEND_STATUS_UNKNOWN : "FAILED";
                if (uncertain) unknown++; else failed++;
                try {
                    if (sending != null) sends.updateStatus(sending.getId(), state,
                            uncertain ? "发送结果待核实，已停止自动重发。" : "执行未完成，请核对任务记录。", now);
                    else writeSendLog(task, student, state, "执行未完成，通知没有重试。", now);
                    if (claim != null && claim.acquired()) notificationGuard.finish(claim, state, sending == null ? null : sending.getId());
                } catch (Exception recordError) {
                    // A PENDING claim remains closed if recording the outcome fails.
                    log.warn("通知回执写入失败 task={} student={}", task.getId(), student.getId());
                }
                skipReasons.add(student.getName() + "执行异常");
            }
        }
        int remaining = candidates.size() - processed;
        String detail = "命中" + candidates.size() + "人，发送" + sent + "人，跳过" + skipped
                + "人，失败" + failed + "人，结果未确认" + unknown + "人。"
                + (remaining > 0 ? "达到单次人数上限，另有" + remaining + "人尚未处理。" : "")
                + (skipReasons.isEmpty() ? "" : "跳过：" + String.join("；",
                skipReasons.subList(0, Math.min(10, skipReasons.size()))) + "。");
        writeAggregateLog(task, candidates.size(), sent, detail);
        if (task.getBlockedReason() != null) {
            tasks.updateBlockedReason(task.getId(), null, now);
        }
        tasks.updateLastRun(task.getId(), now);
        if (Set.of(TeacherAutoTaskSchedule.Mode.ONCE, TeacherAutoTaskSchedule.Mode.MANUAL)
                .contains(TeacherAutoTaskSchedule.parse(task.getScheduleConfig(), json).mode())) endTask(task, now);
        runLedger.complete(runId, unknown > 0 ? "UNKNOWN" : remaining > 0 ? "PARTIAL" : failed > 0 ? (sent > 0 ? "PARTIAL" : "FAILED") : "SUCCEEDED",
                Map.of("targetCount", candidates.size(), "successCount", sent, "failedCount", failed,
                        "unknownCount", unknown, "skippedCount", skipped, "unprocessedCount", remaining, "detail", detail), null);
        return true;
        } catch (RuntimeException e) {
            try { runLedger.complete(runId, "UNKNOWN", null, "执行被中断或回执未能完整保存，请核验发送明细；不会自动重跑。"); }
            catch (RuntimeException receiptError) { log.warn("运行回执保存失败 task={} run={}", task.getId(), runId); }
            throw e;
        }
    }

    private boolean withinCooldown(Date last, Date now, Integer frequencyMinutes) {
        if (last == null || frequencyMinutes == null) return false;
        return now.getTime() - last.getTime() < frequencyMinutes * 60_000L;
    }

    /** 解析目标：辅导员负责班级内、当前仍命中条件且处于临期窗口的学生。 */
    private List<StudentUser> resolveTargets(TeacherUser teacher, TeacherAutoTask task, int maxTargets) {
        List<StudentUser> result = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now(zone);
        List<Long> selected = TeacherAutoTaskTargets.decode(task.getTargetScope());
        for (Long id : scopeService.studentIds(teacher)) {
            if (!selected.isEmpty() && !selected.contains(id)) continue;
            StudentUser student = students.findById(id);
            if (student != null && Integer.valueOf(1).equals(student.getStatus())
                    && matches(id, task, now) && result.size() < maxTargets) result.add(student);
        }
        return result;
    }

    /** 条件判定复用学生首页待办口径，并发送前重查；材料类条件叠加临期天数窗口。 */
    private boolean matches(Long studentId, TeacherAutoTask task, OffsetDateTime now) {
        return TRIGGER_SCHEDULED.equals(task.getTriggerCondition()) || matchingTodo(studentId, task, now) != null;
    }

    static String scheduledIdentity(String message) {
        // Shared across teachers for the same recipient/channel/message, without leaking message text into the key.
        return TRIGGER_SCHEDULED + ":" + org.springframework.util.DigestUtils.md5DigestAsHex(
                message.strip().replaceAll("\\s+", " ").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private StudentTodoItemDTO matchingTodo(Long studentId, TeacherAutoTask task, OffsetDateTime now) {
        List<StudentTodoItemDTO> items;
        try {
            items = todos.getActionableTodos(studentId);
        } catch (Exception e) {
            return null;
        }
        if (items == null || items.isEmpty()) return null;
        for (StudentTodoItemDTO item : items) {
            String key = item.key();
            if (TRIGGER_REFLECTION.equals(task.getTriggerCondition())
                    && "REFLECTION_SUBMISSION_REQUIRED".equals(key)) {
                if (withinWindow(item.dueAt(), task.getTriggerDays(), now)) return item;
            }
            if (TRIGGER_APPRAISAL.equals(task.getTriggerCondition())
                    && "APPRAISAL_SUBMISSION_REQUIRED".equals(key)) {
                if (withinWindow(item.dueAt(), task.getTriggerDays(), now)) return item;
            }
            if (TRIGGER_OFFER.equals(task.getTriggerCondition())
                    && ("OFFER_CONFIRMATION_REQUIRED".equals(key)
                    || "CONFIRMATION_RESUBMISSION_REQUIRED".equals(key))) {
                return item;
            }
        }
        return null;
    }

    static String todoIdentity(StudentTodoItemDTO item) {
        Map<String,Object> params = item.action() == null || item.action().params() == null ? Map.of() : item.action().params();
        Object placement = params.get("placementId");
        if (placement != null && "REFLECTION_SUBMISSION_REQUIRED".equals(item.key()))
            return item.key() + ":placement:" + placement + ":period:" + params.getOrDefault("periodNumber", item.id());
        if (placement != null && "APPRAISAL_SUBMISSION_REQUIRED".equals(item.key()))
            return item.key() + ":placement:" + placement;
        return item.key() + ":" + item.stageNo() + ":" + item.id();
    }

    /** 截止日为空视为不临期；到期前 N 天（按自然日）或已逾期均视为命中。 */
    private boolean withinWindow(OffsetDateTime dueAt, Integer triggerDays, OffsetDateTime now) {
        if (dueAt == null) return false;
        int days = triggerDays == null ? 2 : triggerDays;
        OffsetDateTime windowStart = dueAt.minusDays(days).truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        return !now.isBefore(windowStart);
    }

    private TeacherAutoTaskSendLog writeSendLog(TeacherAutoTask task, StudentUser student,
                                                String status, String detail, Date now) {
        return writeSendLog(task, student, status, detail, now, null, null);
    }
    private TeacherAutoTaskSendLog writeSendLog(TeacherAutoTask task, StudentUser student,
                                                String status, String detail, Date now, String todoId, String runId) {
        TeacherAutoTaskSendLog record = new TeacherAutoTaskSendLog();
        record.setTaskId(task.getId());
        record.setStudentId(student.getId());
        record.setStudentName(student.getName());
        record.setChannel(task.getAction());
        record.setSentAt(now);
        record.setStatus(status);
        record.setDetail(detail);
        record.setCreateTime(now);
        record.setTodoId(todoId);
        record.setRunId(runId);
        sends.insert(record);
        return record;
    }

    private void writeAggregateLog(TeacherAutoTask task, int targetCount, int successCount, String detail) {
        TeacherAutoTaskLog record = new TeacherAutoTaskLog();
        record.setTaskId(task.getId());
        Date now = new Date();
        record.setExecutedAt(now);
        record.setChannel(task.getAction());
        record.setTargetCount(targetCount);
        record.setSuccessCount(successCount);
        record.setDetail(detail);
        record.setCreateTime(now);
        logs.insert(record);
    }

    private void requireCounselor(TeacherUser teacher) {
        scopeService.requireAuthorization(teacher);
    }

    private String requireTrigger(String trigger) {
        if (trigger == null || !TRIGGERS.contains(trigger)) throw new BusinessException("不支持的触发条件。");
        return trigger;
    }

    /** Validate a manual preview without creating a task or contacting any student. */
    public void validateDraft(TeacherUser teacher, String trigger, Integer days, String action, String message,
                              Integer frequency, String endAt, String schedule, List<Long> selected) {
        featureGate.requireEnabled();
        requireCounselor(teacher);
        AutomationPolicy policy = requireManagePolicy(teacher);
        requireTriggerDays(requireTrigger(trigger), days);
        requireAction(action);
        if (!policy.allowsChannel(action)) throw new BusinessException("当前授权不允许使用该提醒渠道。");
        if (ACTION_EMAIL.equals(action)) requireEmailConfigured();
        requireMessage(message);
        requireFrequency(frequency);
        requireEndAt(endAt);
        requireScheduledTime(trigger, requireSchedule(schedule));
        requireSelectedTargets(teacher, selected);
    }

    private void requireScheduledTime(String trigger, String schedule) {
        if (TRIGGER_SCHEDULED.equals(trigger) && TeacherAutoTaskSchedule.parse(schedule, json).isWindow()) {
            throw new BusinessException("定时提醒需要具体执行时间，请选择每天、每周或指定日期的时间。");
        }
    }

    private List<Long> requireSelectedTargets(TeacherUser teacher, List<Long> selected) {
        List<Long> ids = TeacherAutoTaskTargets.ids(selected);
        for (Long id : ids) if (!scopeService.contains(teacher, id)) {
            throw new BusinessException("指定学生不在你当前负责范围内或账号已停用，请重新核对提醒对象。");
        }
        return ids;
    }

    private String targetLabel(TeacherUser teacher, List<Long> selected) {
        if (selected.isEmpty()) return scopeService.label(teacher);
        List<String> labels = new ArrayList<>();
        for (Long id : selected) {
            if (!scopeService.contains(teacher, id)) { labels.add("学生已不在当前负责范围"); continue; }
            StudentUser student = students.findById(id);
            if (student == null) { labels.add("学生待核对"); continue; }
            labels.add(Objects.toString(student.getName(), "学生") + " · " + Objects.toString(student.getUsername(), ""));
        }
        return String.join("、", labels);
    }

    private void describeTargets(TeacherUser teacher, TeacherAutoTask task) {
        try {
            List<Long> selected = TeacherAutoTaskTargets.decode(task.getTargetScope());
            task.setTargetStudentIds(selected);
            task.setTargetLabel(targetLabel(teacher, selected));
        } catch (BusinessException e) {
            // Keep pause/delete available for a damaged configuration; execution remains fail-closed.
            task.setTargetStudentIds(null);
            task.setTargetLabel("提醒对象配置无效，请重新核对");
        }
    }

    private Integer requireTriggerDays(String trigger, Integer days) {
        if (TRIGGER_OFFER.equals(trigger) || TRIGGER_SCHEDULED.equals(trigger)) return null;
        if (days == null) return 2;
        if (days < 1 || days > 30) throw new BusinessException("临期天数需在1至30天之间。");
        return days;
    }

    private String requireAction(String action) {
        if (action != null && action.contains("SMS")) {
            throw new BusinessException("短信通用通知通道尚未接入，请选择站内提醒或邮件。");
        }
        if (action == null || !ACTIONS.contains(action)) throw new BusinessException("不支持的执行动作，仅支持站内提醒、邮件。");
        return action;
    }

    private String requireMessage(String message) {
        if (message == null || message.isBlank() || message.trim().length() > 200) {
            throw new BusinessException("提醒文案需为1至200字。");
        }
        return message.trim();
    }

    private Integer requireFrequency(Integer frequency) {
        if (frequency == null) return 1440;
        if (!Set.of(60, 1440, 10080).contains(frequency)) {
            throw new BusinessException("提醒频率仅支持每小时、每天或每周。");
        }
        return frequency;
    }

    private Date requireEndAt(String endAt) {
        if (endAt == null || endAt.isBlank()) return null;
        try {
            Date date = java.sql.Date.valueOf(java.time.LocalDate.parse(endAt.trim()));
            if (date.before(new Date())) throw new BusinessException("结束时间不能早于当前时间。");
            return date;
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new BusinessException("结束时间格式不正确，应为yyyy-MM-dd。");
        }
    }

    private void requireEmailConfigured() {
        if (!emailService.isConfigured()) {
            throw new BusinessException("邮件通道未配置，暂不能使用邮件提醒。");
        }
    }

    private void requireCapability(TeacherUser teacher, String capability) {
        if (!hasCapability(teacher, capability)) {
            throw new BusinessException("当前身份未开通" + capability + "能力，请联系管理员。");
        }
    }

    private boolean hasCapability(TeacherUser teacher, String capability) {
        try {
            String role = "ROLE_TEACHER_" + teacher.getTeacherType();
            Actor actor = new Actor(role + ":" + teacher.getId() + ":false:",
                    teacher.getId(), role, teacher.getName(), teacher.getTeacherType(),
                    parseId(teacher.getDepartmentId()), parseId(teacher.getDivisionId()), false, Set.of());
            return capabilities.has(actor, capability);
        } catch (Exception e) {
            return false;
        }
    }

    private String requireSchedule(String scheduleConfig) {
        TeacherAutoTaskSchedule schedule = TeacherAutoTaskSchedule.parse(scheduleConfig, json);
        if (schedule.mode() == TeacherAutoTaskSchedule.Mode.ONCE) {
            OffsetDateTime occurrence = LocalDateTime.of(schedule.date(), schedule.time()).atZone(zone).toOffsetDateTime();
            if (!occurrence.isAfter(OffsetDateTime.now(zone))) {
                throw new BusinessException("一次性任务的执行时间必须晚于当前时间。");
            }
        }
        return schedule.toJson(json);
    }

    private String scheduleLabel(TeacherAutoTaskSchedule schedule) {
        return switch (schedule.mode()) {
            case MANUAL -> "确认后执行一次";
            case WINDOW -> "截止窗口内按扫描周期检查";
            case ONCE -> schedule.date() + " " + schedule.time() + " 执行一次";
            case DAILY -> "每天 " + schedule.time() + " 执行";
            case WEEKLY -> "每周" + schedule.weekdays().stream().sorted()
                    .map(this::weekdayLabel).reduce((a, b) -> a + "、" + b).orElse("")
                    + " " + schedule.time() + " 执行";
        };
    }

    private String weekdayLabel(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "一";
            case TUESDAY -> "二";
            case WEDNESDAY -> "三";
            case THURSDAY -> "四";
            case FRIDAY -> "五";
            case SATURDAY -> "六";
            case SUNDAY -> "日";
        };
    }

    private Set<String> parseAllowlist(String value) {
        if (value == null || value.isBlank()) return Set.of();
        Set<String> result = new LinkedHashSet<>();
        for (String item : value.split(",")) {
            String email = item.trim().toLowerCase(Locale.ROOT);
            if (!email.isEmpty()) result.add(email);
        }
        return Set.copyOf(result);
    }

    private boolean testRecipientAllowed(String email) {
        if (!testMode) return true;
        return email != null && recipientAllowlist.contains(email.trim().toLowerCase(Locale.ROOT));
    }

    private AutomationPolicy automationPolicy(TeacherUser teacher) {
        if (teacher == null) return AutomationPolicy.closed(false);
        try {
            return capabilities.effectiveAutomationPolicy(actorOf(teacher));
        } catch (Exception e) {
            log.warn("读取自动化策略失败 teacher={}: {}", teacher.getId(), e.getMessage());
            return AutomationPolicy.closed(hasCapability(teacher, "auto_task"));
        }
    }

    private AutomationPolicy requireManagePolicy(TeacherUser teacher) {
        featureGate.requireEnabled();
        AutomationPolicy policy = automationPolicy(teacher);
        if (!policy.canManage()) {
            throw new BusinessException("当前身份未开通自动化任务能力，请联系管理员。");
        }
        if (!policy.canNotify()) {
            throw new BusinessException("当前身份未开通自动化通知能力，请联系管理员。");
        }
        return policy;
    }

    private String policyBlockReason(TeacherUser teacher, TeacherAutoTask task) {
        try { scopeService.requireAuthorization(teacher); }
        catch (BusinessException e) { return e.getMessage(); }
        try {
            requireTrigger(task.getTriggerCondition());
            requireScheduledTime(task.getTriggerCondition(), task.getScheduleConfig());
            requireSelectedTargets(teacher, TeacherAutoTaskTargets.decode(task.getTargetScope()));
        } catch (BusinessException e) { return e.getMessage(); }
        if (!Objects.equals(teacher.getTeacherType(), task.getTeacherType())) return "教师身份已变化，请重新核对任务范围";
        AutomationPolicy policy = automationPolicy(teacher);
        if (!policy.canManage()) return "AI 能力 auto_task 已被管理员关闭";
        if (!policy.canNotify()) return "AI 能力 auto_task_notify 已被管理员关闭";
        if (!policy.allowsChannel(task.getAction())) return "当前授权不允许" + actionLabel(task.getAction());
        if (!policy.scopes().contains(TeacherAutomationScopeService.scopeOf(teacher.getTeacherType()))) return "当前授权不允许自动化使用该教师负责范围";
        return null;
    }

    private void pauseTask(TeacherAutoTask task, String reason, Date now) {
        tasks.updateStatus(task.getId(), task.getTeacherId(), STATUS_PAUSED, 0, reason, now);
    }

    private void endTask(TeacherAutoTask task, Date now) {
        tasks.updateStatus(task.getId(), task.getTeacherId(), STATUS_ENDED, 0, task.getBlockedReason(), now);
    }

    private Actor actorOf(TeacherUser teacher) {
        String role = "ROLE_TEACHER_" + teacher.getTeacherType();
        return new Actor(role + ":" + teacher.getId() + ":false:", teacher.getId(), role,
                teacher.getName(), teacher.getTeacherType(), parseId(teacher.getDepartmentId()),
                parseId(teacher.getDivisionId()), false, Set.of());
    }

    private void renewLock(String key, String token) {
        try {
            redis.execute(RENEW_LOCK, List.of(key), token, String.valueOf(TASK_LOCK_TTL.toMillis()));
        } catch (Exception e) {
            log.warn("自动化任务执行锁续期失败 key={}: {}", key, e.getMessage());
        }
    }

    private void releaseLock(String key, String token) {
        try {
            redis.execute(RELEASE_LOCK, List.of(key), token);
        } catch (Exception e) {
            log.warn("自动化任务执行锁释放失败 key={}: {}", key, e.getMessage());
        }
    }

    @PreDestroy
    void shutdownLockMaintenance() {
        lockMaintenance.shutdown();
        try {
            if (!lockMaintenance.awaitTermination(5, TimeUnit.SECONDS)) {
                lockMaintenance.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            lockMaintenance.shutdownNow();
        }
    }

    private Long parseId(String value) {
        try {
            return value == null || value.isBlank() ? null : Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String triggerLabel(String trigger) {
        return switch (Objects.toString(trigger, "")) {
            case TRIGGER_APPRAISAL -> "鉴定缺交";
            case TRIGGER_OFFER -> "Offer待确认";
            case TRIGGER_REFLECTION -> "心得未提交";
            case TRIGGER_SCHEDULED -> "定时提醒";
            default -> "待确认条件";
        };
    }

    private String actionLabel(String action) {
        return ACTION_EMAIL.equals(action) ? "邮件提醒" : ACTION_STATION.equals(action) ? "站内提醒" : "渠道待确认";
    }

    private String frequencyLabel(Integer minutes) {
        if (minutes == null) return "每天";
        return switch (minutes) {
            case 60 -> "每小时";
            case 10080 -> "每周";
            default -> "每天";
        };
    }
}
