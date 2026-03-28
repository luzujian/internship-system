package com.gdmu.service;

import com.gdmu.entity.TeacherRolePermission;
import java.util.List;

public interface TeacherRolePermissionService {
    List<TeacherRolePermission> findAll();
    TeacherRolePermission findById(Long id);
    List<TeacherRolePermission> findByRoleId(Long roleId);
    List<TeacherRolePermission> findByPermissionId(Long permissionId);
    void insert(TeacherRolePermission teacherRolePermission);
    void update(TeacherRolePermission teacherRolePermission);
    void deleteById(Long id);
    void deleteByRoleId(Long roleId);
}
