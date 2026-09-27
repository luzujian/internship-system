package com.internship.controller;

import com.internship.entity.Result;
import com.internship.service.TeacherMacroStatisticsService;
import com.internship.utils.CurrentHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teacher/macro-statistics")
@PreAuthorize("hasAnyRole('TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR')")
public class TeacherMacroStatisticsController {
    private final TeacherMacroStatisticsService teacherMacroStatisticsService;

    public TeacherMacroStatisticsController(TeacherMacroStatisticsService teacherMacroStatisticsService) {
        this.teacherMacroStatisticsService = teacherMacroStatisticsService;
    }

    @GetMapping
    public Result getStatistics() {
        return Result.success(teacherMacroStatisticsService.getStatistics(CurrentHolder.getUserId()));
    }
}
