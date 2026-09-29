package com.internship.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.agent.business.AgentActionService;
import com.internship.agent.business.AgentException;
import com.internship.agent.business.AgentIdentityService;
import com.internship.agent.business.AgentTypes.Actor;
import com.internship.anno.Log;
import com.internship.entity.Result;
import com.internship.entity.TeacherAutoTask;
import com.internship.entity.TeacherAutoTaskLog;
import com.internship.entity.TeacherAutoTaskSendLog;
import com.internship.entity.TeacherUser;
import com.internship.service.TeacherAutoTaskService;
import com.internship.service.TeacherUserService;
import com.internship.service.TeacherAutoTaskSchedule;
import com.internship.service.TeacherAutoTaskTargets;
import com.internship.utils.CurrentHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 教师自动化任务管理：模型解析预览 → 确认创建 → 启用 → 定时去重执行 → 生命周期与记录查询。
 * 数据范围限定本人创建的任务，不扩大原业务权限。
 */
@RestController
@RequestMapping("/api/teacher/auto-tasks")
@PreAuthorize("hasAnyRole('TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR')")
public class TeacherAutoTaskController {

    private final TeacherAutoTaskService autoTasks;
    private final TeacherUserService teacherUsers;
    private final ObjectMapper json;
    private final AgentActionService actions;
    private final AgentIdentityService identities;

    public TeacherAutoTaskController(TeacherAutoTaskService autoTasks, TeacherUserService teacherUsers,
                                     ObjectMapper json, AgentActionService actions, AgentIdentityService identities) {
        this.autoTasks = autoTasks;
        this.teacherUsers = teacherUsers;
        this.json = json;
        this.actions = actions;
        this.identities = identities;
    }

    private TeacherUser currentTeacher() {
        return teacherUsers.findById(CurrentHolder.getUserId());
    }

    /** 自然语言生成任务预览（模型草稿+白名单校验，不落库，确认后调用创建接口）。 */
    @PostMapping("/preview")
    public Result preview(@RequestHeader("Authorization") String auth, @RequestBody Map<String, String> body) {
        Actor actor = actor(auth);
        Map<String, Object> draft = new LinkedHashMap<>(autoTasks.preview(body == null ? null : body.get("message"), currentTeacher()));
        if (Boolean.TRUE.equals(draft.get("needsConfirm")) && !Boolean.FALSE.equals(draft.get("channelAvailable"))
                && (!(draft.get("questions") instanceof List<?> questions) || questions.isEmpty())) {
            var action = actions.preview(actor, "TEACHER_TASK_CREATE", "teacher:" + actor.id(), parameters(draft), null);
            draft.put("actionId", action.id());
        }
        return Result.success(draft);
    }

    @PostMapping("/confirmation-preview")
    public Result confirmationPreview(@RequestHeader("Authorization") String auth, @RequestBody Map<String, Object> body) {
        Actor actor = actor(auth);
        requireAvailable();
        autoTasks.validateDraft(currentTeacher(), str(body, "triggerCondition"), intVal(body, "triggerDays"),
                str(body, "action"), str(body, "messageTemplate"), intVal(body, "frequencyMinutes"),
                str(body, "endAt"), schedule(body), TeacherAutoTaskTargets.ids(body.get("targetStudentIds")));
        return Result.success(actions.preview(actor, "TEACHER_TASK_CREATE", "teacher:" + actor.id(), parameters(body), null));
    }

    @PostMapping
    @Log(module = "TEACHING_MANAGEMENT", operationType = "ADD", description = "创建自动化任务")
    public Result create(@RequestHeader("Authorization") String auth, @RequestBody Map<String, Object> body) {
        Actor actor = actor(auth);
        confirmed(body);
        return Result.success(actions.execute(actor, str(body, "actionId"), "TEACHER_TASK_CREATE", parameters(body), () -> {
            actor(auth);
            TeacherAutoTask task = autoTasks.create(currentTeacher(),
                str(body, "name"), str(body, "triggerCondition"), intVal(body, "triggerDays"),
                str(body, "action"), str(body, "messageTemplate"),
                intVal(body, "frequencyMinutes"), str(body, "endAt"), schedule(body), TeacherAutoTaskTargets.ids(body.get("targetStudentIds")));
            return json.convertValue(task, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        }));
    }

    @PutMapping("/{id}")
    @Log(module = "TEACHING_MANAGEMENT", operationType = "UPDATE", description = "修改自动化任务")
    public Result update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object suppliedVersion = body == null ? null : body.get("version");
        if (!(suppliedVersion instanceof Number version) || version.longValue() < 0) throw new AgentException("VERSION_REQUIRED", "请重新打开任务后修改，缺少原版本无法安全保存。", 409);
        return Result.success(autoTasks.update(currentTeacher(), id,
                str(body, "name"), str(body, "triggerCondition"), intVal(body, "triggerDays"),
                str(body, "action"), str(body, "messageTemplate"),
                intVal(body, "frequencyMinutes"), str(body, "endAt"), schedule(body), version.longValue(),
                body.containsKey("targetStudentIds") ? TeacherAutoTaskTargets.ids(body.get("targetStudentIds")) : null));
    }

    @GetMapping
    public Result list() {
        return Result.success(autoTasks.list(currentTeacher()));
    }

    /** 页面与后端创建/启用/执行使用同一套授权结果。 */
    @GetMapping("/capability")
    public Result capability() {
        return Result.success(autoTasks.capability(currentTeacher()));
    }

    @GetMapping("/{id}")
    public Result get(@PathVariable Long id) {
        return Result.success(autoTasks.get(currentTeacher(), id));
    }

    @PostMapping("/{id}/enable/preview")
    public Result enablePreview(@RequestHeader("Authorization") String auth, @PathVariable Long id) {
        Actor actor = actor(auth);
        requireAvailable();
        TeacherAutoTask task = autoTasks.get(currentTeacher(), id);
        Map<String,Object> impact = autoTasks.enableImpact(currentTeacher(),task);
        var action = actions.preview(actor, "TEACHER_TASK_ENABLE", "teacher-task:" + id,
                Map.of("taskId", id.toString(), "enabled", true), Long.toString(task.getVersion()));
        Map<String,Object> result = json.convertValue(action,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>() {});
        result.putAll(impact);
        return Result.success(result);
    }

    @PostMapping("/{id}/enable")
    @Log(module = "TEACHING_MANAGEMENT", operationType = "UPDATE", description = "启用自动化任务")
    public Result enable(@RequestHeader("Authorization") String auth, @PathVariable Long id, @RequestBody Map<String, Object> body) {
        Actor actor = actor(auth);
        confirmed(body);
        String actionId = str(body, "actionId");
        var preview = actions.get(actor, actionId);
        return Result.success(actions.execute(actor, actionId, "TEACHER_TASK_ENABLE",
                Map.of("taskId", id.toString(), "enabled", true), () -> {
                    actor(auth);
                    return json.convertValue(autoTasks.enableConfirmed(currentTeacher(), id, preview.expectedVersion()),
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                }));
    }

    @PostMapping("/{id}/disable")
    @Log(module = "TEACHING_MANAGEMENT", operationType = "UPDATE", description = "暂停自动化任务")
    public Result disable(@PathVariable Long id) {
        return Result.success(autoTasks.setEnabled(currentTeacher(), id, false));
    }

    @PostMapping("/{id}/end")
    @Log(module = "TEACHING_MANAGEMENT", operationType = "UPDATE", description = "结束自动化任务")
    public Result end(@PathVariable Long id) {
        return Result.success(autoTasks.end(currentTeacher(), id));
    }

    @DeleteMapping("/{id}")
    @Log(module = "TEACHING_MANAGEMENT", operationType = "DELETE", description = "删除自动化任务")
    public Result delete(@PathVariable Long id) {
        autoTasks.delete(currentTeacher(), id);
        return Result.success();
    }

    /** 手动执行一次（与定时执行同一套状态重查、能力校验和冷却去重）。 */
    @PostMapping("/{id}/run")
    @Log(module = "TEACHING_MANAGEMENT", operationType = "UPDATE", description = "手动执行自动化任务")
    public Result run(@RequestHeader("Authorization") String auth, @PathVariable Long id, @RequestBody Map<String, Object> body) {
        actor(auth);
        return Result.success(autoTasks.runNow(currentTeacher(), id, str(body, "clientRequestId")));
    }

    @GetMapping("/{id}/runs/{clientRequestId}")
    public Result runStatus(@PathVariable Long id, @PathVariable String clientRequestId) {
        return Result.success(autoTasks.findRun(currentTeacher(), id, clientRequestId));
    }

    /** 汇总执行记录（每次触发一条）。 */
    @GetMapping("/{id}/logs")
    public Result logs(@PathVariable Long id, @RequestParam(defaultValue = "20") int limit) {
        List<TeacherAutoTaskLog> records = autoTasks.runLogs(currentTeacher(), id, limit);
        return Result.success(records);
    }

    /** 单学生发送明细（冷却去重与失败核对）。 */
    @GetMapping("/{id}/sends")
    public Result sends(@PathVariable Long id, @RequestParam(defaultValue = "50") int limit) {
        List<TeacherAutoTaskSendLog> records = autoTasks.sendLogs(currentTeacher(), id, limit);
        return Result.success(records);
    }

    private static String str(Map<String, Object> body, String key) {
        if (body == null || body.get(key) == null) return null;
        String value = String.valueOf(body.get(key)).trim();
        return value.isEmpty() || "null".equalsIgnoreCase(value) ? null : value;
    }

    private static Integer intVal(Map<String, Object> body, String key) {
        if (body == null) return null;
        Object value = body.get(key);
        if (value instanceof Number number) {
            int v = number.intValue();
            return v > 0 ? v : null;
        }
        if (value instanceof String s && s.matches("\\d+")) {
            try {
                int v = Integer.parseInt(s);
                return v > 0 ? v : null;
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    @GetMapping("/{id}/runs/pending")
    public Result pendingRun(@PathVariable Long id) {
        return Result.success(autoTasks.pendingRun(currentTeacher(),id));
    }

    private Actor actor(String authorization) {
        Actor actor = identities.resolve(authorization);
        AgentIdentityService.requireTeacher(actor);
        if (actor.readOnly()) throw AgentIdentityService.forbidden();
        return actor;
    }

    private void requireAvailable() {
        Map<String, Object> capability = autoTasks.capability(currentTeacher());
        if (!Boolean.TRUE.equals(capability.get("canManage")) || !Boolean.TRUE.equals(capability.get("canExecute"))) {
            throw new AgentException("FORBIDDEN", "当前授权不允许启用自动跟进，请联系管理员。", 403);
        }
    }

    private static void confirmed(Map<String, Object> body) {
        if (body == null || !Boolean.TRUE.equals(body.get("confirmed")) || str(body, "actionId") == null) {
            throw new AgentException("CONFIRMATION_REQUIRED", "请先核对操作预览，再明确确认。", 400);
        }
    }

    private Map<String, Object> parameters(Map<String, Object> body) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        for (String key : List.of("name", "triggerCondition", "action", "messageTemplate", "endAt")) parameters.put(key, str(body, key));
        parameters.put("triggerDays", intVal(body, "triggerDays"));
        parameters.put("frequencyMinutes", intVal(body, "frequencyMinutes"));
        parameters.put("targetStudentIds", TeacherAutoTaskTargets.ids(body == null ? null : body.get("targetStudentIds")));
        parameters.put("scheduleConfig", TeacherAutoTaskSchedule.normalize(schedule(body), json));
        return parameters;
    }

    private String schedule(Map<String, Object> body) {
        if (body == null || body.get("scheduleConfig") == null) return null;
        try {
            Object value = body.get("scheduleConfig");
            return value instanceof String text ? text : json.writeValueAsString(value);
        } catch (Exception e) {
            throw new com.internship.exception.BusinessException("调度配置格式不正确。");
        }
    }
}
