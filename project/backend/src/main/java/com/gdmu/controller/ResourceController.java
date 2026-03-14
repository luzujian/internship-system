package com.gdmu.controller;

import com.gdmu.entity.LearningResource;
import com.gdmu.entity.ResourceCategory;
import com.gdmu.service.ResourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 学习资源控制器
 */
@RestController
@RequestMapping("/api/resources")
public class ResourceController {
    
    @Autowired
    private ResourceService resourceService;
    
    private static final Logger log = LoggerFactory.getLogger(ResourceController.class);
    
    /**
     * 上传资源
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadResource(
            @RequestParam(required = false) MultipartFile file,
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam Long uploaderId,
            @RequestParam String uploaderRole) {
        try {
            LearningResource resource = new LearningResource();
            resource.setTitle(title);
            resource.setDescription(description);
            
            Long resourceId = resourceService.uploadResource(file, resource, uploaderId, uploaderRole);
            return ResponseEntity.ok(resourceId);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }
    
    /**
     * 审核资源
     */
    @PostMapping("/{id}/review")
    public ResponseEntity<?> reviewResource(
            @PathVariable Long id,
            @RequestBody ReviewRequest request) {
        try {
            resourceService.reviewResource(id, request.getApproved(), request.getReviewerId());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }
    
    /**
     * 语义搜索
     */
    @PostMapping("/search")
    public ResponseEntity<?> searchResources(
            @RequestBody SearchRequest request) {
        try {
            List<LearningResource> resources = resourceService.semanticSearch(request.getQuery());
            return ResponseEntity.ok(resources);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }
    
    /**
     * 获取资源详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getResource(@PathVariable Long id) {
        try {
            LearningResource resource = resourceService.getResourceById(id);
            if (resource == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("资源不存在");
            }
            return ResponseEntity.ok(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }
    
    /**
     * 获取待审核资源列表
     */
    @GetMapping("/pending")
    public ResponseEntity<?> getPendingResources() {
        try {
            List<LearningResource> resources = resourceService.getPendingResources();
            return ResponseEntity.ok(resources);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }
    
    /**
     * 获取已审核通过资源列表
     */
    @GetMapping("/approved")
    public ResponseEntity<?> getApprovedResources() {
        try {
            List<LearningResource> resources = resourceService.getApprovedResources();
            return ResponseEntity.ok(resources);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }
    
    /**
     * 获取所有资源分类
     */
    @GetMapping("/categories")
    public ResponseEntity<?> getCategories() {
        try {
            List<ResourceCategory> categories = resourceService.getAllCategories();
            return ResponseEntity.ok(categories);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }
    
    /**
     * 添加资源分类
     */
    @PostMapping("/categories")
    public ResponseEntity<?> addCategory(@RequestBody ResourceCategory category) {
        try {
            resourceService.addCategory(category);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }
    
    /**
     * 删除资源
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteResource(@PathVariable Long id) {
        try {
            resourceService.deleteResource(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }
    
    // 内部请求参数类
    static class ReviewRequest {
        private Boolean approved;
        private Long reviewerId;
        
        public Boolean getApproved() {
            return approved;
        }
        
        public void setApproved(Boolean approved) {
            this.approved = approved;
        }
        
        public Long getReviewerId() {
            return reviewerId;
        }
        
        public void setReviewerId(Long reviewerId) {
            this.reviewerId = reviewerId;
        }
    }
    
    static class SearchRequest {
        private String query;
        
        public String getQuery() {
            return query;
        }
        
        public void setQuery(String query) {
            this.query = query;
        }
    }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class AISearchRequest {
        private String query;
        private String fileType;
        private int page = 1;
        private int pageSize = 10;
        
        public String getQuery() {
            return query;
        }
        
        public void setQuery(String query) {
            this.query = query;
        }
        
        public String getFileType() {
            return fileType;
        }
        
        public void setFileType(String fileType) {
            this.fileType = fileType;
        }
        
        public int getPage() {
            return page;
        }
        
        public void setPage(int page) {
            this.page = page;
        }
        
        public int getPageSize() {
            return pageSize;
        }
        
        public void setPageSize(int pageSize) {
            this.pageSize = pageSize;
        }
    }
    
    /**
     * AI智能资源搜索
     */
    @PostMapping("/ai/search")
    public ResponseEntity<?> aiSearchResources(@RequestBody AISearchRequest request) {
        try {
            log.info("AI智能搜索请求: query={}, fileType={}, page={}, pageSize={}", 
                    request.getQuery(), request.getFileType(), request.getPage(), request.getPageSize());
            
            // 调用Service层进行AI智能搜索
            Object searchResult = resourceService.aiSmartSearch(
                    request.getQuery(), 
                    request.getFileType(), 
                    request.getPage(), 
                    request.getPageSize());
            
            return ResponseEntity.ok(searchResult);
        } catch (Exception e) {
            log.error("AI智能搜索失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("AI搜索服务暂时不可用");
        }
    }
    
    // AI聊天功能已迁移到AIChatController
}