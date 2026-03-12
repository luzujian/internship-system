package com.gdmu.mapper;

import com.gdmu.entity.Permission;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface RolePermissionMapper {
    
    @Insert("INSERT INTO role_permissions (role_code, permission_code) VALUES (#{roleCode}, #{permissionCode})")
    void insert(@Param("roleCode") String roleCode, @Param("permissionCode") String permissionCode);
    
    @Delete("DELETE FROM role_permissions WHERE role_code = #{roleCode}")
    void deleteByRoleCode(@Param("roleCode") String roleCode);
    
    @Select("SELECT p.* FROM permissions p " +
            "INNER JOIN role_permissions rp ON p.permission_code = rp.permission_code " +
            "WHERE rp.role_code = #{roleCode}")
    List<Permission> findPermissionsByRoleCode(@Param("roleCode") String roleCode);
    
    @Select("SELECT p.* FROM permissions p " +
            "INNER JOIN role_permissions rp ON p.permission_code = rp.permission_code " +
            "INNER JOIN roles r ON rp.role_code = r.role_code " +
            "WHERE r.id = #{roleId}")
    List<Permission> findPermissionsByRoleId(@Param("roleId") Long roleId);

    @Delete("DELETE FROM role_permissions")
    int deleteAll();
}
