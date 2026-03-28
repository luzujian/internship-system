package com.gdmu.service;

import com.gdmu.entity.StudentReminder;
import java.util.List;

public interface ReminderService {
    void sendReminder(Long studentId, Long teacherId, String content);

    List<StudentReminder> getPendingReminders(Long studentId);

    void confirmReminder(Long reminderId);
}