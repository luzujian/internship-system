package com.gdmu.service;

import com.gdmu.entity.Permission;
import java.util.List;

public interface PermissionService {
    List<Permission> getAllPermissions();
    List<Permission> getPermissionsByRole(String roleCode);
    List<Permission> getPermissionsByTeacherType(String teacherType);
}
