package com.gdmu.mapper;

import com.gdmu.entity.AlgorithmParameter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AlgorithmParameterMapper {
    
    // 根据课程ID和作业ID获取算法参数
    AlgorithmParameter selectByCourseIdAndAssignmentId(@Param("courseId") Long courseId, @Param("assignmentId") Long assignmentId);
    
    // 根据ID获取算法参数
    AlgorithmParameter selectById(@Param("id") Long id);
    
    // 插入算法参数
    int insert(AlgorithmParameter algorithmParameter);
    
    // 更新算法参数
    int update(AlgorithmParameter algorithmParameter);
    
    // 根据ID删除算法参数
    int deleteById(@Param("id") Long id);
}