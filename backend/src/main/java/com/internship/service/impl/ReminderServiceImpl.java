package com.internship.service.impl;

import com.internship.entity.StudentReminder;
import com.internship.entity.StudentUser;
import com.internship.entity.TeacherUser;
import com.internship.exception.BusinessException;
import com.internship.mapper.StudentReminderMapper;
import com.internship.mapper.StudentUserMapper;
import com.internship.mapper.TeacherUserMapper;
import com.internship.service.EmailService;
import com.internship.service.ReminderService;
import com.internship.service.TeacherAutomationScopeService;
import com.internship.utils.CurrentHolder;
import com.internship.websocket.AnnouncementWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ReminderServiceImpl implements ReminderService {

    private static final String EMAIL_SUBJECT = "实习事项提醒";
    private static final String OUT_OF_SCOPE_MESSAGE = "当前教师无权提醒该学生";

    @Autowired
    private StudentReminderMapper reminderMapper;

    @Autowired
    private AnnouncementWebSocketHandler webSocketHandler;

    @Autowired
    private StudentUserMapper studentUserMapper;

    @Autowired
    private TeacherUserMapper teacherUserMapper;

    @Autowired
    private EmailService emailService;

    @Autowired
    private TeacherAutomationScopeService teacherScope;

    @Override
    public void sendReminder(Long studentId, Long teacherId, String content) {
        StudentReminder reminder = new StudentReminder();
        reminder.setStudentId(studentId);
        reminder.setTeacherId(teacherId);
        reminder.setContent(content);
        reminder.setIsConfirmed(0);
        reminder.setCreateTime(LocalDateTime.now());
        reminderMapper.insert(reminder);

        // 通过 WebSocket 实时推送提醒给学生
        Map<String, Object> reminderData = new HashMap<>();
        reminderData.put("id", reminder.getId());
        reminderData.put("content", content);
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void afterCommit() {
                            try { webSocketHandler.sendReminderToStudent(studentId, reminderData); }
                            catch (RuntimeException ignored) { /* The durable reminder remains available after reconnect. */ }
                        }
                    });
        } else {
            webSocketHandler.sendReminderToStudent(studentId, reminderData);
        }
    }

    @Override
    public EmailReminderResult sendEmailReminder(Long studentId, Long teacherId, String content) {
        if (studentId == null || teacherId == null) {
            return new EmailReminderResult(false, OUT_OF_SCOPE_MESSAGE);
        }
        TeacherUser teacher = teacherUserMapper.findById(teacherId);
        try {
            if (teacher == null || !teacherScope.contains(teacher, studentId)) {
                return new EmailReminderResult(false, OUT_OF_SCOPE_MESSAGE);
            }
        } catch (BusinessException e) {
            return new EmailReminderResult(false, OUT_OF_SCOPE_MESSAGE);
        }
        StudentUser student = studentUserMapper.findById(studentId);
        if (student == null || !Integer.valueOf(1).equals(student.getStatus())) {
            return new EmailReminderResult(false, "学生不存在或已停用，无法发送邮件提醒");
        }
        String email = student.getEmail() == null ? "" : student.getEmail().trim();
        if (email.isEmpty()) {
            return new EmailReminderResult(false, "该学生未绑定邮箱，无法发送邮件提醒");
        }
        boolean sent = emailService.sendEmail(email, EMAIL_SUBJECT, buildBody(content, teacherId), senderDisplayName(teacherId));
        if (!sent) {
            return new EmailReminderResult(false, "邮件发送失败，请稍后重试");
        }
        return new EmailReminderResult(true, "邮件提醒已发送");
    }

    @Override
    public BatchEmailReminderResult sendEmailReminderBatch(Long teacherId, List<Long> studentIds, String content) {
        List<Long> ids = new ArrayList<>();
        if (studentIds != null) {
            for (Long id : studentIds) {
                if (id != null && !ids.contains(id)) {
                    ids.add(id);
                }
                if (ids.size() >= BATCH_MAX) {
                    break;
                }
            }
        }
        int success = 0;
        List<EmailReminderFailure> failures = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            Long studentId = ids.get(i);
            EmailReminderResult result = sendEmailReminder(studentId, teacherId, content);
            if (result.sent()) {
                success++;
            } else {
                String name = OUT_OF_SCOPE_MESSAGE.equals(result.message()) ? null : resolveName(studentId);
                failures.add(new EmailReminderFailure(studentId, name, result.message()));
            }
            // 逐条间隔 300ms，降低企业邮箱限流风险（最后一条后不再等待）
            if (i < ids.size() - 1) {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return new BatchEmailReminderResult(ids.size(), success, failures.size(), failures);
    }

    private String resolveName(Long studentId) {
        try {
            StudentUser student = studentUserMapper.findById(studentId);
            return student == null ? null : student.getName();
        } catch (Exception e) {
            return null;
        }
    }

    private String senderDisplayName(Long teacherId) {
        try {
            if (teacherId == null || teacherId <= 0) return "DeepIntern";
            TeacherUser teacher = teacherUserMapper.findById(teacherId);
            if (teacher == null) return "DeepIntern";
            return EmailService.senderDisplayName(teacher.getTeacherType(), teacher.getName());
        } catch (Exception ignored) {
            return "DeepIntern";
        }
    }

    /** 正文 = 提醒内容 + 教师署名；教师缺失时回退为系统署名。 */
    private String buildBody(String content, Long teacherId) {
        String teacherName = null;
        if (teacherId != null && teacherId > 0) {
            TeacherUser teacher = teacherUserMapper.findById(teacherId);
            if (teacher != null && teacher.getName() != null && !teacher.getName().isBlank()) {
                teacherName = teacher.getName().trim();
            }
        }
        String signature = teacherName == null
                ? "—— DeepIntern 实习管理系统"
                : "—— " + teacherName + "（DeepIntern 实习管理系统）";
        return content + "\n\n" + signature;
    }

    @Override
    public List<StudentReminder> getPendingReminders(Long studentId) {
        return reminderMapper.selectPendingByStudentId(studentId);
    }

    @Override
    public void confirmReminder(Long reminderId) {
        Long studentId = CurrentHolder.getUserId();
        if (studentId == null || !"ROLE_STUDENT".equals(CurrentHolder.getUserRole())) {
            throw new AccessDeniedException("当前身份不是学生");
        }
        StudentReminder reminder = reminderMapper.selectById(reminderId);
        if (reminder != null) {
            if (!studentId.equals(reminder.getStudentId())) {
                throw new AccessDeniedException("无权确认其他学生的提醒");
            }
            reminder.setIsConfirmed(1);
            reminder.setConfirmTime(LocalDateTime.now());
            reminderMapper.updateById(reminder);
        }
    }
}
