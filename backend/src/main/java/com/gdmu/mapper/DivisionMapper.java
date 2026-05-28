package com.gdmu.mapper;

import com.gdmu.entity.Division;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DivisionMapper {
    // 根据ID查询系
    Division findById(Long id);

    // 查询所有系
    List<Division> findAll();

    // 根据学院ID查询系
    List<Division> findByDepartmentId(@Param("departmentId") Long departmentId);

    // 新增系
    int insert(Division division);

    // 更新系
    int update(Division division);

    // 删除系
    int delete(Long id);

    // 查询系总数
    Long count();

    // 根据系ID查询教师人数
    Integer getTeacherCountByDivisionId(Long divisionId);

    // 根据系ID查询学生人数
    Integer getStudentCountByDivisionId(Long divisionId);

    // 更新系人数信息
    int updateDivisionCount(@Param("id") Long id, @Param("teacherCount") Integer teacherCount, @Param("studentCount") Integer studentCount);

    // 根据系ID查询已确定实习的学生数
    Integer getConfirmedCountByDivisionId(Long divisionId);

    // 根据系ID查询未找到实习的学生数
    Integer getNotFoundCountByDivisionId(Long divisionId);

    // 根据系ID查询有Offer未确定的学生数
    Integer getHasOfferCountByDivisionId(Long divisionId);

    // 更新系实习状态统计信息
    int updateDivisionInternshipCount(@Param("id") Long id, @Param("confirmedCount") Integer confirmedCount, @Param("notFoundCount") Integer notFoundCount, @Param("hasOfferCount") Integer hasOfferCount);
}
