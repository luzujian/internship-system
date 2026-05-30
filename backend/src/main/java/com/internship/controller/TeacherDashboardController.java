package com.internship.controller;

import com.internship.entity.Result;
import com.internship.entity.TeacherUser;
import com.internship.entity.dto.TeacherDashboardStatsDTO;
import com.internship.mapper.TeacherUserMapper;
import com.internship.service.TeacherDashboardService;
import com.internship.utils.CurrentHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/teacher/dashboard")
public class TeacherDashboardController {

    @Autowired
    private TeacherDashboardService teacherDashboardService;

    @Autowired
    private TeacherUserMapper teacherUserMapper;

    @GetMapping("/stats")
    public Result getDashboardStats(@RequestParam(required = false) String startDate,
                                     @RequestParam(required = false) String endDate) {
        log.info("获取教师端看板统计数据，时间范围：{} - {}", startDate, endDate);
        try {
            Long userId = CurrentHolder.getUserId();
            String userRole = CurrentHolder.getUserRole();

            String teacherType = null;
            if (userId != null) {
                TeacherUser teacher = teacherUserMapper.findById(userId);
                if (teacher != null) {
                    teacherType = teacher.getTeacherType();
                }
            }
            if (teacherType == null) {
                teacherType = mapRoleToUserType(userRole);
            }
            if (userId == null) {
                userId = 0L;
            }

            log.info("看板统计 - userId: {}, teacherType: {}", userId, teacherType);
            TeacherDashboardStatsDTO stats = teacherDashboardService.getDashboardStats(userId, teacherType, startDate, endDate);
            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取教师端看板统计数据失败", e);
            return Result.error("获取统计数据失败: " + e.getMessage());
        }
    }

    private String mapRoleToUserType(String role) {
        if (role == null) return null;
        if (role.contains("COUNSELOR")) return "COUNSELOR";
        if (role.contains("TEACHER")) return "TEACHER";
        if (role.contains("STUDENT")) return "STUDENT";
        return null;
    }

    @GetMapping("/counselor-stats/{counselorId}")
    public Result getCounselorDashboardStats(@PathVariable Long counselorId,
                                              @RequestParam(required = false) String startDate,
                                              @RequestParam(required = false) String endDate) {
        log.info("获取辅导员看板统计数据，辅导员ID: {}, 时间范围：{} - {}", counselorId, startDate, endDate);
        try {
            TeacherDashboardStatsDTO stats = teacherDashboardService.getCounselorDashboardStats(counselorId, startDate, endDate);
            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取辅导员看板统计数据失败", e);
            return Result.error("获取统计数据失败: " + e.getMessage());
        }
    }
}
