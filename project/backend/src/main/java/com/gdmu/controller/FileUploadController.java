package com.gdmu.controller;

import com.gdmu.entity.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/upload")
public class FileUploadController {
    
    @Value("${file.upload.path:/uploads}")
    private String uploadPath;
    
    @Value("${file.upload.url-prefix:/uploads}")
    private String urlPrefix;
    
    @Value("${file.upload.max-size:209715200}")
    private long maxSize;
    
    private static final Set<String> ALLOWED_IMAGE_TYPES = new HashSet<>(Arrays.asList(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/gif",
            "image/bmp",
            "image/webp"
    ));
    
    private static final Set<String> ALLOWED_FILE_TYPES = new HashSet<>(Arrays.asList(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain",
            "application/zip",
            "application/x-rar-compressed",
            "application/x-7z-compressed",
            "video/mp4",
            "video/avi",
            "video/mov",
            "video/wmv",
            "video/flv",
            "video/mkv",
            "video/webm"
    ));
    
    @PostMapping("/image")
    public Result uploadImage(@RequestParam("file") MultipartFile file) {
        try {
            File uploadDir = new File(uploadPath);
            if (!uploadDir.exists()) {
                boolean created = uploadDir.mkdirs();
                log.info("创建上传目录: {}, 结果: {}", uploadPath, created);
            } else {
                log.info("上传目录已存在: {}", uploadPath);
            }
            
            if (file.isEmpty()) {
                return Result.error("文件不能为空");
            }
            
            if (file.getSize() > maxSize) {
                return Result.error("文件大小不能超过" + (maxSize / 1024 / 1024) + "MB");
            }
            
            String contentType = file.getContentType();
            if (!ALLOWED_IMAGE_TYPES.contains(contentType)) {
                return Result.error("只支持图片格式文件");
            }
            
            String originalFilename = file.getOriginalFilename();
            String fileExtension = getFileExtension(originalFilename);
            
            String datePath = new SimpleDateFormat("yyyy/MM/dd").format(new Date());
            String fileName = UUID.randomUUID().toString() + "." + fileExtension;
            
            String relativePath = datePath + "/" + fileName;
            String fullPath = uploadPath + "/" + relativePath;
            
            log.info("准备保存文件到: {}", fullPath);
            
            File destFile = new File(fullPath);
            File parentDir = destFile.getParentFile();
            if (!parentDir.exists()) {
                boolean created = parentDir.mkdirs();
                log.info("创建父目录: {}, 结果: {}", parentDir.getAbsolutePath(), created);
            }
            
            file.transferTo(destFile);
            
            String fileUrl = "/api" + urlPrefix + "/" + relativePath;
            
            log.info("文件上传成功: {}", fileUrl);
            
            Map<String, String> result = new HashMap<>();
            result.put("url", fileUrl);
            result.put("filename", fileName);
            result.put("size", String.valueOf(file.getSize()));
            
            return Result.success("上传成功", result);
        } catch (IOException e) {
            log.error("文件上传失败: {}", e.getMessage(), e);
            return Result.error("文件上传失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("文件上传异常: {}", e.getMessage(), e);
            return Result.error("文件上传异常");
        }
    }
    
    @PostMapping("/images")
    public Result uploadImages(@RequestParam("files") MultipartFile[] files) {
        try {
            if (files == null || files.length == 0) {
                return Result.error("文件不能为空");
            }
            
            List<Map<String, String>> uploadedFiles = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            
            File uploadDir = new File(uploadPath);
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
                log.info("创建上传目录: {}", uploadPath);
            }
            
            for (int i = 0; i < files.length; i++) {
                MultipartFile file = files[i];
                try {
                    if (file.isEmpty()) {
                        errors.add("文件" + (i + 1) + "为空");
                        continue;
                    }
                    
                    if (file.getSize() > maxSize) {
                        errors.add("文件" + (i + 1) + "超过大小限制");
                        continue;
                    }
                    
                    String contentType = file.getContentType();
                    if (!ALLOWED_IMAGE_TYPES.contains(contentType)) {
                        errors.add("文件" + (i + 1) + "格式不支持");
                        continue;
                    }
                    
                    String originalFilename = file.getOriginalFilename();
                    String fileExtension = getFileExtension(originalFilename);
                    
                    String datePath = new SimpleDateFormat("yyyy/MM/dd").format(new Date());
                    String fileName = UUID.randomUUID().toString() + "." + fileExtension;
                    
                    String relativePath = datePath + "/" + fileName;
                    String fullPath = uploadPath + "/" + relativePath;
                    
                    File destFile = new File(fullPath);
                    destFile.getParentFile().mkdirs();
                    
                    file.transferTo(destFile);
                    
                    String fileUrl = "/api" + urlPrefix + "/" + relativePath;
                    
                    Map<String, String> fileInfo = new HashMap<>();
                    fileInfo.put("url", fileUrl);
                    fileInfo.put("filename", fileName);
                    fileInfo.put("size", String.valueOf(file.getSize()));
                    
                    uploadedFiles.add(fileInfo);
                } catch (IOException e) {
                    log.error("文件{}上传失败: {}", i + 1, e.getMessage());
                    errors.add("文件" + (i + 1) + "上传失败");
                }
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("uploadedFiles", uploadedFiles);
            result.put("errors", errors);
            result.put("total", files.length);
            result.put("success", uploadedFiles.size());
            result.put("failed", errors.size());
            
            return Result.success("上传完成", result);
        } catch (Exception e) {
            log.error("批量上传失败: {}", e.getMessage(), e);
            return Result.error("批量上传失败: " + e.getMessage());
        }
    }
    
    @DeleteMapping("/image")
    public Result deleteImage(@RequestBody Map<String, String> request) {
        try {
            String url = request.get("url");
            if (url == null || url.isEmpty()) {
                return Result.error("文件URL不能为空");
            }
            
            String relativePath = url.replace(urlPrefix + "/", "");
            String fullPath = uploadPath + "/" + relativePath;
            
            File file = new File(fullPath);
            if (file.exists()) {
                boolean deleted = file.delete();
                if (deleted) {
                    log.info("文件删除成功: {}", fullPath);
                    return Result.success("删除成功");
                } else {
                    return Result.error("删除失败");
                }
            } else {
                return Result.error("文件不存在");
            }
        } catch (Exception e) {
            log.error("文件删除失败: {}", e.getMessage(), e);
            return Result.error("文件删除失败");
        }
    }
    
    @PostMapping("/file")
    public Result uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            File uploadDir = new File(uploadPath);
            if (!uploadDir.exists()) {
                boolean created = uploadDir.mkdirs();
                log.info("创建上传目录: {}, 结果: {}", uploadPath, created);
            } else {
                log.info("上传目录已存在: {}", uploadPath);
            }
            
            if (file.isEmpty()) {
                return Result.error("文件不能为空");
            }
            
            if (file.getSize() > maxSize) {
                return Result.error("文件大小不能超过" + (maxSize / 1024 / 1024) + "MB");
            }
            
            String contentType = file.getContentType();
            if (!ALLOWED_FILE_TYPES.contains(contentType) && !ALLOWED_IMAGE_TYPES.contains(contentType)) {
                return Result.error("不支持的文件格式");
            }
            
            String originalFilename = file.getOriginalFilename();
            String fileExtension = getFileExtension(originalFilename);
            
            String datePath = new SimpleDateFormat("yyyy/MM/dd").format(new Date());
            String fileName = UUID.randomUUID().toString() + "." + fileExtension;
            
            String relativePath = datePath + "/" + fileName;
            String fullPath = uploadPath + "/" + relativePath;
            
            log.info("准备保存文件到: {}", fullPath);
            
            File destFile = new File(fullPath);
            File parentDir = destFile.getParentFile();
            if (!parentDir.exists()) {
                boolean created = parentDir.mkdirs();
                log.info("创建父目录: {}, 结果: {}", parentDir.getAbsolutePath(), created);
            }
            
            file.transferTo(destFile);
            
            String fileUrl = "/api" + urlPrefix + "/" + relativePath;
            
            log.info("文件上传成功: {}", fileUrl);
            
            Map<String, String> result = new HashMap<>();
            result.put("url", fileUrl);
            result.put("filename", fileName);
            result.put("size", String.valueOf(file.getSize()));
            
            return Result.success("上传成功", result);
        } catch (IOException e) {
            log.error("文件上传失败: {}", e.getMessage(), e);
            return Result.error("文件上传失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("文件上传异常: {}", e.getMessage(), e);
            return Result.error("文件上传异常");
        }
    }
    
    @PostMapping("/files")
    public Result uploadFiles(@RequestParam("files") MultipartFile[] files) {
        try {
            log.info("开始批量上传文件，文件数量: {}", files.length);
            
            if (files == null || files.length == 0) {
                log.warn("上传文件为空");
                return Result.error("文件不能为空");
            }
            
            List<Map<String, String>> uploadedFiles = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            
            File uploadDir = new File(uploadPath);
            if (!uploadDir.exists()) {
                boolean created = uploadDir.mkdirs();
                log.info("上传目录不存在，创建目录: {}, 结果: {}", uploadPath, created);
            } else {
                log.info("上传目录已存在: {}", uploadPath);
            }
            
            for (int i = 0; i < files.length; i++) {
                MultipartFile file = files[i];
                try {
                    log.info("开始处理文件{}/{}, 原始文件名: {}, 大小: {}", 
                             i + 1, files.length, file.getOriginalFilename(), file.getSize());
                    
                    if (file.isEmpty()) {
                        log.warn("文件{}为空", i + 1);
                        errors.add("文件" + (i + 1) + "为空");
                        continue;
                    }
                    
                    if (file.getSize() > maxSize) {
                        log.warn("文件{}超过大小限制: {} > {}", i + 1, file.getSize(), maxSize);
                        errors.add("文件" + (i + 1) + "超过大小限制");
                        continue;
                    }
                    
                    String contentType = file.getContentType();
                    log.info("文件{}的Content-Type: {}", i + 1, contentType);
                    
                    if (!ALLOWED_FILE_TYPES.contains(contentType) && !ALLOWED_IMAGE_TYPES.contains(contentType)) {
                        log.warn("文件{}格式不支持: {}", i + 1, contentType);
                        errors.add("文件" + (i + 1) + "格式不支持");
                        continue;
                    }
                    
                    String originalFilename = file.getOriginalFilename();
                    String fileExtension = getFileExtension(originalFilename);
                    
                    String datePath = new SimpleDateFormat("yyyy/MM/dd").format(new Date());
                    String fileName = UUID.randomUUID().toString() + "." + fileExtension;
                    
                    String relativePath = datePath + "/" + fileName;
                    String fullPath = uploadPath + "/" + relativePath;
                    
                    log.info("文件{}保存路径: {}", i + 1, fullPath);
                    
                    File destFile = new File(fullPath);
                    destFile.getParentFile().mkdirs();
                    
                    file.transferTo(destFile);
                    
                    String fileUrl = "/api" + urlPrefix + "/" + relativePath;
                    
                    log.info("文件{}上传成功: {}", i + 1, fileUrl);
                    
                    Map<String, String> fileInfo = new HashMap<>();
                    fileInfo.put("url", fileUrl);
                    fileInfo.put("filename", originalFilename);
                    fileInfo.put("storedName", fileName);
                    fileInfo.put("size", String.valueOf(file.getSize()));
                    fileInfo.put("type", contentType);
                    
                    uploadedFiles.add(fileInfo);
                } catch (IOException e) {
                    log.error("文件{}上传失败: {}", i + 1, e.getMessage(), e);
                    errors.add("文件" + (i + 1) + "上传失败: " + e.getMessage());
                }
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("uploadedFiles", uploadedFiles);
            result.put("errors", errors);
            result.put("total", files.length);
            result.put("success", uploadedFiles.size());
            result.put("failed", errors.size());
            
            log.info("批量上传完成，成功: {}, 失败: {}", uploadedFiles.size(), errors.size());
            
            return Result.success("上传完成", result);
        } catch (Exception e) {
            log.error("批量上传失败: {}", e.getMessage(), e);
            return Result.error("批量上传失败: " + e.getMessage());
        }
    }
    
    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "jpg";
        }
        
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "jpg";
        }
        
        return filename.substring(lastDotIndex + 1).toLowerCase();
    }
    
    @GetMapping("/download/**")
    public ResponseEntity<Resource> downloadFile(HttpServletRequest request) {
        try {
            String path = (String) request.getAttribute(
                org.springframework.web.servlet.HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
            
            if (path == null || path.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            String filePath = uploadPath + "/" + path;
            File file = new File(filePath);
            
            if (!file.exists() || !file.isFile()) {
                log.warn("文件不存在: {}", filePath);
                return ResponseEntity.notFound().build();
            }
            
            Resource resource = new FileSystemResource(file);
            String filename = file.getName();
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8.toString())
                    .replaceAll("\\+", "%20");
            
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, 
                    "attachment; filename=\"" + encodedFilename + "\"");
            headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE);
            
            log.info("下载文件: {}", filePath);
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(resource);
        } catch (Exception e) {
            log.error("下载文件失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/uploads/**")
    public ResponseEntity<Resource> serveFile(HttpServletRequest request) {
        try {
            String path = (String) request.getAttribute(
                org.springframework.web.servlet.HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
            
            if (path == null || path.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            String filePath = uploadPath + "/" + path;
            File file = new File(filePath);
            
            if (!file.exists() || !file.isFile()) {
                log.warn("文件不存在: {}", filePath);
                return ResponseEntity.notFound().build();
            }
            
            Resource resource = new FileSystemResource(file);
            
            // 根据文件类型设置Content-Type
            String contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            String filename = file.getName().toLowerCase();
            
            if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
                contentType = MediaType.IMAGE_JPEG_VALUE;
            } else if (filename.endsWith(".png")) {
                contentType = MediaType.IMAGE_PNG_VALUE;
            } else if (filename.endsWith(".gif")) {
                contentType = MediaType.IMAGE_GIF_VALUE;
            } else if (filename.endsWith(".webp")) {
                contentType = "image/webp";
            } else if (filename.endsWith(".mp4")) {
                contentType = "video/mp4";
            } else if (filename.endsWith(".avi")) {
                contentType = "video/avi";
            } else if (filename.endsWith(".mov")) {
                contentType = "video/quicktime";
            } else if (filename.endsWith(".wmv")) {
                contentType = "video/x-ms-wmv";
            }
            
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_TYPE, contentType);
            headers.add(HttpHeaders.CACHE_CONTROL, "max-age=86400");
            
            log.info("访问文件: {}, Content-Type: {}", filePath, contentType);
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(resource);
        } catch (Exception e) {
            log.error("访问文件失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
