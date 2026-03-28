package com.gdmu.controller;

import com.gdmu.entity.Result;
import com.gdmu.entity.dto.TeacherDashboardStatsDTO;
import com.gdmu.service.TeacherDashboardService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/teacher/dashboard")
public class TeacherDashboardController {

    @Autowired
    private TeacherDashboardService teacherDashboardService;

    @GetMapping("/stats")
    public Result getDashboardStats(@RequestParam(required = false) String startDate,
                                     @RequestParam(required = false) String endDate) {
        log.info("获取教师端看板统计数据，时间范围：{} - {}", startDate, endDate);
        try {
            TeacherDashboardStatsDTO stats = teacherDashboardService.getDashboardStats(startDate, endDate);
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
        try {
            TeacherDashboardStatsDTO stats = teacherDashboardService.getCounselorDashboardStats(counselorId, startDate, endDate);
            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取辅导员看板统计数据失败", e);
            return Result.error("获取统计数据失败: " + e.getMessage());
        }
    }
}
