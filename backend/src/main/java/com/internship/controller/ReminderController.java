package com.internship.controller;

import com.internship.anno.Log;
import com.internship.entity.Result;
import com.internship.entity.StudentReminder;
import com.internship.service.ReminderService;
import com.internship.service.TeacherAutomationScopeService;
import com.internship.service.TeacherUserService;
import com.internship.entity.TeacherUser;
import com.internship.exception.BusinessException;
import com.internship.utils.CurrentHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api")
public class ReminderController {

    @Autowired
    private ReminderService reminderService;

    @Autowired
    private TeacherUserService teacherUserService;

    @Autowired
    private TeacherAutomationScopeService teacherScope;

    // 教师发送邮件提醒（学生状态监控页：单发；发送到学生绑定邮箱）
    @PostMapping("/teacher/reminder/send")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR')")
    @Log(operationType = "CREATE", module = "STUDENT_INTERNSHIP", description = "发送邮件提醒", riskLevel = "MEDIUM")
    public Result sendReminder(@RequestParam Long studentId,
                               @RequestParam(required = false) Long teacherId,
                               @RequestParam String content) {
        TeacherUser teacher = requireCurrentTeacher();
        requireRecipient(teacher, studentId);
        ReminderService.EmailReminderResult result =
                reminderService.sendEmailReminder(studentId, teacher.getId(), content);
        if (!result.sent()) {
            return Result.error(result.message());
        }
        return Result.success(result.message(), null);
    }

    /** 批量请求体：一键邮件提醒（服务端逐条发送并节流）。 */
    public record BatchSendRequest(List<Long> studentIds, Long teacherId, String content) {}

    // 教师批量发送邮件提醒（学生状态监控页：一键提醒；服务端节流并返回失败明细）
    @PostMapping("/teacher/reminder/send-batch")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR')")
    @Log(operationType = "CREATE", module = "STUDENT_INTERNSHIP", description = "批量发送邮件提醒", riskLevel = "MEDIUM")
    public Result sendBatchReminder(@RequestBody BatchSendRequest request) {
        if (request == null || request.studentIds() == null || request.studentIds().isEmpty()) {
            return Result.error("请选择需要提醒的学生");
        }
        if (request.content() == null || request.content().isBlank()) {
            return Result.error("请输入提醒内容");
        }
        if (request.studentIds().size() > ReminderService.BATCH_MAX) {
            return Result.error("单次最多提醒 " + ReminderService.BATCH_MAX + " 名学生，请缩小筛选范围");
        }
        TeacherUser teacher = requireCurrentTeacher();
        Set<Long> allowedStudentIds;
        try {
            List<Long> scopedIds = teacherScope.studentIds(teacher);
            allowedStudentIds = scopedIds == null ? Set.of() : Set.copyOf(scopedIds);
        } catch (BusinessException e) {
            throw new AccessDeniedException("当前教师无权发送提醒", e);
        }
        if (request.studentIds().stream().anyMatch(id -> id == null || !allowedStudentIds.contains(id))) {
            throw new AccessDeniedException("所选学生不在当前教师负责范围内");
        }
        ReminderService.BatchEmailReminderResult result = reminderService.sendEmailReminderBatch(
                teacher.getId(), request.studentIds(), request.content());
        return Result.success("批量邮件提醒已处理", result);
    }

    // 学生获取未确认提醒
    @GetMapping("/student/reminder/pending")
    @PreAuthorize("hasRole('STUDENT')")
    public Result getPendingReminders(@RequestParam(required = false) String studentId) {
        List<StudentReminder> reminders = reminderService.getPendingReminders(requireCurrentStudentId());
        return Result.success(reminders);
    }

    // 学生确认提醒
    @PostMapping("/student/reminder/confirm/{id}")
    @PreAuthorize("hasRole('STUDENT')")
    @Log(operationType = "UPDATE", module = "STUDENT_INTERNSHIP", description = "确认实习提醒", riskLevel = "LOW")
    public Result confirmReminder(@PathVariable Long id) {
        requireCurrentStudentId();
        reminderService.confirmReminder(id);
        return Result.success();
    }

    private TeacherUser requireCurrentTeacher() {
        Long teacherId = CurrentHolder.getUserId();
        String role = CurrentHolder.getUserRole();
        if (teacherId == null || !("ROLE_TEACHER".equals(role)
                || "ROLE_TEACHER_COLLEGE".equals(role)
                || "ROLE_TEACHER_DEPARTMENT".equals(role)
                || "ROLE_TEACHER_COUNSELOR".equals(role))) {
            throw new AccessDeniedException("当前身份不是教师");
        }
        TeacherUser teacher = teacherUserService.findById(teacherId);
        if (teacher == null || !teacherId.equals(teacher.getId())
                || (!"ROLE_TEACHER".equals(role)
                        && !role.equals("ROLE_TEACHER_" + teacher.getTeacherType()))) {
            throw new AccessDeniedException("当前教师身份已失效");
        }
        return teacher;
    }

    private void requireRecipient(TeacherUser teacher, Long studentId) {
        try {
            if (studentId == null || !teacherScope.contains(teacher, studentId)) {
                throw new AccessDeniedException("学生不在当前教师负责范围内");
            }
        } catch (BusinessException e) {
            throw new AccessDeniedException("当前教师无权发送提醒", e);
        }
    }

    private Long requireCurrentStudentId() {
        Long studentId = CurrentHolder.getUserId();
        if (studentId == null || !"ROLE_STUDENT".equals(CurrentHolder.getUserRole())) {
            throw new AccessDeniedException("当前身份不是学生");
        }
        return studentId;
    }
}
