package com.gdmu.service;

import com.gdmu.entity.LearningResource;
import com.gdmu.entity.ResourceCategory;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 学习资源服务接口
 */
public interface ResourceService {
    
    /**
     * 上传资源
     */
    Long uploadResource(MultipartFile file, LearningResource resource, Long uploaderId, String uploaderRole);
    
    /**
     * 审核资源
     */
    void reviewResource(Long resourceId, Boolean approved, Long reviewerId);
    
    /**
     * 语义搜索资源
     */
    List<LearningResource> semanticSearch(String query);
    
    /**
     * 根据ID获取资源
     */
    LearningResource getResourceById(Long id);
    
    /**
     * 获取待审核资源列表
     */
    List<LearningResource> getPendingResources();
    
    /**
     * 获取已审核资源列表
     */
    List<LearningResource> getApprovedResources();
    
    /**
     * 获取已审核资源列表（支持分页）
     * @param page 页码
     * @param pageSize 每页数量
     * @return 分页结果对象，包含list和total
     */
    Object getApprovedResources(Integer page, Integer pageSize);
    
    /**
     * 获取已发布资源列表（支持分页）
     * @param page 页码
     * @param pageSize 每页数量
     * @return 分页结果对象，包含list和total
     */
    Object getPublishedResources(Integer page, Integer pageSize);
    
    /**
     * 获取用户上传的资源
     */
    List<LearningResource> getResourcesByUploaderId(Long uploaderId);
    
    /**
     * 获取所有资源分类
     */
    List<ResourceCategory> getAllCategories();
    
    /**
     * 添加资源分类
     */
    void addCategory(ResourceCategory category);
    
    /**
     * 删除资源
     */
    void deleteResource(Long id);
    
    /**
     * 更新资源
     */
    void updateResource(LearningResource resource);
    
    /**
     * AI智能资源搜索
     * @param query 搜索查询
     * @param resourceType 资源类型过滤
     * @param page 页码
     * @param pageSize 每页数量
     * @return 搜索结果
     */
    Object aiSmartSearch(String query, String fileType, int page, int pageSize);
}