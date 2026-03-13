package com.gdmu.mapper;

import com.gdmu.entity.LearningResource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 学习资源Mapper
 */
@Mapper
public interface LearningResourceMapper {
    
    /**
     * 插入学习资源
     */
    int insert(LearningResource resource);
    
    /**
     * 根据ID删除学习资源
     */
    int deleteById(Long id);
    
    /**
     * 更新学习资源
     */
    int updateById(LearningResource resource);
    
    /**
     * 根据ID查询学习资源
     */
    LearningResource selectById(Long id);
    
    /**
     * 查询所有学习资源
     */
    List<LearningResource> selectList();
    
    /**
     * 根据状态查询资源
     */
    List<LearningResource> selectByStatus(@Param("status") String status);
    
    /**
     * 根据上传者ID查询资源
     */
    List<LearningResource> selectByUploaderId(@Param("uploaderId") Long uploaderId);
    
    /**
     * 根据上传者角色查询资源
     */
    List<LearningResource> selectByUploaderRole(@Param("uploaderRole") String uploaderRole);
    
    /**
     * 语义搜索相似资源
     */
    List<LearningResource> findSimilarResources(Map<String, Object> params);
    
    /**
     * 根据向量和类型查询相似资源（分页）
     */
    List<LearningResource> findSimilarResourcesByType(Map<String, Object> params);
    
    /**
     * 统计相似资源数量
     */
    long countSimilarResourcesByType(Map<String, Object> params);
    
    /**
     * 根据类型和状态查询资源（分页）
     */
    List<LearningResource> selectByTypeAndStatus(
            @Param("fileType") String fileType,
            @Param("status") String status,
            @Param("offset") int offset,
            @Param("limit") int limit);
    
    /**
     * 统计指定类型和状态的资源数量
     */
    int countByTypeAndStatus(
            @Param("fileType") String fileType,
            @Param("status") String status);
}