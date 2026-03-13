package com.gdmu.mapper;

import com.gdmu.entity.Permission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PermissionMapper {
    
    @Select("SELECT * FROM permissions")
    List<Permission> findAll();
    
    @Select("SELECT * FROM permissions WHERE id = #{id}")
    Permission findById(Long id);
    
    @Select("SELECT * FROM permissions WHERE permission_code = #{permissionCode}")
    Permission findByPermissionCode(String permissionCode);
    
    @Select("SELECT p.* FROM permissions p " +
            "INNER JOIN role_permissions rp ON p.permission_code = rp.permission_code " +
            "WHERE rp.role_code = #{roleCode}")
    List<Permission> findByRoleCode(String roleCode);
    
    @Select("SELECT p.* FROM permissions p " +
            "INNER JOIN role_permissions rp ON p.permission_code = rp.permission_code " +
            "WHERE rp.role_code IN " +
            "('ROLE_TEACHER_COLLEGE', 'ROLE_TEACHER_DEPARTMENT', 'ROLE_TEACHER_COUNSELOR')")
    List<Permission> findAllTeacherPermissions();
    
    @Select("SELECT * FROM permissions WHERE module = #{module}")
    List<Permission> findByModule(String module);
}
