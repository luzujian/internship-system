package com.internship.controller;

import com.internship.anno.Log;
import com.internship.entity.Result;
import com.internship.entity.TeacherUser;
import com.internship.entity.dto.TeacherAppraisalDetail;
import com.internship.entity.dto.TeacherAppraisalExportFile;
import com.internship.entity.dto.TeacherAppraisalFileContent;
import com.internship.mapper.TeacherUserMapper;
import com.internship.service.TeacherInternshipAppraisalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/teacher/internship-appraisals")
@PreAuthorize("hasAnyRole('TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR')")
public class TeacherInternshipAppraisalController {
    private static final DateTimeFormatter EXPORT_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final TeacherInternshipAppraisalService appraisalService;
    private final TeacherUserMapper teacherUserMapper;

    public TeacherInternshipAppraisalController(TeacherInternshipAppraisalService appraisalService,
                                                 TeacherUserMapper teacherUserMapper) {
        this.appraisalService = appraisalService;
        this.teacherUserMapper = teacherUserMapper;
    }

    @GetMapping
    public ResponseEntity<Result> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long classId,
            @RequestParam(defaultValue = "ALL") String status) {
        TeacherUser teacher = currentTeacher();
        if (teacher == null) return unauthorized();
        return ResponseEntity.ok(Result.success(
                appraisalService.list(teacher, page, pageSize, keyword, classId, status)));
    }

    @GetMapping("/students/{studentId}")
    public ResponseEntity<Result> detail(@PathVariable Long studentId) {
        TeacherUser teacher = currentTeacher();
        if (teacher == null) return unauthorized();
        TeacherAppraisalDetail detail = appraisalService.findSubmittedDetail(teacher, studentId);
        if (detail == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        return ResponseEntity.ok(Result.success(detail));
    }

    @Log(operationType = "READ", module = "INTERNSHIP_MANAGEMENT", description = "预览实习鉴定表（敏感读取）", riskLevel = "MEDIUM")
    @GetMapping("/files/{fileId}/preview")
    public ResponseEntity<byte[]> preview(@PathVariable Long fileId) {
        return fileResponse(fileId, true);
    }

    @Log(operationType = "DOWNLOAD", module = "INTERNSHIP_MANAGEMENT", description = "下载实习鉴定表", riskLevel = "MEDIUM")
    @GetMapping("/files/{fileId}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long fileId) {
        return fileResponse(fileId, false);
    }

    @Log(operationType = "EXPORT", module = "INTERNSHIP_MANAGEMENT", description = "导出实习鉴定数据", riskLevel = "MEDIUM", critical = true)
    @GetMapping("/export")
    public void export(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long classId,
            @RequestParam(defaultValue = "ALL") String status,
            jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        TeacherUser teacher = currentTeacher();
        if (teacher == null) {
            writeJsonError(response, HttpStatus.UNAUTHORIZED.value(), "未登录或教师账号不存在");
            return;
        }
        List<TeacherAppraisalExportFile> files =
                appraisalService.prepareExport(teacher, keyword, classId, status);
        if (files.isEmpty()) {
            writeJsonError(response, HttpStatus.BAD_REQUEST.value(),
                    "当前筛选范围内没有已提交的实习鉴定表可导出");
            return;
        }

        String fileName = "实习鉴定表_" + LocalDateTime.now().format(EXPORT_TIME) + ".zip";
        response.setContentType("application/zip");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Content-Disposition",
                ContentDisposition.attachment()
                        .filename(fileName, StandardCharsets.UTF_8)
                        .build()
                        .toString());
        try (java.io.OutputStream out = response.getOutputStream()) {
            appraisalService.writeZip(files, out);
            out.flush();
        } catch (Exception e) {
            log.error("导出实习鉴定表 ZIP 失败", e);
            if (!response.isCommitted()) {
                writeJsonError(response, HttpStatus.INTERNAL_SERVER_ERROR.value(), "导出失败：" + e.getMessage());
            }
        }
    }

    private void writeJsonError(jakarta.servlet.http.HttpServletResponse response, int status, String message)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        response.getWriter().write(mapper.writeValueAsString(Result.error(message)));
        response.getWriter().flush();
    }

    private ResponseEntity<byte[]> fileResponse(Long fileId, boolean preview) {
        TeacherUser teacher = currentTeacher();
        if (teacher == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            TeacherAppraisalFileContent file = appraisalService.loadFile(teacher, fileId, preview);
            if (file == null) return ResponseEntity.notFound().build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(file.getFileType()));
            headers.setContentLength(file.getContent().length);
            headers.setCacheControl("no-store");
            headers.setContentDisposition((preview ? ContentDisposition.inline() : ContentDisposition.attachment())
                    .filename(file.getFileName(), StandardCharsets.UTF_8)
                    .build());
            return ResponseEntity.ok().headers(headers).body(file.getContent());
        } catch (Exception e) {
            log.error("读取实习鉴定表文件失败，fileId={}, preview={}", fileId, preview, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private TeacherUser currentTeacher() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null || "anonymousUser".equals(authentication.getName())) {
            return null;
        }
        return teacherUserMapper.findByTeacherUserId(authentication.getName());
    }

    private ResponseEntity<Result> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Result.error("未登录或教师账号不存在"));
    }
}
