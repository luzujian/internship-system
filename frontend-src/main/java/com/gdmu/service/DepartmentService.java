package com.gdmu.service;

import com.gdmu.entity.Department;

import java.util.List;

public interface DepartmentService {

    // 添加院系
    int addDepartment(Department department);

    // 根据ID获取院系
    Department findById(Long id);

    // 获取所有院系
    List<Department> findAll();
    
    // 按名称模糊查询院系
    List<Department> findByName(String name);

    // 更新院系
    int updateDepartment(Department department);

    // 删除院系
    int deleteDepartment(Long id);

    // 统计院系数量
    Long count();
    
    // 获取所有带有人数信息的院系
    List<Department> findAllWithUserCount();
    
    // 获取指定院系的教师人数
    Integer getTeacherCountByDepartmentId(Long departmentId);
    
    // 获取指定院系的学生人数
    Integer getStudentCountByDepartmentId(Long departmentId);
    
    // 获取指定院系的已确定实习学生数
    Integer getConfirmedCountByDepartmentId(Long departmentId);
    
    // 获取指定院系的未找到实习学生数
    Integer getNotFoundCountByDepartmentId(Long departmentId);
    
    // 获取指定院系的有Offer未确定学生数
    Integer getHasOfferCountByDepartmentId(Long departmentId);
}