package com.gdmu.mapper;

import com.gdmu.entity.Department;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface DepartmentMapper {

    // 插入院系
    int insert(Department department);

    // 根据ID查询院系
    Department findById(Long id);

    // 查询所有院系
    List<Department> findAll();

    // 按名称模糊查询院系
    List<Department> list(Map<String, Object> params);

    // 更新院系
    int update(Department department);

    // 删除院系
    int deleteById(Long id);

    // 统计院系数量
    Long count();
    
    // 根据院系ID查询教师人数
    Integer getTeacherCountByDepartmentId(Long departmentId);
    
    // 根据院系ID查询学生人数
    Integer getStudentCountByDepartmentId(Long departmentId);
    
    // 更新院系人数信息
    int updateDepartmentCount(@Param("id") Long id, @Param("teacherCount") Integer teacherCount, @Param("studentCount") Integer studentCount);
    
    // 根据院系ID查询已确定实习的学生数
    Integer getConfirmedCountByDepartmentId(Long departmentId);
    
    // 根据院系ID查询未找到实习的学生数
    Integer getNotFoundCountByDepartmentId(Long departmentId);
    
    // 根据院系ID查询有Offer未确定的学生数
    Integer getHasOfferCountByDepartmentId(Long departmentId);
    
    // 更新院系实习状态统计信息
    int updateDepartmentInternshipCount(@Param("id") Long id, @Param("confirmedCount") Integer confirmedCount, @Param("notFoundCount") Integer notFoundCount, @Param("hasOfferCount") Integer hasOfferCount);

}