package com.internship.service.impl;

import com.internship.entity.StudentReminder;
import com.internship.mapper.StudentReminderMapper;
import com.internship.service.ReminderService;
import com.internship.websocket.AnnouncementWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReminderServiceImpl implements ReminderService {

    @Autowired
    private StudentReminderMapper reminderMapper;

    @Autowired
    private AnnouncementWebSocketHandler webSocketHandler;

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
        webSocketHandler.sendReminderToStudent(studentId, reminderData);
    }

    @Override
    public List<StudentReminder> getPendingReminders(Long studentId) {
        return reminderMapper.selectPendingByStudentId(studentId);
    }

    @Override
    public void confirmReminder(Long reminderId) {
        StudentReminder reminder = reminderMapper.selectById(reminderId);
        if (reminder != null) {
            reminder.setIsConfirmed(1);
            reminder.setConfirmTime(LocalDateTime.now());
            reminderMapper.updateById(reminder);
        }
    }
}