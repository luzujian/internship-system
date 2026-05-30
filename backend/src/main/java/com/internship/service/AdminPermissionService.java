package com.internship.service;

import com.internship.entity.Role;
import com.internship.entity.Permission;

import java.util.List;
import java.util.Map;

public interface AdminPermissionService {
    
    List<Role> getAllRoles();
    
    void createRole(Role role);
    
    void updateRole(Role role);
    
    void deleteRole(Long id);
    
    List<Permission> getAllPermissions();
    
    List<Permission> getRolePermissions(Long roleId);
    
    List<Permission> getRolePermissionsByCode(String roleCode);
    
    void assignPermissions(Long roleId, List<Long> permissionIds);

    List<Map<String, Object>> getPermissionTree();

    int clearRolePermissions();
}
