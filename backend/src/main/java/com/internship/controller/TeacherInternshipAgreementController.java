package com.internship.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.anno.Log;
import com.internship.entity.Result;
import com.internship.entity.TeacherUser;
import com.internship.service.InternshipAgreementService;
import com.internship.mapper.TeacherUserMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@RestController
@RequestMapping("/api/teacher/internship-agreements")
@PreAuthorize("hasAnyRole('TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR')")
public class TeacherInternshipAgreementController {
    private final InternshipAgreementService agreements;
    private final TeacherUserMapper teachers;

    public TeacherInternshipAgreementController(InternshipAgreementService agreements, TeacherUserMapper teachers) {
        this.agreements = agreements;
        this.teachers = teachers;
    }

    @GetMapping
    public Result list(Authentication authentication, @RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "20") int pageSize, @RequestParam(required = false) String keyword,
                       @RequestParam(required = false) Long classId, @RequestParam(required = false) String sourceType,
                       @RequestParam(defaultValue = "ALL") String status,
                       @RequestParam(defaultValue = "ACTIVE") String businessStatus) {
        return Result.success(agreements.list(teacher(authentication), page, pageSize, keyword, classId,
                sourceType, status, businessStatus));
    }

    @GetMapping("/records/{sourceType}/{sourceId}")
    public Result detail(Authentication authentication, @PathVariable String sourceType, @PathVariable Long sourceId) {
        return Result.success(agreements.teacherDetail(teacher(authentication), sourceType, sourceId));
    }

    @GetMapping("/files/{fileId}/{action:preview|download}")
    @Log(operationType = "READ", module = "INTERNSHIP_MANAGEMENT", description = "读取管理范围内实习协议", riskLevel = "MEDIUM")
    public ResponseEntity<byte[]> file(Authentication authentication, @PathVariable Long fileId, @PathVariable String action)
            throws Exception {
        boolean preview = "preview".equals(action);
        return StudentInternshipAgreementController.fileResponse(
                agreements.teacherFile(teacher(authentication), fileId, preview), preview);
    }

    @GetMapping("/export")
    @Log(operationType = "EXPORT", module = "INTERNSHIP_MANAGEMENT", description = "导出实习协议", riskLevel = "MEDIUM", critical = true)
    public void export(Authentication authentication, @RequestParam(required = false) String keyword,
                       @RequestParam(required = false) Long classId, @RequestParam(required = false) String sourceType,
                       @RequestParam(defaultValue = "ALL") String status,
                       @RequestParam(defaultValue = "ACTIVE") String businessStatus, HttpServletResponse response)
            throws java.io.IOException {
        var files = agreements.exportFiles(teacher(authentication), keyword, classId, sourceType, status, businessStatus);
        if (files.isEmpty()) {
            response.setStatus(400);
            response.setContentType("application/json;charset=UTF-8");
            new ObjectMapper().writeValue(response.getWriter(), Result.error("当前筛选范围内没有已提交的实习协议可导出"));
            return;
        }
        String name = "实习协议_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".zip";
        response.setContentType("application/zip");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Content-Disposition", ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString());
        try {
            agreements.writeZip(files, response.getOutputStream());
        } catch (Exception e) {
            log.error("导出实习协议失败", e);
            if (!response.isCommitted()) {
                response.reset();
                response.setStatus(500);
                response.setContentType("application/json;charset=UTF-8");
                new ObjectMapper().writeValue(response.getWriter(), Result.error("实习协议导出失败"));
            }
        }
    }

    private TeacherUser teacher(Authentication authentication) {
        TeacherUser teacher = authentication == null ? null : teachers.findByTeacherUserId(authentication.getName());
        if (teacher == null) throw new AccessDeniedException("教师账号不存在");
        return teacher;
    }
}
