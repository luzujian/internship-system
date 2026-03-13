package com.gdmu.mapper;

import com.gdmu.entity.CourseMajor;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/**
 * 课程与专业关联的Mapper接口
 */
@Mapper
public interface CourseMajorMapper {
    
    /**
     * 保存课程专业关联
     */
    int save(CourseMajor courseMajor);
    
    /**
     * 批量保存课程专业关联
     */
    int batchSave(List<CourseMajor> courseMajorList);
    
    /**
     * 根据课程ID删除所有关联
     */
    int deleteByCourseId(Long courseId);
    
    /**
     * 根据课程ID查询所有关联的专业
     */
    List<CourseMajor> findByCourseId(Long courseId);
    
    /**
     * 根据专业ID查询所有关联的课程
     */
    List<CourseMajor> findByMajorId(Long majorId);
    
    /**
     * 根据课程ID和专业ID查询是否存在关联
     */
    CourseMajor findByCourseIdAndMajorId(Long courseId, Long majorId);
}