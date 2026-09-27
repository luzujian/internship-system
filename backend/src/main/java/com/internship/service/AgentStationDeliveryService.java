package com.internship.service;

import com.internship.mapper.TeacherAutoTaskSendLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Date;

@Service
public class AgentStationDeliveryService {
    private final ReminderService reminders;
    private final TeacherAutoTaskSendLogMapper sends;
    private final AgentNotificationGuardService guard;
    public AgentStationDeliveryService(ReminderService reminders, TeacherAutoTaskSendLogMapper sends, AgentNotificationGuardService guard) {
        this.reminders=reminders; this.sends=sends; this.guard=guard;
    }
    @Transactional
    public void send(Long studentId, Long teacherId, String message, Long sendLogId, AgentNotificationGuardService.Claim claim) {
        reminders.sendReminder(studentId, teacherId, message);
        if (sends.updateStatus(sendLogId, "SENT", "站内提醒已保存。", new Date()) != 1)
            throw new IllegalStateException("站内提醒回执不存在。");
        guard.finish(claim, "SENT", sendLogId);
    }
}
