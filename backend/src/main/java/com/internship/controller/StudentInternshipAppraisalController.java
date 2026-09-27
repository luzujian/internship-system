package com.internship.controller;

import com.internship.anno.Log;
import com.internship.entity.FileUpload;
import com.internship.entity.InternshipAppraisalFile;
import com.internship.entity.InternshipStageAssignment;
import com.internship.entity.Result;
import com.internship.entity.User;
import com.internship.entity.dto.AppraisalSubmitRequest;
import com.internship.service.InternshipAppraisalService;
import com.internship.service.InternshipStageAssignmentService;
import com.internship.service.UserService;
import com.internship.utils.AliyunOSSOperator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/student/internship-appraisal")
@PreAuthorize("hasRole('STUDENT')")
public class StudentInternshipAppraisalController {
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx");

    private final UserService userService;
    private final InternshipAppraisalService appraisalService;
    private final InternshipStageAssignmentService assignmentService;
    private final AliyunOSSOperator ossOperator;

    @Value("${internship.appraisal.max-size:20971520}")
    private long maxSize;

    public StudentInternshipAppraisalController(UserService userService,
                                                InternshipAppraisalService appraisalService,
                                                InternshipStageAssignmentService assignmentService,
                                                AliyunOSSOperator ossOperator) {
        this.userService = userService;
        this.appraisalService = appraisalService;
        this.assignmentService = assignmentService;
        this.ossOperator = ossOperator;
    }

    @GetMapping
    public Result getAppraisal() {
        User user = currentUser();
        if (user == null) return Result.error("未登录");
        return Result.success(appraisalService.getAppraisal(user.getId()));
    }

    @PostMapping("/files")
    @Log(operationType = "UPLOAD", module = "INTERNSHIP_MANAGEMENT", description = "上传实习鉴定表", riskLevel = "MEDIUM")
    public Result upload(@RequestParam("file") MultipartFile file,
                         @RequestParam(value = "placementId", required = false) Long placementId,
                         @RequestParam(value = "stageAssignmentId", required = false) Long legacyStageAssignmentId) {
        User user = currentUser();
        if (user == null) return Result.error("未登录");
        String objectName = null;
        boolean uploadedToOss = false;
        try {
            validateFile(file);
            Long assignmentId = placementId != null ? placementId : legacyStageAssignmentId;
            InternshipStageAssignment assignment = null;
            if (assignmentId != null) {
                assignment = assignmentService.findById(assignmentId);
                if (assignment == null || !user.getId().equals(assignment.getStudentId())) {
                    return Result.error("无权关联该实习经历");
                }
            }
            appraisalService.validateFileUploadAllowed(user.getId());

            String originalName = file.getOriginalFilename();
            String extension = extension(originalName);
            String datePath = new SimpleDateFormat("yyyy/MM").format(new Date());
            objectName = "internship-appraisal/" + datePath + "/"
                    + UUID.randomUUID() + "." + extension;
            String fileUrl = ossOperator.uploadWithObjectName(file.getBytes(), objectName);
            uploadedToOss = true;

            FileUpload upload = new FileUpload();
            upload.setFileName(originalName);
            upload.setFilePath(fileUrl);
            upload.setFileSize(file.getSize());
            upload.setFileType(extension);
            upload.setUploaderId(user.getId());
            upload.setUploaderType("student");
            upload.setBusinessType("internship_appraisal");
            InternshipAppraisalFile appraisalFile = new InternshipAppraisalFile();
            appraisalFile.setStageAssignmentId(assignmentId);
            if (assignment != null) {
                appraisalFile.setCompanyId(assignment.getCompanyId());
                appraisalFile.setCompanyName(assignment.getCompanyName());
            }
            appraisalFile.setFileName(originalName);
            appraisalFile.setFilePath(fileUrl);
            appraisalFile.setFileSize(file.getSize());
            appraisalFile.setFileType(upload.getFileType());
            appraisalFile = appraisalService.addUploadedFile(user.getId(), upload, appraisalFile);

            Map<String, Object> data = new HashMap<>();
            data.put("id", appraisalFile.getId());
            data.put("fileName", appraisalFile.getFileName());
            data.put("placementId", appraisalFile.getStageAssignmentId());
            data.put("stageAssignmentId", appraisalFile.getStageAssignmentId());
            return Result.success("鉴定表上传成功", data);
        } catch (Exception e) {
            if (uploadedToOss && objectName != null) {
                try {
                    ossOperator.deleteFile(objectName);
                } catch (Exception cleanupError) {
                    log.warn("上传失败后清理 OSS 鉴定表文件失败: {}", cleanupError.getMessage());
                }
            }
            log.error("上传实习鉴定表失败: {}", e.getMessage(), e);
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/files/{id}")
    @Log(operationType = "DELETE", module = "INTERNSHIP_MANAGEMENT", description = "删除实习鉴定表", riskLevel = "MEDIUM")
    public Result delete(@PathVariable Long id) {
        User user = currentUser();
        if (user == null) return Result.error("未登录");
        try {
            appraisalService.deleteFile(user.getId(), id);
            return Result.success("鉴定表已删除");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/submit")
    @Log(operationType = "CREATE", module = "INTERNSHIP_MANAGEMENT", description = "提交实习鉴定表", riskLevel = "MEDIUM")
    public Result submit(@RequestBody(required = false) AppraisalSubmitRequest request) {
        User user = currentUser();
        if (user == null) return Result.error("未登录");
        try {
            return Result.success("实习鉴定表提交成功", appraisalService.submit(user.getId(), request));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/withdraw")
    @Log(operationType = "UPDATE", module = "INTERNSHIP_MANAGEMENT", description = "撤回实习鉴定表", riskLevel = "MEDIUM")
    public Result withdraw() {
        User user = currentUser();
        if (user == null) return Result.error("未登录");
        try {
            return Result.success("实习鉴定表已撤回，可修改后重新提交", appraisalService.withdraw(user.getId()));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("文件不能为空");
        if (file.getSize() > maxSize) throw new IllegalArgumentException("文件大小不能超过20MB");
        if (!ALLOWED_EXTENSIONS.contains(extension(file.getOriginalFilename()))) {
            throw new IllegalArgumentException("仅支持 PDF、DOC、DOCX 格式");
        }
    }

    private String extension(String name) {
        if (name == null || !name.contains(".")) return "";
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase();
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof org.springframework.security.core.userdetails.User principal)) {
            return null;
        }
        return userService.findByUsername(principal.getUsername());
    }
}
