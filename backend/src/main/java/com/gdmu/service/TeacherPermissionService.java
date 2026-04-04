package com.gdmu.service;

import com.gdmu.entity.TeacherPermission;
import com.gdmu.entity.TeacherRole;
import java.util.List;
import java.util.Map;

public interface TeacherPermissionService {
    List<TeacherPermission> findAll();
    TeacherPermission findById(Long id);
    TeacherPermission findByPermissionCode(String permissionCode);
    List<TeacherPermission> findByParentId(Long parentId);
    void insert(TeacherPermission teacherPermission);
    void update(TeacherPermission teacherPermission);
    void deleteById(Long id);
    
    List<TeacherPermission> getPermissionsByRoleId(Long roleId);
    List<String> getPermissionCodesByRoleId(Long roleId);
    List<String> getPermissionCodesByRoleCode(String roleCode);
    List<TeacherPermission> getPermissionsByRoleCode(String roleCode);
    
    List<TeacherRole> getAllRoles();
    TeacherRole getRoleByRoleCode(String roleCode);
    List<Map<String, Object>> getPermissionTree();
    void assignPermissions(Long roleId, List<Long> permissionIds, Map<Long, Integer> sortOrderMap);
}
