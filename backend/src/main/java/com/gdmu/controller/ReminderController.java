package com.gdmu.controller;

import com.gdmu.entity.Result;
import com.gdmu.entity.StudentReminder;
import com.gdmu.entity.User;
import com.gdmu.service.ReminderService;
import com.gdmu.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ReminderController {

    @Autowired
    private ReminderService reminderService;

    @Autowired
    private UserService userService;

    /**
     * 解析学生ID，支持两种格式：
     * 1. 纯数字字符串如 "1" -> 直接转换为 Long
     * 2. 学号字符串如 "s001" -> 通过用户服务查找对应的 ID
     */
    private Long parseStudentId(String studentId) {
        if (studentId == null || studentId.isBlank()) {
            throw new IllegalArgumentException("studentId不能为空");
        }
        try {
            // 尝试直接解析为数字
            return Long.parseLong(studentId);
        } catch (NumberFormatException e) {
            // 如果是学号格式，通过用户服务查找
            User user = userService.findByUsername(studentId);
            if (user != null) {
                return user.getId();
            }
            throw new IllegalArgumentException("无效的学生ID: " + studentId);
        }
    }

    // 教师发送提醒
    @PostMapping("/teacher/reminder/send")
    public Result sendReminder(@RequestParam Long studentId,
                               @RequestParam Long teacherId,
                               @RequestParam String content) {
        reminderService.sendReminder(studentId, teacherId, content);
        return Result.success();
    }

    // 学生获取未确认提醒
    @GetMapping("/student/reminder/pending")
    public Result getPendingReminders(@RequestParam String studentId) {
        Long id = parseStudentId(studentId);
        List<StudentReminder> reminders = reminderService.getPendingReminders(id);
        return Result.success(reminders);
    }

    // 学生确认提醒
    @PostMapping("/student/reminder/confirm/{id}")
    public Result confirmReminder(@PathVariable Long id) {
        reminderService.confirmReminder(id);
        return Result.success();
    }
}