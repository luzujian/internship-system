package com.internship.service;

import com.internship.entity.StudentReminder;
import java.util.List;

public interface ReminderService {
    void sendReminder(Long studentId, Long teacherId, String content);

    List<StudentReminder> getPendingReminders(Long studentId);

    void confirmReminder(Long reminderId);
}