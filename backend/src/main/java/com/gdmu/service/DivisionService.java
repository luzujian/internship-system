package com.gdmu.service;

import com.gdmu.entity.Division;

import java.util.List;

public interface DivisionService {
    // 根据ID查询系
    Division findById(Long id);

    // 查询所有系
    List<Division> findAll();

    // 根据学院ID查询系
    List<Division> findByDepartmentId(Long departmentId);

    // 新增系
    int addDivision(Division division);

    // 更新系
    int updateDivision(Division division);

    // 删除系
    int deleteDivision(Long id);

    // 获取所有带有用户数的系
    List<Division> findAllWithUserCount();

    // 获取指定系的教师人数
    Integer getTeacherCountByDivisionId(Long divisionId);

    // 获取指定系的学生人数
    Integer getStudentCountByDivisionId(Long divisionId);

    // 获取指定系的已确定实习学生数
    Integer getConfirmedCountByDivisionId(Long divisionId);

    // 获取指定系的未找到实习学生数
    Integer getNotFoundCountByDivisionId(Long divisionId);

    // 获取指定系的有Offer未确定学生数
    Integer getHasOfferCountByDivisionId(Long divisionId);
}
