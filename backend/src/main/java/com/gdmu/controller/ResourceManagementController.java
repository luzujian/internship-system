package com.gdmu.controller;

import com.gdmu.entity.Resource;
import com.gdmu.entity.Result;
import com.gdmu.service.ResourceManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/file-management")
public class ResourceManagementController {
    
    private static final Logger logger = LoggerFactory.getLogger(ResourceManagementController.class);
    
    @Autowired
    private ResourceManagementService resourceManagementService;
    
    @GetMapping("/list")
    public Result getResourceList(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "8") int pageSize) {
        try {
            Map<String, Object> result = resourceManagementService.getResourcePage(type, keyword, page, pageSize);
            return Result.success("查询成功", result);
        } catch (Exception e) {
            logger.error("获取资源列表失败: {}", e.getMessage(), e);
            return Result.error("获取资源列表失败");
        }
    }
    
    @GetMapping("/detail/{id}")
    public Result getResourceDetail(@PathVariable Long id) {
        try {
            Resource resource = resourceManagementService.getResourceById(id);
            if (resource == null) {
                return Result.error("资源不存在");
            }
            return Result.success("查询成功", resource);
        } catch (Exception e) {
            logger.error("获取资源详情失败: {}", e.getMessage(), e);
            return Result.error("获取资源详情失败");
        }
    }
    
    @PostMapping("/upload")
    public Result uploadResource(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam("type") String type,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "uploaderId", defaultValue = "1") Long uploaderId,
            @RequestParam(value = "uploaderRole", defaultValue = "TEACHER") String uploaderRole,
            @RequestParam(value = "uploaderName", defaultValue = "管理员") String uploaderName) {
        try {
            Resource resource = new Resource();
            resource.setName(name);
            resource.setType(type);
            resource.setDescription(description);
            
            Long resourceId = resourceManagementService.uploadResource(file, resource, uploaderId, uploaderRole, uploaderName);
            
            Map<String, Object> result = new HashMap<>();
            result.put("id", resourceId);
            
            return Result.success("上传成功", result);
        } catch (Exception e) {
            logger.error("上传资源失败: {}", e.getMessage(), e);
            return Result.error("上传资源失败: " + e.getMessage());
        }
    }
    
    @PutMapping("/update")
    public Result updateResource(@RequestBody Resource resource) {
        try {
            resourceManagementService.updateResource(resource);
            return Result.success("更新成功");
        } catch (Exception e) {
            logger.error("更新资源失败: {}", e.getMessage(), e);
            return Result.error("更新资源失败");
        }
    }
    
    @DeleteMapping("/delete/{id}")
    public Result deleteResource(@PathVariable Long id) {
        try {
            resourceManagementService.deleteResource(id);
            return Result.success("删除成功");
        } catch (Exception e) {
            logger.error("删除资源失败: {}", e.getMessage(), e);
            return Result.error("删除资源失败");
        }
    }
    
    @GetMapping("/download/{id}")
    public ResponseEntity<org.springframework.core.io.Resource> downloadResource(@PathVariable Long id) {
        try {
            Resource resource = resourceManagementService.getResourceById(id);
            if (resource == null || resource.getFileUrl() == null) {
                return ResponseEntity.notFound().build();
            }
            
            resourceManagementService.incrementDownloadCount(id);
            
            String filePath = resource.getFileUrl().replace("/uploads", "D:/Web-work/Internship/uploads");
            Path path = Paths.get(filePath);
            File file = path.toFile();
            
            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }
            
            org.springframework.core.io.Resource springResource = new FileSystemResource(file);
            
            String contentType = Files.probeContentType(path);
            if (contentType == null) {
                contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getName() + "\"")
                    .body(springResource);
        } catch (Exception e) {
            logger.error("下载资源失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/preview/{id}")
    public Result previewResource(@PathVariable Long id) {
        try {
            Resource resource = resourceManagementService.getResourceById(id);
            if (resource == null) {
                return Result.error("资源不存在");
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("id", resource.getId());
            result.put("name", resource.getName());
            result.put("type", resource.getType());
            result.put("description", resource.getDescription());
            result.put("fileUrl", resource.getFileUrl());
            result.put("content", resource.getContent());
            result.put("uploadTime", resource.getCreateTime());
            result.put("size", formatFileSize(resource.getFileSize()));
            result.put("downloadCount", resource.getDownloadCount());
            result.put("uploader", resource.getUploaderName());
            
            return Result.success("查询成功", result);
        } catch (Exception e) {
            logger.error("预览资源失败: {}", e.getMessage(), e);
            return Result.error("预览资源失败");
        }
    }
    
    private String formatFileSize(Long size) {
        if (size == null) {
            return "0B";
        }
        if (size < 1024) {
            return size + "B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2fKB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2fMB", size / (1024.0 * 1024));
        } else {
            return String.format("%.2fGB", size / (1024.0 * 1024 * 1024));
        }
    }
}
