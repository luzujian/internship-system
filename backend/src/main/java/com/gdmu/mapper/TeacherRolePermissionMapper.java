package com.gdmu.mapper;

import com.gdmu.entity.TeacherRolePermission;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TeacherRolePermissionMapper {
    
    List<TeacherRolePermission> findAll();
    
    TeacherRolePermission findById(Long id);
    
    List<TeacherRolePermission> findByRoleId(Long roleId);
    
    List<TeacherRolePermission> findByPermissionId(Long permissionId);
    
    void insert(TeacherRolePermission teacherRolePermission);
    
    void update(TeacherRolePermission teacherRolePermission);
    
    void deleteById(Long id);
    
    void deleteByRoleId(Long roleId);
}
