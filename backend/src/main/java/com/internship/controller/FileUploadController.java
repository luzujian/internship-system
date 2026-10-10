package com.internship.controller;

import com.internship.anno.Log;
import com.internship.entity.Result;
import com.internship.utils.AliyunOSSOperator;
import com.internship.utils.CurrentHolder;
import com.internship.utils.LegacyWordPreviewConverter;
import com.internship.utils.OssObjectReadPolicy;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Semaphore;

@Slf4j
@RestController
@RequestMapping("/api/upload")
public class FileUploadController {
    private static final int MAX_LEGACY_DOC_PREVIEW_BYTES = 10 * 1024 * 1024;
    private static final int MAX_LEGACY_DOC_HTML_CHARS = 5 * 1024 * 1024;
    private static final byte[] OLE_SIGNATURE = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
            (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
    private static final Semaphore LEGACY_DOC_PREVIEW_SLOTS = new Semaphore(2);

    @Autowired
    private AliyunOSSOperator aliyunOSSOperator;

    @Autowired
    private LegacyWordPreviewConverter legacyWordPreviewConverter;

    /** The browser first obtains bytes from a record-scoped download route, then submits them here. */
    @PostMapping(value = "/preview-legacy-doc", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Log(module = "FILE", operationType = "SELECT", description = "预览旧版 Word 文档")
    public ResponseEntity<String> previewLegacyDoc(HttpServletRequest request) {
        if (request.getContentLengthLong() > MAX_LEGACY_DOC_PREVIEW_BYTES) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        }
        try (InputStream input = request.getInputStream()) {
            byte[] bytes = input.readNBytes(MAX_LEGACY_DOC_PREVIEW_BYTES + 1);
            if (bytes.length > MAX_LEGACY_DOC_PREVIEW_BYTES) {
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
            }
            if (bytes.length < OLE_SIGNATURE.length
                    || !Arrays.equals(Arrays.copyOf(bytes, OLE_SIGNATURE.length), OLE_SIGNATURE)) {
                return ResponseEntity.unprocessableEntity().build();
            }
            if (!LEGACY_DOC_PREVIEW_SLOTS.tryAcquire()) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
            }
            try {
                String html = legacyWordPreviewConverter.convertToHtml(bytes);
                if (html.length() > MAX_LEGACY_DOC_HTML_CHARS) {
                    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
                }
                return ResponseEntity.ok()
                        .header("X-Content-Type-Options", "nosniff")
                        .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                        .body(html);
            } finally {
                LEGACY_DOC_PREVIEW_SLOTS.release();
            }
        } catch (IOException e) {
            log.warn("旧版 Word 预览解析失败: {}", e.getClass().getSimpleName());
            return ResponseEntity.unprocessableEntity().build();
        }
    }

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
            "application/x-7z-compressed",
            "video/mp4",
            "video/webm",
            "video/ogg",
            "video/quicktime",
            "video/x-msvideo",
            "video/x-ms-wmv"
    ));

    @PostMapping("/company-media/{kind}")
    @PreAuthorize("hasRole('COMPANY')")
    @Log(operationType = "UPLOAD", module = "FILE_ACCESS", description = "上传企业公开媒体", riskLevel = "MEDIUM")
    public Result uploadCompanyPublicMedia(@PathVariable String kind, @RequestParam("file") MultipartFile file) {
        Long companyId = CurrentHolder.getUserId();
        if (!"ROLE_COMPANY".equals(CurrentHolder.getUserRole()) || companyId == null || companyId <= 0) {
            throw new AccessDeniedException("企业身份不可用");
        }
        if (!Set.of("logo", "photo", "video").contains(kind)) {
            return Result.error("不支持的企业媒体类型");
        }
        try {
            if (file.isEmpty() || file.getSize() > maxSize) {
                return Result.error("文件为空或超过大小限制");
            }
            String contentType = file.getContentType();
            boolean image = !"video".equals(kind);
            if (image && (!ALLOWED_IMAGE_TYPES.contains(contentType)
                    || !matchesImageSignature(file, contentType))) {
                return Result.error("图片内容格式不匹配");
            }
            if (!image && (contentType == null || !contentType.startsWith("video/")
                    || !ALLOWED_FILE_TYPES.contains(contentType))) {
                return Result.error("只支持允许的视频格式");
            }
            String extension = safeExtensionForContentType(contentType);
            String objectName = "company/media/" + companyId + "/" + kind + "/"
                    + new SimpleDateFormat("yyyy/MM").format(new Date()) + "/"
                    + UUID.randomUUID() + "." + extension;
            String url = aliyunOSSOperator.uploadPublicMediaWithObjectName(file.getBytes(), objectName);
            Map<String, String> uploaded = new HashMap<>();
            uploaded.put("url", url);
            uploaded.put("filename", file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
            uploaded.put("size", String.valueOf(file.getSize()));
            Map<String, Object> response = new HashMap<>();
            response.put("url", url);
            response.put("uploadedFiles", List.of(uploaded));
            response.put("errors", List.of());
            response.put("total", 1);
            response.put("success", 1);
            response.put("failed", 0);
            return Result.success("上传成功", response);
        } catch (Exception e) {
            log.error("企业公开媒体上传失败：kind={}", kind, e);
            return Result.error("上传失败");
        }
    }

    @PostMapping("/image")
    @Log(operationType = "UPLOAD", module = "FILE_ACCESS", description = "上传图片", riskLevel = "LOW")
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
            if (!matchesImageSignature(file, contentType)) {
                return Result.error("图片内容格式不匹配");
            }

            String fileExtension = safeExtensionForContentType(contentType);

            String datePath = new SimpleDateFormat("yyyy/MM").format(new Date());
            String fileName = UUID.randomUUID().toString() + "." + fileExtension;

            // 上传到 OSS 的 images 目录
            String objectName = "images/" + datePath + "/" + fileName;
            String fileUrl = aliyunOSSOperator.uploadWithObjectName(file.getBytes(), objectName);

            log.info("图片上传 OSS 成功: bytes={}", file.getSize());

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
    @Log(operationType = "UPLOAD", module = "FILE_ACCESS", description = "批量上传图片", riskLevel = "MEDIUM")
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
                    if (!matchesImageSignature(file, contentType)) {
                        errors.add("文件" + (i + 1) + "图片内容格式不匹配");
                        continue;
                    }

                    String fileExtension = safeExtensionForContentType(contentType);

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
            String path = java.net.URI.create(url).getPath();
            if (path != null && (path.startsWith("internship-agreement/") || path.contains("/internship-agreement/"))) {
                return Result.error("实习协议须通过协议入口管理，已提交文件须保留历史");
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
    @Log(operationType = "UPLOAD", module = "FILE_ACCESS", description = "上传文件", riskLevel = "MEDIUM")
    public Result uploadFile(@RequestParam("file") MultipartFile file) {
        return uploadFileInternal(file, false);
    }

    @Autowired
    private com.internship.service.AnnouncementAttachmentPolicy announcementAttachmentPolicy;

    @PostMapping("/announcement-file")
    @PreAuthorize("hasAuthority('announcement:add') or hasAuthority('announcement:edit')")
    @Log(operationType = "UPLOAD", module = "FILE_ACCESS", description = "上传公告附件", riskLevel = "MEDIUM")
    public Result uploadAnnouncementFile(@RequestParam("file") MultipartFile file) {
        return uploadFileInternal(file, true);
    }

    private Result uploadFileInternal(MultipartFile file, boolean announcement) {
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
            if (ALLOWED_IMAGE_TYPES.contains(contentType) && !matchesImageSignature(file, contentType)) {
                return Result.error("图片内容格式不匹配");
            }

            String originalFilename = file.getOriginalFilename();
            String fileExtension = safeExtensionForContentType(contentType);

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
                } else if (contentType.startsWith("video/")) {
                    category = "videos";
                }
            }

            // 上传到 OSS
            String objectName = applicationUploadPath(category, datePath, fileName);
            String fileUrl = aliyunOSSOperator.uploadWithObjectName(file.getBytes(), objectName);

            log.info("文件上传 OSS 成功: bytes={}", file.getSize());

            Map<String, String> result = new HashMap<>();
            result.put("url", fileUrl);
            result.put("filename", originalFilename);
            result.put("storedName", fileName);
            result.put("size", String.valueOf(file.getSize()));
            result.put("type", contentType);
            if (announcement) {
                result.put("receipt", announcementAttachmentPolicy.issue(originalFilename, fileUrl));
            }

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
    @Log(operationType = "UPLOAD", module = "FILE_ACCESS", description = "批量上传文件", riskLevel = "MEDIUM")
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
                    if (ALLOWED_IMAGE_TYPES.contains(contentType) && !matchesImageSignature(file, contentType)) {
                        errors.add("文件" + (i + 1) + "图片内容格式不匹配");
                        continue;
                    }

                    String originalFilename = file.getOriginalFilename();
                    String fileExtension = safeExtensionForContentType(contentType);

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
                        } else if (contentType.startsWith("video/")) {
                            category = "videos";
                        }
                    }

                    // 上传到 OSS
                    String objectName = applicationUploadPath(category, datePath, fileName);
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

    private String applicationUploadPath(String category, String datePath, String fileName) {
        if ("ROLE_STUDENT".equals(CurrentHolder.getUserRole())) {
            Long studentId = CurrentHolder.getUserId();
            if (studentId == null || studentId <= 0) throw new IllegalStateException("学生身份不可用");
            return "student/applications/" + studentId + "/" + category + "/" + datePath + "/" + fileName;
        }
        return category + "/" + datePath + "/" + fileName;
    }

    private boolean matchesImageSignature(MultipartFile file, String contentType) throws IOException {
        byte[] bytes = file.getBytes();
        if (bytes.length < 4) return false;
        return switch (contentType) {
            case "image/jpeg", "image/jpg" -> (bytes[0] & 0xff) == 0xff
                    && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
            case "image/png" -> bytes.length >= 8 && (bytes[0] & 0xff) == 0x89
                    && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G'
                    && bytes[4] == 13 && bytes[5] == 10 && bytes[6] == 26 && bytes[7] == 10;
            case "image/gif" -> bytes.length >= 6 && bytes[0] == 'G' && bytes[1] == 'I'
                    && bytes[2] == 'F' && bytes[3] == '8'
                    && (bytes[4] == '7' || bytes[4] == '9') && bytes[5] == 'a';
            case "image/bmp" -> bytes[0] == 'B' && bytes[1] == 'M';
            case "image/webp" -> bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I'
                    && bytes[2] == 'F' && bytes[3] == 'F' && bytes[8] == 'W'
                    && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
            default -> false;
        };
    }

    private String safeExtensionForContentType(String contentType) {
        // The client-controlled filename must never decide the stored extension.
        // These types have already passed the upload allowlist above.
        return switch (contentType) {
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/bmp" -> "bmp";
            case "image/webp" -> "webp";
            case "application/pdf" -> "pdf";
            case "application/msword" -> "doc";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx";
            case "application/vnd.ms-excel" -> "xls";
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx";
            case "application/vnd.ms-powerpoint" -> "ppt";
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "pptx";
            case "text/plain" -> "txt";
            case "application/zip" -> "zip";
            case "application/x-rar-compressed" -> "rar";
            case "application/x-7z-compressed" -> "7z";
            case "video/mp4" -> "mp4";
            case "video/webm" -> "webm";
            case "video/ogg" -> "ogv";
            case "video/quicktime" -> "mov";
            case "video/x-msvideo" -> "avi";
            case "video/x-ms-wmv" -> "wmv";
            default -> throw new IllegalArgumentException("不支持的文件格式");
        };
    }

    @Log(operationType = "DOWNLOAD", module = "FILE_ACCESS", description = "下载文件", riskLevel = "MEDIUM")
    @GetMapping("/download/**")
    public ResponseEntity<InputStreamResource> downloadFile(HttpServletRequest request) {
        try {
            // 获取请求路径中的文件 URL
            String path = (String) request.getAttribute(
                org.springframework.web.servlet.HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);

            if (path == null || path.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            // 路径穿越防护：拒绝包含 ../ 或编码后的路径穿越字符
            if (path.contains("..") || path.contains("./") || path.contains(".\\")
                    || path.contains("%2e%2e") || path.contains("%2E%2E")) {
                log.warn("检测到路径穿越攻击企图：{}", path);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            String ossPath = path.replace("/api/upload/download/", "");
            if (!OssObjectReadPolicy.mayReadGeneric(ossPath)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            // 从 OSS 下载文件
            byte[] fileContent = aliyunOSSOperator.downloadFile(ossPath);

            String filename = path.substring(path.lastIndexOf('/') + 1);
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8.toString())
                    .replaceAll("\\+", "%20");

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + encodedFilename + "\"");
            headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE);

            log.info("下载上传文件: bytes={}", fileContent.length);
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
    @Log(operationType = "READ", module = "FILE_ACCESS", description = "预览文件（敏感读取）", riskLevel = "MEDIUM")
    @GetMapping("/preview/**")
    public ResponseEntity<byte[]> previewFile(HttpServletRequest request) {
        try {
            // 获取请求路径中的文件 URL
            String path = (String) request.getAttribute(
                org.springframework.web.servlet.HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);

            if (path == null || path.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            // 路径穿越防护
            if (path.contains("..") || path.contains("./") || path.contains(".\\")
                    || path.contains("%2e%2e") || path.contains("%2E%2E")) {
                log.warn("检测到路径穿越攻击企图(preview)：{}", path);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            // 去掉 /api/upload/preview/ 前缀，获取实际的 OSS 路径
            String ossPath = path.replace("/api/upload/preview/", "");
            if (!OssObjectReadPolicy.mayReadGeneric(ossPath)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            // 从 OSS 下载文件
            byte[] fileContent = aliyunOSSOperator.downloadFile(ossPath);

            // 根据文件扩展名设置 Content-Type
            String filename = ossPath.substring(ossPath.lastIndexOf('/') + 1);
            String contentType = getContentTypeForFilename(filename);

            log.info("预览上传文件: contentType={}, bytes={}", contentType, fileContent.length);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .header("X-Content-Type-Options", "nosniff")
                    .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                    .body(fileContent);
        } catch (Exception e) {
            log.error("预览文件失败：{}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Word 文件预览接口：.doc 转为 PDF，.docx 直接返回。
     */
    @Log(operationType = "READ", module = "FILE_ACCESS", description = "预览Word文档（敏感读取）", riskLevel = "MEDIUM")
    @GetMapping("/preview-docx/**")
    public ResponseEntity<byte[]> previewDocxFile(HttpServletRequest request) {
        try {
            String path = (String) request.getAttribute(
                org.springframework.web.servlet.HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);

            if (path == null || path.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            if (path.contains("..") || path.contains("./") || path.contains(".\\")
                    || path.contains("%2e%2e") || path.contains("%2E%2E")) {
                log.warn("检测到路径穿越攻击企图(preview-docx)：{}", path);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            String ossPath = path.replace("/api/upload/preview-docx/", "");
            if (!OssObjectReadPolicy.mayReadGeneric(ossPath)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            byte[] fileContent = aliyunOSSOperator.downloadFile(ossPath);
            String filename = ossPath.substring(ossPath.lastIndexOf('/') + 1);

            // 旧版 .doc 使用 LibreOffice 转为 PDF，避免丢失原始排版
            if (filename.toLowerCase().endsWith(".doc") && !filename.toLowerCase().endsWith(".docx")) {
                byte[] convertedBytes = legacyWordPreviewConverter.convertToPdf(fileContent);
                log.info("预览 .doc 转 PDF: bytes={}", convertedBytes.length);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                        .header("X-Content-Type-Options", "nosniff")
                        .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                        .body(convertedBytes);
            }

            // .docx 直接返回
            log.info("预览 docx: bytes={}", fileContent.length);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                    .header("X-Content-Type-Options", "nosniff")
                    .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                    .body(fileContent);
        } catch (Exception e) {
            log.error("预览 docx 文件失败：{}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 处理预检请求
     */
    @RequestMapping(value = {"/preview/**", "/preview-docx/**"}, method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> handlePreflight(HttpServletRequest request) {
        return ResponseEntity.ok()
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
