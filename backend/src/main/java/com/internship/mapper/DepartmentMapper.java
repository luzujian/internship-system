package com.internship.mapper;

import com.internship.entity.Department;
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

    // ========== 删除院系级联清理：按 department_id 删除所有引用该院系的业务表记录（每条在 Service 独立 try-catch，表不存在时跳过） ==========
    // 1. 先清理教师用户 teacher_users.department_id 列（置空，若删院系时不想删老师），或者直接 DELETE；这里置 NULL 保留教师记录但解除绑定
    @Update("UPDATE teacher_users SET department_id = NULL WHERE department_id = #{departmentId}")
    int clearDepartmentIdOnTeachers(@Param("departmentId") Long departmentId);

    // 2. 系 division.department_id → 删院系前必须先删/解绑 division；这里直接删该部门所有 division 物理记录（按链路 department→division→major→class→students 本来就会走 divisionService，但先清 department_id 反向绑定更安全）
    @Delete("delete from division where department_id = #{departmentId}")
    int deleteDivisionsByDepartmentId(@Param("departmentId") Long departmentId);
}