package com.internship.service;

import com.internship.entity.StudentReminder;
import java.util.List;

public interface ReminderService {
    void sendReminder(Long studentId, Long teacherId, String content);

    /**
     * 教师手动提醒（学生状态监控页）：改为向学生绑定邮箱发送邮件，不回退站内提醒。
     * sent=false 时 message 为可展示的明确原因（学生不可用/未绑定邮箱/发送失败）。
     */
    EmailReminderResult sendEmailReminder(Long studentId, Long teacherId, String content);

    /**
     * 一键邮件提醒（批量）：服务端逐条发送并节流，返回汇总与失败明细。
     * 入参去重；单次上限 {@link #BATCH_MAX}。
     */
    BatchEmailReminderResult sendEmailReminderBatch(Long teacherId, List<Long> studentIds, String content);

    List<StudentReminder> getPendingReminders(Long studentId);

    void confirmReminder(Long reminderId);

    record EmailReminderResult(boolean sent, String message) {}

    record EmailReminderFailure(Long studentId, String name, String reason) {}

    record BatchEmailReminderResult(int total, int success, int fail, List<EmailReminderFailure> failures) {}

    /** 单次批量上限 */
    int BATCH_MAX = 500;
}
