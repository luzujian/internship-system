package com.internship.agent.business;

import com.internship.entity.StudentUser;
import com.internship.entity.TeacherUser;
import com.internship.exception.BusinessException;
import com.internship.mapper.StudentUserMapper;
import com.internship.mapper.TeacherUserMapper;
import com.internship.service.ReminderService;
import com.internship.service.TeacherAutomationScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import static com.internship.agent.business.AgentIdentityService.*;
import static com.internship.agent.business.AgentTypes.*;

/**
 * 教师单次提醒：AI 只做核验与拟稿，真正发送由教师在确认卡上点确认后执行。
 * 发送结果写入不可变回执；外部发送（邮件 / WebSocket）不在数据库事务内进行。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TeacherReminderExecuteService {
    public static final String ACTION = "TEACHER_REMINDER_SEND";
    public static final Set<String> CHANNELS = AgentCapabilityService.AUTO_TASK_CHANNELS;
    private static final int MAX_CONTENT = 2000;
    private static final int MAX_QUERY = 60;

    public record PreviewRequest(String studentQuery, String channel, String content) {}
    public record SendRequest(String actionId, Map<String, String> parameters, String clientRequestId, boolean confirmed) {}

    private final TeacherUserMapper teachers;
    private final StudentUserMapper students;
    private final TeacherAutomationScopeService scopes;
    private final ReminderService reminders;
    private final AgentActionService actions;
    private final AgentFeatureGate featureGate;
    private final AgentCapabilityService capabilities;

    /** 只读：把学号或姓名解析为负责范围内的唯一学生，并生成待确认的提醒草稿。不发送任何消息。 */
    public ToolResult draft(Actor actor, String studentQuery, String content) {
        return draft(actor, studentQuery, content, null);
    }

    public ToolResult draft(Actor actor, String studentQuery, String content, String channel) {
        TeacherUser teacher = teacher(actor);
        String query = normalizedQuery(studentQuery);
        if (query == null) throw new AgentException("INVALID_INPUT", "请说明要提醒的学生，用学号或姓名。", 400);
        StudentUser student = resolveOne(teacher, query);
        String normalized = normalizedContent(content);
        Map<String, Object> row = studentRow(student, normalized);
        if (channel != null && !channel.isBlank()) row.put("channel", normalizedChannel(channel));
        String warning = normalized == null
                ? "已按负责范围核验该学生。提醒内容还不完整，请补充后由教师确认发送。"
                : "AI 不会直接发送。内容确认无误后，教师在本卡片上点确认才会真正发出。";
        return AgentReadTools.result("teacher_reminder_draft", "学生提醒确认", actor, List.of(row), 1, row,
                List.of(new Link("查看学生", "OPEN_STUDENT_TRACKING", String.valueOf(student.getId()), Map.of())),
                List.of(warning));
    }

    /** 生成确认卡：核验学生归属与渠道，返回不可变的待确认参数。 */
    public AgentActionService.ActionView preview(Actor actor, PreviewRequest request) {
        TeacherUser teacher = authorizeSend(actor);
        if (request == null) throw new AgentException("INVALID_INPUT", "请说明要提醒的学生、渠道与内容。", 400);
        StudentUser student = resolveOne(teacher, normalizedQuery(request.studentQuery()));
        String channel = normalizedChannel(request.channel());
        String content = requiredContent(request.content());
        return actions.preview(actor, ACTION, "student:" + student.getId(),
                new LinkedHashMap<>(parameters(student, channel, content)), null);
    }

    /** 确认后发送：执行前重新核验身份、负责范围与渠道，结果写入不可变回执。 */
    public Map<String, Object> send(Actor actor, SendRequest request) {
        authorizeSend(actor);
        if (request == null || !request.confirmed())
            throw new AgentException("CONFIRM_REQUIRED", "请先核对提醒内容，再确认发送。", 400);
        if (request.actionId() == null)
            throw new AgentException("PREVIEW_REQUIRED", "确认卡已失效，请重新核对提醒内容。", 400);
        Map<String, String> parameters = request.parameters();
        if (parameters == null || parameters.isEmpty())
            throw new AgentException("PREVIEW_REQUIRED", "确认卡已失效，请重新核对提醒内容。", 400);
        return actions.executeExternal(actor, request.actionId(), ACTION, new LinkedHashMap<>(parameters), () -> deliver(actor, parameters));
    }

    private Map<String, Object> deliver(Actor actor, Map<String, String> parameters) {
        // The feature switch, notification grant and teacher scope may change after preview.
        TeacherUser teacher = authorizeSend(actor);
        Long studentId = longId(parameters.get("studentId"));
        if (studentId == null || !inScope(teacher, studentId))
            throw new AgentException("SCOPE_CHANGED", "该学生已不在你的负责范围内，本次提醒没有发送。", 403);
        String channel = normalizedChannel(parameters.get("channel"));
        String content = requiredContent(parameters.get("content"));
        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("studentId", String.valueOf(studentId));
        receipt.put("studentName", value(parameters.get("studentName")));
        receipt.put("studentNo", value(parameters.get("studentNo")));
        receipt.put("channel", channel);
        receipt.put("content", content);
        receipt.put("sentAt", OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).toString());
        if ("EMAIL".equals(channel)) {
            ReminderService.EmailReminderResult result = reminders.sendEmailReminder(studentId, actor.id(), content);
            if (result == null || !result.sent())
                throw new AgentException("DELIVERY_FAILED",
                        result == null ? "邮件发送失败，请稍后重试。" : result.message(), 400);
            receipt.put("message", result.message());
        } else {
            reminders.sendReminder(studentId, actor.id(), content);
            receipt.put("message", "站内提醒已发送");
        }
        return receipt;
    }

    private Map<String, Object> studentRow(StudentUser student, String content) {
        Map<String, Object> row = AgentReadTools.row(
                "id", String.valueOf(student.getId()),
                "title", value(student.getName()),
                "studentNo", value(student.getStudentUserId()),
                "className", value(student.getClassName()),
                "emailMasked", maskedEmail(student.getEmail()),
                "emailAvailable", String.valueOf(!value(student.getEmail()).isEmpty()),
                "phoneMasked", maskedPhone(student.getPhone()),
                "phoneAvailable", String.valueOf(!value(student.getPhone()).isEmpty()),
                "content", content == null ? "" : content);
        return row;
    }

    private Map<String, String> parameters(StudentUser student, String channel, String content) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("studentId", String.valueOf(student.getId()));
        parameters.put("studentNo", value(student.getStudentUserId()));
        parameters.put("studentName", value(student.getName()));
        parameters.put("className", value(student.getClassName()));
        parameters.put("channel", channel);
        parameters.put("content", content);
        parameters.put("emailAvailable", String.valueOf(!value(student.getEmail()).isEmpty()));
        return parameters;
    }

    private TeacherUser teacher(Actor actor) {
        requireTeacher(actor);
        TeacherUser teacher = teachers.findById(actor.id());
        if (teacher == null || !Integer.valueOf(1).equals(teacher.getStatus()))
            throw new AgentException("AUTH_REQUIRED", "教师账号不可用，请重新登录。", 401);
        if (!Objects.equals(teacher.getTeacherType(), actor.teacherType()))
            throw new AgentException("SCOPE_CHANGED", "教师身份已变化，请重新打开页面。", 403);
        return teacher;
    }

    private TeacherUser authorizeSend(Actor actor) {
        featureGate.requireEnabled();
        if (actor.readOnly())
            throw new AgentException("READ_ONLY_FORBIDDEN", "只读身份不可发送提醒。", 403);
        TeacherUser teacher = teacher(actor);
        if (!capabilities.has(actor, "auto_task_notify"))
            throw new AgentException("CAPABILITY_DISABLED", "当前身份未开通提醒发送能力。", 403);
        return teacher;
    }

    private StudentUser resolveOne(TeacherUser teacher, String query) {
        if (query == null) throw new AgentException("INVALID_INPUT", "请说明要提醒的学生，用学号或姓名。", 400);
        List<StudentUser> matches;
        try {
            matches = scopes.matchStudents(teacher, query);
        } catch (BusinessException e) {
            throw new AgentException("FORBIDDEN", e.getMessage(), 403);
        }
        if (matches == null || matches.isEmpty())
            throw new AgentException("OBJECT_UNAVAILABLE", "该学生不在你负责的范围内，或学号与姓名不匹配。", 404);
        if (matches.size() > 1)
            throw new AgentException("AMBIGUOUS_STUDENT", "匹配到多名学生，请用完整学号或姓名确认：" + names(matches) + "。", 400);
        return matches.getFirst();
    }

    private boolean inScope(TeacherUser teacher, Long studentId) {
        try {
            return scopes.contains(teacher, studentId);
        } catch (BusinessException e) {
            throw new AgentException("FORBIDDEN", e.getMessage(), 403);
        }
    }

    private static String names(List<StudentUser> matches) {
        return matches.stream().limit(5)
                .map(student -> value(student.getName()) + "（" + value(student.getStudentUserId()) + "）")
                .reduce((left, right) -> left + "、" + right).orElse("");
    }

    private static String normalizedQuery(String query) {
        String value = value(query);
        if (value.isEmpty()) return null;
        if (value.length() > MAX_QUERY) throw new AgentException("INVALID_INPUT", "学号或姓名过长，请重新确认。", 400);
        return value;
    }

    private static String normalizedContent(String content) {
        String value = value(content);
        return value.isEmpty() ? null : value;
    }

    private static String requiredContent(String content) {
        String value = normalizedContent(content);
        if (value == null) throw new AgentException("INVALID_INPUT", "请填写要发送的提醒内容。", 400);
        if (value.length() > MAX_CONTENT) throw new AgentException("INVALID_INPUT", "提醒内容最多2000字。", 400);
        return value;
    }

    private static String normalizedChannel(String channel) {
        String value = value(channel).toUpperCase(Locale.ROOT);
        if (value.isEmpty()) return "EMAIL";
        if (!CHANNELS.contains(value)) throw new AgentException("INVALID_INPUT", "只支持邮件或站内提醒。", 400);
        return value;
    }

    private static String maskedPhone(String phone) {
        String value = value(phone);
        if (value.length() < 7) return "";
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }

    private static String maskedEmail(String email) {
        String value = value(email);
        int at = value.indexOf('@');
        if (at <= 0) return "";
        String local = value.substring(0, at);
        String domain = value.substring(at);
        return (local.length() <= 1 ? local : local.substring(0, 1) + "***") + domain;
    }

    private static Long longId(String value) {
        String text = value(value);
        if (!text.matches("[1-9][0-9]{0,17}")) return null;
        try { return Long.valueOf(text); } catch (NumberFormatException e) { return null; }
    }

    private static String value(String value) { return value == null ? "" : value.trim(); }
}
