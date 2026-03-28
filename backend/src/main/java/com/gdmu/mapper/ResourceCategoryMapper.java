package com.gdmu.mapper;

import com.gdmu.entity.ResourceCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 资源分类Mapper
 */
@Mapper
public interface ResourceCategoryMapper {
    
    /**
     * 插入资源分类
     */
    int insert(ResourceCategory category);
    
    /**
     * 根据ID删除资源分类
     */
    int deleteById(Long id);
    
    /**
     * 更新资源分类
     */
    int updateById(ResourceCategory category);
    
    /**
     * 根据ID查询资源分类
     */
    ResourceCategory selectById(Long id);
    
    /**
     * 查询所有资源分类
     */
    List<ResourceCategory> selectList();
    
    /**
     * 根据父分类ID查询子分类
     */
    List<ResourceCategory> selectByParentId(@Param("parentId") Long parentId);
    
    /**
     * 查询所有顶级分类
     */
    List<ResourceCategory> selectTopCategories();
}