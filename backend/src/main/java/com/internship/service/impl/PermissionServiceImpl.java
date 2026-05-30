package com.internship.service.impl;

import com.internship.entity.Permission;
import com.internship.mapper.PermissionMapper;
import com.internship.service.PermissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class PermissionServiceImpl implements PermissionService {
    
    @Autowired
    private PermissionMapper permissionMapper;
    
    @Override
    public List<Permission> getAllPermissions() {
        return permissionMapper.findAll();
    }
    
    @Override
    public List<Permission> getPermissionsByRole(String roleCode) {
        return permissionMapper.findByRoleCode(roleCode);
    }
    
    @Override
    public List<Permission> getPermissionsByTeacherType(String teacherType) {
        Map<String, String> typeToRoleMap = new HashMap<>();
        typeToRoleMap.put("COLLEGE", "ROLE_TEACHER_COLLEGE");
        typeToRoleMap.put("DEPARTMENT", "ROLE_TEACHER_DEPARTMENT");
        typeToRoleMap.put("COUNSELOR", "ROLE_TEACHER_COUNSELOR");
        
        String roleCode = typeToRoleMap.get(teacherType);
        if (roleCode == null) {
            log.warn("未知的教师类型: {}", teacherType);
            return List.of();
        }
        
        return getPermissionsByRole(roleCode);
    }
}
