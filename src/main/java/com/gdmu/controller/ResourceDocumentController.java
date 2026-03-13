package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.entity.PageResult;
import com.gdmu.entity.Result;
import com.gdmu.entity.ResourceDocument;
import com.gdmu.service.ResourceDocumentService;
import com.gdmu.utils.AliyunOSSOperator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/resource-documents")
public class ResourceDocumentController {

    @Autowired
    private ResourceDocumentService resourceDocumentService;

    @Autowired
    private AliyunOSSOperator aliyunOSSOperator;

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
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/gif",
            "image/bmp",
            "image/webp"
    ));

    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_TEACHER', 'ROLE_STUDENT')")
    public Result getAllResourceDocuments() {
        log.info("获取所有资源文档列表");
        try {
            List<ResourceDocument> resourceDocuments = resourceDocumentService.findAll();
            return Result.success(resourceDocuments);
        } catch (Exception e) {
            log.error("获取资源文档列表失败：{}", e.getMessage(), e);
            return Result.error("获取资源文档列表失败：" + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_TEACHER', 'ROLE_STUDENT')")
    public Result getResourceDocumentById(@PathVariable Long id) {
        log.info("根据 ID 获取资源文档：{}", id);
        try {
            ResourceDocument resourceDocument = resourceDocumentService.findById(id);
            if (resourceDocument == null) {
                return Result.error("资源文档不存在");
            }
            resourceDocumentService.incrementViewCount(id);
            return Result.success(resourceDocument);
        } catch (Exception e) {
            log.error("获取资源文档详情失败：{}", e.getMessage(), e);
            return Result.error("获取资源文档详情失败：" + e.getMessage());
        }
    }

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('resource:view')")
    public Result getResourceDocumentsByPage(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String publisherRole) {
        log.info("分页查询资源文档，页码：{}, 每页大小：{}, 标题：{}, 状态：{}, 发布人身份：{}",
                page, pageSize, title, status, publisherRole);
        try {
            return Result.success(resourceDocumentService.findPage(page, pageSize, title, status, publisherRole));
        } catch (Exception e) {
            log.error("分页查询资源文档失败：{}", e.getMessage(), e);
            return Result.error("分页查询资源文档失败：" + e.getMessage());
        }
    }

    @PostMapping
    @Log(operationType = "ADD", module = "RESOURCE_DOCUMENT_MANAGEMENT", description = "新增资源文档")
    @PreAuthorize("hasAuthority('resource:add')")
    public Result addResourceDocument(@RequestBody ResourceDocument resourceDocument) {
        log.info("新增资源文档：{}", resourceDocument.getTitle());
        try {
            int result = resourceDocumentService.insert(resourceDocument);
            return Result.success("添加资源文档成功", result);
        } catch (Exception e) {
            log.error("添加资源文档失败：{}", e.getMessage(), e);
            return Result.error("添加资源文档失败：" + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @Log(operationType = "UPDATE", module = "RESOURCE_DOCUMENT_MANAGEMENT", description = "更新资源文档")
    @PreAuthorize("hasAuthority('resource:edit')")
    public Result updateResourceDocument(@PathVariable Long id, @RequestBody ResourceDocument resourceDocument) {
        log.info("更新资源文档：ID={}", id);
        try {
            resourceDocument.setId(id);
            int result = resourceDocumentService.update(resourceDocument);
            return Result.success("更新资源文档成功", result);
        } catch (Exception e) {
            log.error("更新资源文档失败：{}", e.getMessage(), e);
            return Result.error("更新资源文档失败：" + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @Log(operationType = "DELETE", module = "RESOURCE_DOCUMENT_MANAGEMENT", description = "删除资源文档")
    @PreAuthorize("hasAuthority('resource:delete')")
    public Result deleteResourceDocument(@PathVariable Long id) {
        log.info("删除资源文档：ID={}", id);
        try {
            // 先获取资源文档信息，删除 OSS 中的文件
            ResourceDocument resourceDocument = resourceDocumentService.findById(id);
            if (resourceDocument != null && resourceDocument.getFileUrl() != null) {
                try {
                    aliyunOSSOperator.deleteFile(resourceDocument.getFileUrl());
                    log.info("已删除 OSS 中的文件：{}", resourceDocument.getFileUrl());
                } catch (Exception e) {
                    log.error("删除 OSS 文件失败：{}", e.getMessage());
                }
            }

            int result = resourceDocumentService.delete(id);
            return Result.success("删除资源文档成功", result);
        } catch (Exception e) {
            log.error("删除资源文档失败：{}", e.getMessage(), e);
            return Result.error("删除资源文档失败：" + e.getMessage());
        }
    }

    @DeleteMapping("/batch")
    @Log(operationType = "DELETE", module = "RESOURCE_DOCUMENT_MANAGEMENT", description = "批量删除资源文档")
    @PreAuthorize("hasAuthority('resource:delete')")
    public Result batchDeleteResourceDocument(@RequestBody Map<String, List<Long>> request) {
        List<Long> ids = request.get("ids");
        log.info("批量删除资源文档，IDs: {}", ids);
        try {
            if (ids == null || ids.isEmpty()) {
                return Result.error("资源文档 ID 列表不能为空");
            }

            // 批量删除时，先删除 OSS 中的文件
            for (Long id : ids) {
                ResourceDocument resourceDocument = resourceDocumentService.findById(id);
                if (resourceDocument != null && resourceDocument.getFileUrl() != null) {
                    try {
                        aliyunOSSOperator.deleteFile(resourceDocument.getFileUrl());
                    } catch (Exception e) {
                        log.error("删除 OSS 文件失败，ID={}: {}", id, e.getMessage());
                    }
                }
            }

            int result = resourceDocumentService.batchDelete(ids);
            return Result.success("批量删除资源文档成功", result);
        } catch (Exception e) {
            log.error("批量删除资源文档失败：{}", e.getMessage(), e);
            return Result.error("批量删除资源文档失败：" + e.getMessage());
        }
    }

    @PostMapping("/upload")
    @Log(operationType = "ADD", module = "RESOURCE_DOCUMENT_MANAGEMENT", description = "上传资源文档")
    @PreAuthorize("hasAuthority('resource:add')")
    public Result uploadResourceDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("publisher") String publisher,
            @RequestParam("publisherRole") String publisherRole,
            @RequestParam(value = "targetType", defaultValue = "ALL") String targetType,
            @RequestParam(value = "status", defaultValue = "PUBLISHED") String status) {
        log.info("上传资源文档：标题={}, 文件名={}", title, file.getOriginalFilename());
        try {
            if (file.isEmpty()) {
                return Result.error("文件不能为空");
            }

            String contentType = file.getContentType();
            if (!ALLOWED_FILE_TYPES.contains(contentType)) {
                return Result.error("不支持的文件类型，仅支持 PDF、Word、Excel、PPT、TXT、ZIP、RAR、7Z 等格式");
            }

            String originalFilename = file.getOriginalFilename();
            String fileExtension = getFileExtension(originalFilename);

            String datePath = new SimpleDateFormat("yyyy/MM").format(new Date());
            String fileName = UUID.randomUUID().toString() + "." + fileExtension;

            // 上传到 OSS 的 resources/doc 目录
            String objectName = "resources/doc/" + datePath + "/" + fileName;
            String fileUrl = aliyunOSSOperator.uploadWithObjectName(file.getBytes(), objectName);

            ResourceDocument resourceDocument = new ResourceDocument();
            resourceDocument.setTitle(title);
            resourceDocument.setDescription(description);
            resourceDocument.setFileUrl(fileUrl);
            resourceDocument.setFileName(originalFilename);
            resourceDocument.setFileType(contentType);
            resourceDocument.setFileSize(file.getSize());
            resourceDocument.setPublisher(publisher);
            resourceDocument.setPublisherRole(publisherRole);
            resourceDocument.setTargetType(targetType);
            resourceDocument.setStatus(status);

            int result = resourceDocumentService.insert(resourceDocument);

            log.info("资源文档上传成功，文件 URL: {}", fileUrl);
            return Result.success("上传成功", result);
        } catch (Exception e) {
            log.error("上传资源文档失败：{}", e.getMessage(), e);
            return Result.error("上传资源文档失败：" + e.getMessage());
        }
    }

    @GetMapping("/download/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_TEACHER', 'ROLE_STUDENT')")
    public ResponseEntity<InputStreamResource> downloadResourceDocument(@PathVariable Long id) {
        log.info("下载资源文档：ID={}", id);
        try {
            ResourceDocument resourceDocument = resourceDocumentService.findById(id);
            if (resourceDocument == null) {
                return ResponseEntity.notFound().build();
            }

            if (resourceDocument.getFileUrl() == null || resourceDocument.getFileUrl().isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            // 从 OSS 下载文件
            byte[] fileContent = aliyunOSSOperator.downloadFile(resourceDocument.getFileUrl());

            resourceDocumentService.incrementDownloadCount(id);

            String encodedFilename = URLEncoder.encode(resourceDocument.getFileName(), StandardCharsets.UTF_8.toString())
                    .replaceAll("\\+", "%20");

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + encodedFilename + "\"");
            headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(new InputStreamResource(new ByteArrayInputStream(fileContent)));
        } catch (Exception e) {
            log.error("下载资源文档失败：{}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_TEACHER', 'ROLE_STUDENT')")
    public Result searchResourceDocuments(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String publisherRole) {
        log.info("搜索资源文档，标题：{}, 状态：{}, 发布人身份：{}", title, status, publisherRole);
        try {
            List<ResourceDocument> resourceDocuments = resourceDocumentService.list(title, status, publisherRole);
            return Result.success(resourceDocuments);
        } catch (Exception e) {
            log.error("搜索资源文档失败：{}", e.getMessage(), e);
            return Result.error("搜索资源文档失败：" + e.getMessage());
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }

        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "";
        }

        return filename.substring(lastDotIndex + 1).toLowerCase();
    }
}
