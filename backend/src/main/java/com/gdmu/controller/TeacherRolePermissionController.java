package com.gdmu.controller;

import com.gdmu.entity.Result;
import com.gdmu.entity.TeacherRolePermission;
import com.gdmu.service.TeacherRolePermissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/teacher/role-permissions")
public class TeacherRolePermissionController {
    
    @Autowired
    private TeacherRolePermissionService teacherRolePermissionService;
    
    @GetMapping
    public Result getAllRolePermissions() {
        log.info("获取所有角色权限关联列表");
        List<TeacherRolePermission> rolePermissions = teacherRolePermissionService.findAll();
        return Result.success(rolePermissions);
    }
    
    @GetMapping("/{id}")
    public Result getRolePermissionById(@PathVariable Long id) {
        log.info("根据ID获取角色权限关联: {}", id);
        TeacherRolePermission rolePermission = teacherRolePermissionService.findById(id);
        if (rolePermission == null) {
            return Result.error("角色权限关联不存在");
        }
        return Result.success(rolePermission);
    }
    
    @GetMapping("/role/{roleId}")
    public Result getRolePermissionsByRoleId(@PathVariable Long roleId) {
        log.info("根据角色ID获取角色权限关联列表: {}", roleId);
        List<TeacherRolePermission> rolePermissions = teacherRolePermissionService.findByRoleId(roleId);
        return Result.success(rolePermissions);
    }
    
    @GetMapping("/permission/{permissionId}")
    public Result getRolePermissionsByPermissionId(@PathVariable Long permissionId) {
        log.info("根据权限ID获取角色权限关联列表: {}", permissionId);
        List<TeacherRolePermission> rolePermissions = teacherRolePermissionService.findByPermissionId(permissionId);
        return Result.success(rolePermissions);
    }
    
    @PostMapping
    public Result createRolePermission(@RequestBody TeacherRolePermission teacherRolePermission) {
        log.info("创建角色权限关联: roleId={}, permissionId={}", 
                teacherRolePermission.getRoleId(), teacherRolePermission.getPermissionId());
        try {
            teacherRolePermissionService.insert(teacherRolePermission);
            return Result.success("创建成功", teacherRolePermission);
        } catch (Exception e) {
            log.error("创建角色权限关联失败", e);
            return Result.error("创建失败: " + e.getMessage());
        }
    }
    
    @PutMapping("/{id}")
    public Result updateRolePermission(@PathVariable Long id, @RequestBody TeacherRolePermission teacherRolePermission) {
        log.info("更新角色权限关联: {}", id);
        try {
            teacherRolePermission.setId(id);
            teacherRolePermissionService.update(teacherRolePermission);
            return Result.success("更新成功");
        } catch (Exception e) {
            log.error("更新角色权限关联失败", e);
            return Result.error("更新失败: " + e.getMessage());
        }
    }
    
    @DeleteMapping("/{id}")
    public Result deleteRolePermission(@PathVariable Long id) {
        log.info("删除角色权限关联: {}", id);
        try {
            teacherRolePermissionService.deleteById(id);
            return Result.success("删除成功");
        } catch (Exception e) {
            log.error("删除角色权限关联失败", e);
            return Result.error("删除失败: " + e.getMessage());
        }
    }
    
    @DeleteMapping("/role/{roleId}")
    public Result deleteRolePermissionsByRoleId(@PathVariable Long roleId) {
        log.info("删除角色的所有权限关联: {}", roleId);
        try {
            teacherRolePermissionService.deleteByRoleId(roleId);
            return Result.success("删除成功");
        } catch (Exception e) {
            log.error("删除角色的所有权限关联失败", e);
            return Result.error("删除失败: " + e.getMessage());
        }
    }
}
