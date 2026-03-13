package com.gdmu.mapper;

import com.gdmu.entity.Major;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MajorMapper {
    // 根据ID查询专业
    Major findById(Long id);
    
    // 查询所有专业
    List<Major> findAll();
    
    // 根据名称搜索专业
    List<Major> findByName(@Param("name") String name);
    
    // 根据院系ID查询专业
    List<Major> findByDepartmentId(@Param("departmentId") Long departmentId);
    
    // 新增专业
    int insert(Major major);
    
    // 更新专业
    int update(Major major);
    
    // 删除专业
    int delete(Long id);
    
    // 根据专业ID查询教师人数
    Integer getTeacherCountByMajorId(Long majorId);
    
    // 根据专业ID查询学生人数
    Integer getStudentCountByMajorId(Long majorId);
    
    // 更新专业人数信息
    int updateMajorCount(@Param("id") Long id, @Param("teacherCount") Integer teacherCount, @Param("studentCount") Integer studentCount);
    
    // 根据专业ID查询已确定实习的学生数
    Integer getConfirmedCountByMajorId(Long majorId);
    
    // 根据专业ID查询未找到实习的学生数
    Integer getNotFoundCountByMajorId(Long majorId);
    
    // 根据专业ID查询有Offer未确定的学生数
    Integer getHasOfferCountByMajorId(Long majorId);
    
    // 更新专业实习状态统计信息
    int updateMajorInternshipCount(@Param("id") Long id, @Param("confirmedCount") Integer confirmedCount, @Param("notFoundCount") Integer notFoundCount, @Param("hasOfferCount") Integer hasOfferCount);
    
    // 查询专业总数
    Long count();
}