package com.gdmu.service.impl;

import com.gdmu.entity.StudentReminder;
import com.gdmu.mapper.StudentReminderMapper;
import com.gdmu.service.ReminderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReminderServiceImpl implements ReminderService {

    @Autowired
    private StudentReminderMapper reminderMapper;

    @Override
    public void sendReminder(Long studentId, Long teacherId, String content) {
        StudentReminder reminder = new StudentReminder();
        reminder.setStudentId(studentId);
        reminder.setTeacherId(teacherId);
        reminder.setContent(content);
        reminder.setIsConfirmed(0);
        reminder.setCreateTime(LocalDateTime.now());
        reminderMapper.insert(reminder);
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