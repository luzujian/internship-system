package com.internship.mapper;

import com.internship.entity.TeacherRolePermission;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TeacherRolePermissionMapper {

    List<TeacherRolePermission> findAll();

    TeacherRolePermission findById(Long id);

    List<TeacherRolePermission> findByRoleId(Long roleId);

    List<TeacherRolePermission> findByPermissionId(Long permissionId);

    /** 统计拥有指定权限码的未删除教师角色数量（跨 teacher_role_permission + teacher_permission + teacher_role 三表 JOIN） */
    int countRolesByPermissionCode(String permissionCode);

    void insert(TeacherRolePermission teacherRolePermission);

    void update(TeacherRolePermission teacherRolePermission);

    void deleteById(Long id);

    void deleteByRoleId(Long roleId);
}
