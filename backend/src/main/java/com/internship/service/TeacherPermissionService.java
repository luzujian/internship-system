package com.internship.service;

import com.internship.entity.TeacherPermission;
import com.internship.entity.TeacherRole;
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

    /** 判断指定权限码是否被授予给任意一类教师角色 */
    boolean isPermissionGrantedToAnyTeacherRole(String permissionCode);
}
