package com.internship.controller;

import com.internship.anno.Log;
import com.internship.entity.Result;
import com.internship.entity.StudentArchive;
import com.internship.entity.StudentUser;
import com.internship.service.StudentArchiveService;
import com.internship.service.StudentUserService;
import com.internship.service.UserService;
import com.internship.utils.AliyunOSSOperator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/student/profile")
@PreAuthorize("hasRole('STUDENT')")
public class StudentProfileController {

    @Autowired
    private StudentArchiveService studentArchiveService;

    @Autowired
    private StudentUserService studentUserService;

    @Autowired
    private UserService userService;

    @Autowired
    private AliyunOSSOperator aliyunOSSOperator;

    /**
     * 获取当前登录用户
     */
    private com.internship.entity.User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof org.springframework.security.core.userdetails.User)) {
            return null;
        }
        String username = ((org.springframework.security.core.userdetails.User) auth.getPrincipal()).getUsername();
        return userService.findByUsername(username);
    }

    /**
     * 获取当前学生的ID
     */
    private Long getCurrentStudentId() {
        com.internship.entity.User user = getCurrentUser();
        return user != null ? user.getId() : null;
    }

    /**
     * 上传简历
     */
    @PostMapping("/upload/resume")
    @Log(operationType = "UPLOAD", module = "STUDENT_MANAGEMENT", description = "上传简历", riskLevel = "MEDIUM")
    public Result uploadResume(@RequestParam("file") MultipartFile file) {
        try {
            Long studentId = getCurrentStudentId();
            if (studentId == null) {
                return Result.error("请先登录");
            }

            if (file.isEmpty()) {
                return Result.error("文件不能为空");
            }

            // 验证文件类型
            String contentType = file.getContentType();
            if (contentType == null || (!contentType.contains("pdf") && !contentType.contains("word") && !contentType.contains("document"))) {
                return Result.error("简历只支持 PDF、Word 格式");
            }

            // 上传到 OSS
            String originalFilename = file.getOriginalFilename();
            String fileExtension = getFileExtension(originalFilename);
            String datePath = new java.text.SimpleDateFormat("yyyy/MM").format(new Date());
            String fileName = UUID.randomUUID().toString() + "." + fileExtension;
            String objectName = "student/resume/" + studentId + "/" + datePath + "/" + fileName;
            String fileUrl = aliyunOSSOperator.uploadWithObjectName(file.getBytes(), objectName);

            log.info("简历上传成功：studentId={}, url={}", studentId, fileUrl);

            // 先删除旧简历（如果存在）
            List<StudentArchive> oldResumes = studentArchiveService.findByStudentId(studentId).stream()
                    .filter(a -> "resume".equals(a.getFileType()))
                    .toList();
            for (StudentArchive old : oldResumes) {
                try {
                    aliyunOSSOperator.deleteFile(old.getFileUrl());
                } catch (Exception e) {
                    log.warn("删除旧简历失败：{}", e.getMessage());
                }
                studentArchiveService.delete(old.getId());
            }

            // 保存新简历记录
            StudentArchive archive = new StudentArchive();
            archive.setStudentId(studentId);
            archive.setFileType("resume");
            archive.setFileName(originalFilename);
            archive.setFileUrl(fileUrl);
            archive.setUploadTime(new Date());
            archive.setStatus(1); // 已审核
            studentArchiveService.insert(archive);

            Map<String, String> result = new HashMap<>();
            result.put("url", fileUrl);
            result.put("filename", originalFilename);
            result.put("size", String.valueOf(file.getSize()));

            return Result.success("简历上传成功", result);
        } catch (IOException e) {
            log.error("简历上传失败：{}", e.getMessage(), e);
            return Result.error("简历上传失败：" + e.getMessage());
        } catch (Exception e) {
            log.error("简历上传异常：{}", e.getMessage(), e);
            return Result.error("简历上传异常");
        }
    }

    /**
     * 上传证书
     */
    @PostMapping("/upload/certificate")
    @Log(operationType = "UPLOAD", module = "STUDENT_MANAGEMENT", description = "上传证书", riskLevel = "MEDIUM")
    public Result uploadCertificate(@RequestParam("file") MultipartFile file) {
        try {
            Long studentId = getCurrentStudentId();
            if (studentId == null) {
                return Result.error("请先登录");
            }

            if (file.isEmpty()) {
                return Result.error("文件不能为空");
            }

            // 验证文件类型
            String contentType = file.getContentType();
            Set<String> allowedTypes = new HashSet<>(Arrays.asList(
                    "application/pdf",
                    "image/jpeg",
                    "image/jpg",
                    "image/png",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            ));
            if (contentType == null || !allowedTypes.contains(contentType)) {
                return Result.error("证书只支持 PDF、图片、Word 格式");
            }

            // 上传到 OSS
            String originalFilename = file.getOriginalFilename();
            String fileExtension = getFileExtension(originalFilename);
            String datePath = new java.text.SimpleDateFormat("yyyy/MM").format(new Date());
            String fileName = UUID.randomUUID().toString() + "." + fileExtension;
            String objectName = "student/certificate/" + studentId + "/" + datePath + "/" + fileName;
            String fileUrl = aliyunOSSOperator.uploadWithObjectName(file.getBytes(), objectName);

            log.info("证书上传成功：studentId={}, url={}", studentId, fileUrl);

            // 保存证书记录
            StudentArchive archive = new StudentArchive();
            archive.setStudentId(studentId);
            archive.setFileType("certificate");
            archive.setFileName(originalFilename);
            archive.setFileUrl(fileUrl);
            archive.setUploadTime(new Date());
            archive.setStatus(1);
            studentArchiveService.insert(archive);

            Map<String, String> result = new HashMap<>();
            result.put("url", fileUrl);
            result.put("filename", originalFilename);
            result.put("size", String.valueOf(file.getSize()));

            return Result.success("证书上传成功", result);
        } catch (IOException e) {
            log.error("证书上传失败：{}", e.getMessage(), e);
            return Result.error("证书上传失败：" + e.getMessage());
        } catch (Exception e) {
            log.error("证书上传异常：{}", e.getMessage(), e);
            return Result.error("证书上传异常");
        }
    }

    /**
     * 上传头像
     */
    @PostMapping("/upload/avatar")
    @Log(operationType = "UPLOAD", module = "STUDENT_MANAGEMENT", description = "上传头像", riskLevel = "LOW")
    public Result uploadAvatar(@RequestParam("file") MultipartFile file) {
        try {
            Long studentId = getCurrentStudentId();
            if (studentId == null) {
                return Result.error("请先登录");
            }

            if (file.isEmpty()) {
                return Result.error("文件不能为空");
            }

            // 验证文件类型（只允许图片）
            String contentType = file.getContentType();
            String extension = switch (contentType == null ? "" : contentType) {
                case "image/jpeg", "image/jpg" -> "jpg";
                case "image/png" -> "png";
                case "image/gif" -> "gif";
                case "image/webp" -> "webp";
                case "image/bmp" -> "bmp";
                default -> null;
            };
            if (extension == null) {
                return Result.error("头像只支持图片格式");
            }

            // 限制文件大小（5MB）
            if (file.getSize() > 5 * 1024 * 1024) {
                return Result.error("头像大小不能超过 5MB");
            }

            // 上传到 OSS
            byte[] content = file.getBytes();
            if (!matchesAvatarSignature(content, contentType)) {
                return Result.error("头像内容格式不正确");
            }
            String datePath = new java.text.SimpleDateFormat("yyyy/MM/dd").format(new Date());
            String fileName = UUID.randomUUID().toString() + "." + extension;
            String objectName = "student/avatar/" + studentId + "/" + datePath + "/" + fileName;
            String fileUrl = aliyunOSSOperator.uploadPublicMediaWithObjectName(content, objectName);

            log.info("头像上传成功：studentId={}, url={}", studentId, fileUrl);

            // 更新学生头像字段
            StudentUser studentUser = studentUserService.findById(studentId);
            if (studentUser != null) {
                // 删除旧头像（如果存在）
                if (studentUser.getAvatar() != null && !studentUser.getAvatar().isEmpty()) {
                    try {
                        aliyunOSSOperator.deleteFile(studentUser.getAvatar());
                    } catch (Exception e) {
                        log.warn("删除旧头像失败：{}", e.getMessage());
                    }
                }
                studentUser.setAvatar(fileUrl);
                studentUserService.update(studentUser);
            }

            Map<String, String> result = new HashMap<>();
            result.put("url", fileUrl);

            return Result.success("头像上传成功", result);
        } catch (IOException e) {
            log.error("头像上传失败：{}", e.getMessage(), e);
            return Result.error("头像上传失败：" + e.getMessage());
        } catch (Exception e) {
            log.error("头像上传异常：{}", e.getMessage(), e);
            return Result.error("头像上传异常");
        }
    }

    private boolean matchesAvatarSignature(byte[] bytes, String contentType) {
        if (bytes.length < 4) return false;
        return switch (contentType) {
            case "image/jpeg", "image/jpg" -> (bytes[0] & 0xff) == 0xff
                    && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
            case "image/png" -> bytes.length >= 8 && (bytes[0] & 0xff) == 0x89
                    && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G'
                    && bytes[4] == 13 && bytes[5] == 10 && bytes[6] == 26 && bytes[7] == 10;
            case "image/gif" -> bytes.length >= 6 && bytes[0] == 'G' && bytes[1] == 'I'
                    && bytes[2] == 'F' && bytes[3] == '8' && (bytes[4] == '7' || bytes[4] == '9') && bytes[5] == 'a';
            case "image/bmp" -> bytes[0] == 'B' && bytes[1] == 'M';
            case "image/webp" -> bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I'
                    && bytes[2] == 'F' && bytes[3] == 'F' && bytes[8] == 'W'
                    && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
            default -> false;
        };
    }

    /**
     * 绑定/更换手机号和邮箱（直接更新，无需验证码）
     * phone、email 至少传一个；传空或缺失的字段保持不变
     */
    @PostMapping("/bind-phone")
    @Log(operationType = "UPDATE", module = "STUDENT_MANAGEMENT", description = "绑定手机号和邮箱", riskLevel = "MEDIUM")
    public Result bindPhone(@RequestBody Map<String, String> request) {
        try {
            Long studentId = getCurrentStudentId();
            if (studentId == null) {
                return Result.error("请先登录");
            }
            String phone = request.get("phone") == null ? "" : request.get("phone").trim();
            String email = request.get("email") == null ? "" : request.get("email").trim();
            if (phone.isEmpty() && email.isEmpty()) {
                return Result.error("手机号和邮箱不能同时为空");
            }
            if (!phone.isEmpty() && !phone.matches("^1[3-9]\\d{9}$")) {
                return Result.error("请输入正确的手机号码");
            }
            if (!email.isEmpty() && !email.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")) {
                return Result.error("邮箱格式不正确");
            }
            StudentUser studentUser = studentUserService.findById(studentId);
            if (studentUser == null) {
                return Result.error("用户不存在");
            }
            if (!phone.isEmpty()) {
                studentUser.setPhone(phone);
            }
            if (!email.isEmpty()) {
                studentUser.setEmail(email);
            }
            studentUserService.update(studentUser);
            log.info("学生 {} 更新手机号和邮箱成功", studentUser.getStudentId());
            return Result.success("绑定成功");
        } catch (Exception e) {
            log.error("绑定失败: {}", e.getMessage(), e);
            return Result.error("绑定失败");
        }
    }

    /**
     * 获取我的简历列表
     */
    @GetMapping("/resumes")
    public Result getMyResumes() {
        try {
            Long studentId = getCurrentStudentId();
            if (studentId == null) {
                return Result.error("请先登录");
            }

            List<StudentArchive> archives = studentArchiveService.findByStudentId(studentId).stream()
                    .filter(a -> "resume".equals(a.getFileType()))
                    .toList();

            List<Map<String, Object>> result = new ArrayList<>();
            for (StudentArchive archive : archives) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", archive.getId());
                item.put("name", archive.getFileName());
                item.put("url", archive.getFileUrl());
                item.put("uploadTime", archive.getUploadTime());
                item.put("status", archive.getStatus());
                result.add(item);
            }

            return Result.success(result);
        } catch (Exception e) {
            log.error("获取简历列表失败：{}", e.getMessage(), e);
            return Result.error("获取简历列表失败");
        }
    }

    /**
     * 获取我的证书列表
     */
    @GetMapping("/certificates")
    public Result getMyCertificates() {
        try {
            Long studentId = getCurrentStudentId();
            if (studentId == null) {
                return Result.error("请先登录");
            }

            List<StudentArchive> archives = studentArchiveService.findByStudentId(studentId).stream()
                    .filter(a -> "certificate".equals(a.getFileType()))
                    .toList();

            List<Map<String, Object>> result = new ArrayList<>();
            for (StudentArchive archive : archives) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", archive.getId());
                item.put("name", archive.getFileName());
                item.put("url", archive.getFileUrl());
                item.put("uploadTime", archive.getUploadTime());
                item.put("status", archive.getStatus());
                result.add(item);
            }

            return Result.success(result);
        } catch (Exception e) {
            log.error("获取证书列表失败：{}", e.getMessage(), e);
            return Result.error("获取证书列表失败");
        }
    }

    /**
     * 删除简历
     */
    @DeleteMapping("/resume/{id}")
    @Log(operationType = "DELETE", module = "STUDENT_MANAGEMENT", description = "删除简历", riskLevel = "MEDIUM")
    public Result deleteResume(@PathVariable Long id) {
        try {
            Long studentId = getCurrentStudentId();
            if (studentId == null) {
                return Result.error("请先登录");
            }

            StudentArchive archive = studentArchiveService.findById(id);
            if (archive == null) {
                return Result.error("简历不存在");
            }

            if (!archive.getStudentId().equals(studentId)) {
                return Result.error("无权删除此简历");
            }

            // 从 OSS 删除
            try {
                aliyunOSSOperator.deleteFile(archive.getFileUrl());
            } catch (Exception e) {
                log.warn("从OSS删除简历失败：{}", e.getMessage());
            }

            // 从数据库删除
            studentArchiveService.delete(id);

            return Result.success("简历删除成功");
        } catch (Exception e) {
            log.error("删除简历失败：{}", e.getMessage(), e);
            return Result.error("删除简历失败");
        }
    }

    /**
     * 删除证书
     */
    @DeleteMapping("/certificate/{id}")
    @Log(operationType = "DELETE", module = "STUDENT_MANAGEMENT", description = "删除证书", riskLevel = "MEDIUM")
    public Result deleteCertificate(@PathVariable Long id) {
        try {
            Long studentId = getCurrentStudentId();
            if (studentId == null) {
                return Result.error("请先登录");
            }

            StudentArchive archive = studentArchiveService.findById(id);
            if (archive == null) {
                return Result.error("证书不存在");
            }

            if (!archive.getStudentId().equals(studentId)) {
                return Result.error("无权删除此证书");
            }

            // 从 OSS 删除
            try {
                aliyunOSSOperator.deleteFile(archive.getFileUrl());
            } catch (Exception e) {
                log.warn("从OSS删除证书失败：{}", e.getMessage());
            }

            // 从数据库删除
            studentArchiveService.delete(id);

            return Result.success("证书删除成功");
        } catch (Exception e) {
            log.error("删除证书失败：{}", e.getMessage(), e);
            return Result.error("删除证书失败");
        }
    }

    /**
     * 预览简历
     */
    @Log(operationType = "DOWNLOAD", module = "STUDENT_MANAGEMENT", description = "下载学生简历", riskLevel = "MEDIUM")
    @GetMapping("/download/resume/{id}")
    public ResponseEntity<byte[]> downloadResume(@PathVariable Long id) {
        try {
            Long studentId = getCurrentStudentId();
            if (studentId == null) {
                return ResponseEntity.status(401).build();
            }

            StudentArchive archive = studentArchiveService.findById(id);
            if (archive == null) {
                return ResponseEntity.notFound().build();
            }

            if (!archive.getStudentId().equals(studentId)) {
                return ResponseEntity.status(403).build();
            }

            byte[] fileBytes = aliyunOSSOperator.downloadFile(archive.getFileUrl());

            HttpHeaders headers = new HttpHeaders();
            MediaType mediaType = getMediaType(archive.getFileName());
            headers.setContentType(mediaType);
            // inline 表示浏览器内联显示（预览），attachment 表示下载
            headers.setContentDisposition(org.springframework.http.ContentDisposition.builder("inline")
                    .filename(URLEncoder.encode(archive.getFileName(), StandardCharsets.UTF_8.toString()).replaceAll("\\+", "%20"))
                    .build());

            return ResponseEntity.ok().headers(headers).body(fileBytes);
        } catch (Exception e) {
            log.error("预览简历失败：{}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 预览简历 - 仅返回文件内容供前端 blob 预览
     */
    @Log(operationType = "READ", module = "STUDENT_MANAGEMENT", description = "预览学生简历（敏感读取）", riskLevel = "MEDIUM")
    @GetMapping("/preview/resume/{id}")
    public ResponseEntity<byte[]> previewResume(@PathVariable Long id) {
        try {
            Long studentId = getCurrentStudentId();
            if (studentId == null) {
                return ResponseEntity.status(401).build();
            }

            StudentArchive archive = studentArchiveService.findById(id);
            if (archive == null) {
                return ResponseEntity.notFound().build();
            }

            if (!archive.getStudentId().equals(studentId)) {
                return ResponseEntity.status(403).build();
            }

            byte[] fileBytes = aliyunOSSOperator.downloadFile(archive.getFileUrl());

            HttpHeaders headers = new HttpHeaders();
            MediaType mediaType = getMediaType(archive.getFileName());
            headers.setContentType(mediaType);
            // 不设置 Content-Disposition，前端用 blob URL 预览

            return ResponseEntity.ok().headers(headers).body(fileBytes);
        } catch (Exception e) {
            log.error("预览简历失败：{}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 预览证书
     */
    @Log(operationType = "DOWNLOAD", module = "STUDENT_MANAGEMENT", description = "下载学生证书", riskLevel = "MEDIUM")
    @GetMapping("/download/certificate/{id}")
    public ResponseEntity<byte[]> downloadCertificate(@PathVariable Long id) {
        try {
            Long studentId = getCurrentStudentId();
            if (studentId == null) {
                return ResponseEntity.status(401).build();
            }

            StudentArchive archive = studentArchiveService.findById(id);
            if (archive == null) {
                return ResponseEntity.notFound().build();
            }

            if (!archive.getStudentId().equals(studentId)) {
                return ResponseEntity.status(403).build();
            }

            byte[] fileBytes = aliyunOSSOperator.downloadFile(archive.getFileUrl());

            HttpHeaders headers = new HttpHeaders();
            MediaType mediaType = getMediaType(archive.getFileName());
            headers.setContentType(mediaType);
            headers.setContentDisposition(org.springframework.http.ContentDisposition.builder("inline")
                    .filename(URLEncoder.encode(archive.getFileName(), StandardCharsets.UTF_8.toString()).replaceAll("\\+", "%20"))
                    .build());

            return ResponseEntity.ok().headers(headers).body(fileBytes);
        } catch (Exception e) {
            log.error("预览证书失败：{}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 根据文件名获取 MediaType
     */
    private MediaType getMediaType(String filename) {
        if (filename == null || filename.isEmpty()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        String lowerName = filename.toLowerCase();
        if (lowerName.endsWith(".pdf")) {
            return MediaType.APPLICATION_PDF;
        } else if (lowerName.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        } else if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        } else if (lowerName.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        } else if (lowerName.endsWith(".doc")) {
            return MediaType.parseMediaType("application/msword");
        } else if (lowerName.endsWith(".docx")) {
            return MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        } else if (lowerName.endsWith(".xls")) {
            return MediaType.parseMediaType("application/vnd.ms-excel");
        } else if (lowerName.endsWith(".xlsx")) {
            return MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "pdf";
        }
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "pdf";
        }
        return filename.substring(lastDotIndex + 1).toLowerCase();
    }
}
