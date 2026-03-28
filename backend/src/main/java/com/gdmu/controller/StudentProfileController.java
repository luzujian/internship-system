package com.gdmu.controller;

import com.gdmu.entity.Result;
import com.gdmu.entity.StudentArchive;
import com.gdmu.entity.StudentUser;
import com.gdmu.service.StudentArchiveService;
import com.gdmu.service.StudentUserService;
import com.gdmu.service.UserService;
import com.gdmu.utils.AliyunOSSOperator;
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
    private com.gdmu.entity.User getCurrentUser() {
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
        com.gdmu.entity.User user = getCurrentUser();
        return user != null ? user.getId() : null;
    }

    /**
     * 上传简历
     */
    @PostMapping("/upload/resume")
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
            if (contentType == null || !contentType.startsWith("image/")) {
                return Result.error("头像只支持图片格式");
            }

            // 限制文件大小（5MB）
            if (file.getSize() > 5 * 1024 * 1024) {
                return Result.error("头像大小不能超过 5MB");
            }

            // 上传到 OSS
            String originalFilename = file.getOriginalFilename();
            String fileExtension = getFileExtension(originalFilename);
            if (fileExtension.isEmpty()) {
                fileExtension = "jpg";
            }
            String datePath = new java.text.SimpleDateFormat("yyyy/MM/dd").format(new Date());
            String fileName = UUID.randomUUID().toString() + "." + fileExtension;
            String objectName = "student/avatar/" + studentId + "/" + datePath + "/" + fileName;
            String fileUrl = aliyunOSSOperator.uploadWithObjectName(file.getBytes(), objectName);

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
