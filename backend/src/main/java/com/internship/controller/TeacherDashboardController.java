package com.internship.controller;

import com.internship.entity.Result;
import com.internship.entity.TeacherUser;
import com.internship.entity.dto.TeacherDashboardStatsDTO;
import com.internship.mapper.TeacherUserMapper;
import com.internship.service.TeacherDashboardService;
import com.internship.utils.CurrentHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
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
        TeacherUser teacher = requireCurrentTeacher();
        try {
            log.info("看板统计 - userId: {}, teacherType: {}", teacher.getId(), teacher.getTeacherType());
            TeacherDashboardStatsDTO stats = teacherDashboardService.getDashboardStats(
                    teacher.getId(), teacher.getTeacherType(), startDate, endDate);
            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取教师端看板统计数据失败", e);
            return Result.error("获取统计数据失败: " + e.getMessage());
        }
    }

    @GetMapping("/counselor-stats/{counselorId}")
    public Result getCounselorDashboardStats(@PathVariable Long counselorId,
                                              @RequestParam(required = false) String startDate,
                                              @RequestParam(required = false) String endDate) {
        log.info("获取辅导员看板统计数据，辅导员ID: {}, 时间范围：{} - {}", counselorId, startDate, endDate);
        TeacherUser teacher = requireCurrentTeacher();
        if (!"COUNSELOR".equals(teacher.getTeacherType()) || !teacher.getId().equals(counselorId)) {
            throw new AccessDeniedException("无权查看该辅导员看板");
        }
        try {
            TeacherDashboardStatsDTO stats = teacherDashboardService.getCounselorDashboardStats(counselorId, startDate, endDate);
            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取辅导员看板统计数据失败", e);
            return Result.error("获取统计数据失败: " + e.getMessage());
        }
    }

    private TeacherUser requireCurrentTeacher() {
        Long userId = CurrentHolder.getUserId();
        TeacherUser teacher = userId == null ? null : teacherUserMapper.findById(userId);
        if (teacher == null || !Integer.valueOf(1).equals(teacher.getStatus())
                || teacher.getTeacherType() == null
                || !java.util.Set.of("COUNSELOR", "DEPARTMENT", "COLLEGE").contains(teacher.getTeacherType())) {
            throw new AccessDeniedException("教师身份或负责范围不可用");
        }
        return teacher;
    }
}
