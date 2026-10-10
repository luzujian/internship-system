package com.internship.controller;

import com.internship.anno.Log;
import com.internship.entity.Result;
import com.internship.entity.User;
import com.internship.entity.dto.InternshipAgreementModels.SubmitAgreementRequest;
import com.internship.entity.dto.TeacherAppraisalFileContent;
import com.internship.service.InternshipAgreementService;
import com.internship.service.UserService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/student/internship-agreements")
@PreAuthorize("hasRole('STUDENT')")
public class StudentInternshipAgreementController {
    private final InternshipAgreementService agreements;
    private final UserService users;

    public StudentInternshipAgreementController(InternshipAgreementService agreements, UserService users) {
        this.agreements = agreements;
        this.users = users;
    }

    @PostMapping("/files")
    @Log(operationType = "UPLOAD", module = "INTERNSHIP_MANAGEMENT", description = "上传盖章实习协议", riskLevel = "MEDIUM")
    public Result upload(Authentication authentication, @RequestParam("file") MultipartFile file) throws Exception {
        return Result.success("协议文件上传成功", agreements.upload(studentId(authentication), file));
    }

    @DeleteMapping("/uploads/{uploadId}")
    @Log(operationType = "DELETE", module = "INTERNSHIP_MANAGEMENT", description = "移除未提交实习协议", riskLevel = "MEDIUM")
    public Result deleteUpload(Authentication authentication, @PathVariable Long uploadId) throws Exception {
        agreements.deleteUnsubmittedUpload(studentId(authentication), uploadId);
        return Result.success("文件已移除");
    }

    @GetMapping("/records/{sourceType}/{sourceId}")
    public Result detail(Authentication authentication, @PathVariable String sourceType, @PathVariable Long sourceId) {
        return Result.success(agreements.studentDetail(studentId(authentication), sourceType, sourceId));
    }

    @PostMapping("/records/{sourceType}/{sourceId}/submit")
    @Log(operationType = "UPDATE", module = "INTERNSHIP_MANAGEMENT", description = "补交或更正实习协议", riskLevel = "MEDIUM")
    public Result submit(Authentication authentication, @PathVariable String sourceType, @PathVariable Long sourceId,
                         @RequestBody SubmitAgreementRequest request) {
        return Result.success("实习协议提交成功", agreements.correct(studentId(authentication), sourceType, sourceId, request));
    }

    @GetMapping("/files/{fileId}/{action:preview|download}")
    @Log(operationType = "READ", module = "INTERNSHIP_MANAGEMENT", description = "读取本人实习协议", riskLevel = "MEDIUM")
    public ResponseEntity<byte[]> file(Authentication authentication, @PathVariable Long fileId, @PathVariable String action)
            throws Exception {
        boolean preview = "preview".equals(action);
        return fileResponse(agreements.studentFile(studentId(authentication), fileId, preview), preview);
    }

    private Long studentId(Authentication authentication) {
        User user = authentication == null ? null : users.findByUsername(authentication.getName());
        if (user == null) throw new AccessDeniedException("学生账号不存在");
        return user.getId();
    }

    static ResponseEntity<byte[]> fileResponse(TeacherAppraisalFileContent file, boolean preview) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(file.getFileType()));
        headers.setContentLength(file.getContent().length);
        headers.setCacheControl("no-store");
        headers.setContentDisposition((preview ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(file.getFileName(), StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(file.getContent());
    }
}
