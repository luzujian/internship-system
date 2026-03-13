package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.entity.Result;
import com.gdmu.utils.AliyunOSSOperator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/upload")
public class FileUploadController {

    @Autowired
    private AliyunOSSOperator aliyunOSSOperator;

    @Value("${file.upload.max-size:10485760}")
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
            "application/x-7z-compressed"
    ));

    @PostMapping("/image")
    public Result uploadImage(@RequestParam("file") MultipartFile file) {
        try {
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

            String datePath = new SimpleDateFormat("yyyy/MM").format(new Date());
            String fileName = UUID.randomUUID().toString() + "." + fileExtension;

            // 上传到 OSS 的 images 目录
            String objectName = "images/" + datePath + "/" + fileName;
            String fileUrl = aliyunOSSOperator.uploadWithObjectName(file.getBytes(), objectName);

            log.info("文件上传 OSS 成功：{}", fileUrl);

            Map<String, String> result = new HashMap<>();
            result.put("url", fileUrl);
            result.put("filename", fileName);
            result.put("size", String.valueOf(file.getSize()));

            return Result.success("上传成功", result);
        } catch (IOException e) {
            log.error("文件上传失败：{}", e.getMessage(), e);
            return Result.error("文件上传失败：" + e.getMessage());
        } catch (Exception e) {
            log.error("文件上传异常：{}", e.getMessage(), e);
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

                    String datePath = new SimpleDateFormat("yyyy/MM").format(new Date());
                    String fileName = UUID.randomUUID().toString() + "." + fileExtension;

                    // 上传到 OSS 的 images 目录
                    String objectName = "images/" + datePath + "/" + fileName;
                    String fileUrl = aliyunOSSOperator.uploadWithObjectName(file.getBytes(), objectName);

                    Map<String, String> fileInfo = new HashMap<>();
                    fileInfo.put("url", fileUrl);
                    fileInfo.put("filename", fileName);
                    fileInfo.put("size", String.valueOf(file.getSize()));

                    uploadedFiles.add(fileInfo);
                } catch (Exception e) {
                    log.error("文件{}上传失败：{}", i + 1, e.getMessage());
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
            log.error("批量上传失败：{}", e.getMessage(), e);
            return Result.error("批量上传失败：" + e.getMessage());
        }
    }

    @DeleteMapping("/image")
    @Log(operationType = "DELETE", module = "SYSTEM_SETTINGS", description = "删除图片文件")
    public Result deleteImage(@RequestBody Map<String, String> request) {
        try {
            String url = request.get("url");
            if (url == null || url.isEmpty()) {
                return Result.error("文件 URL 不能为空");
            }

            // 从 OSS 删除文件
            aliyunOSSOperator.deleteFile(url);

            log.info("文件删除成功：{}", url);
            return Result.success("删除成功");
        } catch (Exception e) {
            log.error("文件删除失败：{}", e.getMessage(), e);
            return Result.error("文件删除失败：" + e.getMessage());
        }
    }

    @PostMapping("/file")
    public Result uploadFile(@RequestParam("file") MultipartFile file) {
        try {
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

            String datePath = new SimpleDateFormat("yyyy/MM").format(new Date());
            String fileName = UUID.randomUUID().toString() + "." + fileExtension;

            // 根据文件类型选择 OSS 目录
            String category = "files";
            if (contentType != null) {
                if (contentType.contains("pdf")) {
                    category = "resources/pdf";
                } else if (contentType.contains("word") || contentType.contains("document")) {
                    category = "resources/doc";
                } else if (contentType.contains("excel") || contentType.contains("spreadsheet")) {
                    category = "resources/excel";
                } else if (contentType.contains("powerpoint") || contentType.contains("presentation")) {
                    category = "resources/ppt";
                } else if (contentType.startsWith("image/")) {
                    category = "images";
                }
            }

            // 上传到 OSS
            String objectName = category + "/" + datePath + "/" + fileName;
            String fileUrl = aliyunOSSOperator.uploadWithObjectName(file.getBytes(), objectName);

            log.info("文件上传 OSS 成功：{}", fileUrl);

            Map<String, String> result = new HashMap<>();
            result.put("url", fileUrl);
            result.put("filename", originalFilename);
            result.put("storedName", fileName);
            result.put("size", String.valueOf(file.getSize()));
            result.put("type", contentType);

            return Result.success("上传成功", result);
        } catch (IOException e) {
            log.error("文件上传失败：{}", e.getMessage(), e);
            return Result.error("文件上传失败：" + e.getMessage());
        } catch (Exception e) {
            log.error("文件上传异常：{}", e.getMessage(), e);
            return Result.error("文件上传异常");
        }
    }

    @PostMapping("/files")
    public Result uploadFiles(@RequestParam("files") MultipartFile[] files) {
        try {
            if (files == null || files.length == 0) {
                return Result.error("文件不能为空");
            }

            List<Map<String, String>> uploadedFiles = new ArrayList<>();
            List<String> errors = new ArrayList<>();

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
                    if (!ALLOWED_FILE_TYPES.contains(contentType) && !ALLOWED_IMAGE_TYPES.contains(contentType)) {
                        errors.add("文件" + (i + 1) + "格式不支持");
                        continue;
                    }

                    String originalFilename = file.getOriginalFilename();
                    String fileExtension = getFileExtension(originalFilename);

                    String datePath = new SimpleDateFormat("yyyy/MM").format(new Date());
                    String fileName = UUID.randomUUID().toString() + "." + fileExtension;

                    // 根据文件类型选择 OSS 目录
                    String category = "files";
                    if (contentType != null) {
                        if (contentType.contains("pdf")) {
                            category = "resources/pdf";
                        } else if (contentType.contains("word") || contentType.contains("document")) {
                            category = "resources/doc";
                        } else if (contentType.contains("excel") || contentType.contains("spreadsheet")) {
                            category = "resources/excel";
                        } else if (contentType.contains("powerpoint") || contentType.contains("presentation")) {
                            category = "resources/ppt";
                        } else if (contentType.startsWith("image/")) {
                            category = "images";
                        }
                    }

                    // 上传到 OSS
                    String objectName = category + "/" + datePath + "/" + fileName;
                    String fileUrl = aliyunOSSOperator.uploadWithObjectName(file.getBytes(), objectName);

                    Map<String, String> fileInfo = new HashMap<>();
                    fileInfo.put("url", fileUrl);
                    fileInfo.put("filename", originalFilename);
                    fileInfo.put("storedName", fileName);
                    fileInfo.put("size", String.valueOf(file.getSize()));
                    fileInfo.put("type", contentType);

                    uploadedFiles.add(fileInfo);
                } catch (Exception e) {
                    log.error("文件{}上传失败：{}", i + 1, e.getMessage());
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
            log.error("批量上传失败：{}", e.getMessage(), e);
            return Result.error("批量上传失败：" + e.getMessage());
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
    public ResponseEntity<InputStreamResource> downloadFile(HttpServletRequest request) {
        try {
            // 获取请求路径中的文件 URL
            String path = (String) request.getAttribute(
                org.springframework.web.servlet.HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);

            if (path == null || path.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            // 从 OSS 下载文件
            byte[] fileContent = aliyunOSSOperator.downloadFile(path);

            String filename = path.substring(path.lastIndexOf('/') + 1);
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8.toString())
                    .replaceAll("\\+", "%20");

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + encodedFilename + "\"");
            headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE);

            log.info("从 OSS 下载文件：{}", path);
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(new InputStreamResource(new ByteArrayInputStream(fileContent)));
        } catch (Exception e) {
            log.error("下载文件失败：{}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 文件预览接口 - 支持 CORS 跨域访问
     * 用于 PDF.js 等前端库加载 OSS 文件
     */
    @GetMapping("/preview/**")
    @CrossOrigin(origins = "*")
    public ResponseEntity<byte[]> previewFile(HttpServletRequest request) {
        try {
            // 获取请求路径中的文件 URL
            String path = (String) request.getAttribute(
                org.springframework.web.servlet.HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);

            if (path == null || path.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            // 去掉 /api/upload/preview/ 前缀，获取实际的 OSS 路径
            String ossPath = path.replace("/api/upload/preview/", "");

            // 从 OSS 下载文件
            byte[] fileContent = aliyunOSSOperator.downloadFile(ossPath);

            // 根据文件扩展名设置 Content-Type
            String filename = ossPath.substring(ossPath.lastIndexOf('/') + 1);
            String contentType = getContentTypeForFilename(filename);

            log.info("预览文件：{}", ossPath);
            return ResponseEntity.ok()
                    .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
                    .header(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET, OPTIONS")
                    .header(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "*")
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .body(fileContent);
        } catch (Exception e) {
            log.error("预览文件失败：{}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 处理预检请求
     */
    @RequestMapping(value = "/preview/**", method = RequestMethod.OPTIONS)
    @CrossOrigin(origins = "*")
    public ResponseEntity<Void> handlePreflight(HttpServletRequest request) {
        return ResponseEntity.ok()
                .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
                .header(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET, OPTIONS")
                .header(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "*")
                .build();
    }

    /**
     * 根据文件名获取 Content-Type
     */
    private String getContentTypeForFilename(String filename) {
        if (filename == null) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        String lowerFilename = filename.toLowerCase();
        if (lowerFilename.endsWith(".pdf")) {
            return "application/pdf";
        } else if (lowerFilename.endsWith(".doc")) {
            return "application/msword";
        } else if (lowerFilename.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        } else if (lowerFilename.endsWith(".xls")) {
            return "application/vnd.ms-excel";
        } else if (lowerFilename.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        } else if (lowerFilename.endsWith(".ppt")) {
            return "application/vnd.ms-powerpoint";
        } else if (lowerFilename.endsWith(".pptx")) {
            return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        } else if (lowerFilename.endsWith(".jpg") || lowerFilename.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lowerFilename.endsWith(".png")) {
            return "image/png";
        } else if (lowerFilename.endsWith(".gif")) {
            return "image/gif";
        } else if (lowerFilename.endsWith(".bmp")) {
            return "image/bmp";
        } else if (lowerFilename.endsWith(".webp")) {
            return "image/webp";
        } else if (lowerFilename.endsWith(".txt")) {
            return "text/plain";
        } else if (lowerFilename.endsWith(".json")) {
            return "application/json";
        } else if (lowerFilename.endsWith(".xml")) {
            return "application/xml";
        } else if (lowerFilename.endsWith(".csv")) {
            return "text/csv";
        } else if (lowerFilename.endsWith(".mp4")) {
            return "video/mp4";
        } else if (lowerFilename.endsWith(".webm")) {
            return "video/webm";
        } else if (lowerFilename.endsWith(".mp3")) {
            return "audio/mpeg";
        } else if (lowerFilename.endsWith(".wav")) {
            return "audio/wav";
        } else {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
    }
}
