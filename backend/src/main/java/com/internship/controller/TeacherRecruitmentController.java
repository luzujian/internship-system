package com.internship.controller;

import com.internship.anno.RequireTeacherPermission;
import com.internship.entity.Result;
import com.internship.service.TeacherRecruitmentService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teacher/recruitment/positions")
@PreAuthorize("hasAnyRole('TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR')")
@Validated
public class TeacherRecruitmentController {
    private final TeacherRecruitmentService recruitmentService;

    public TeacherRecruitmentController(TeacherRecruitmentService recruitmentService) {
        this.recruitmentService = recruitmentService;
    }

    @GetMapping
    @RequireTeacherPermission("menu:recruitment")
    public Result getPositions(@RequestParam(defaultValue = "1") @Min(1) Integer page,
                               @RequestParam(defaultValue = "10") @Min(1) @Max(100) Integer pageSize,
                               @RequestParam(required = false) String companyName,
                               @RequestParam(required = false) String positionName,
                               @RequestParam(required = false) String status,
                               @RequestParam(required = false) @Min(1) Long categoryId) {
        return Result.success(recruitmentService.findPage(page, pageSize, companyName, positionName, status, categoryId));
    }

    @GetMapping("/{id}")
    @RequireTeacherPermission("menu:recruitment")
    public Result getPosition(@PathVariable @Min(1) Long id) {
        return Result.success(recruitmentService.findDetail(id));
    }
}
