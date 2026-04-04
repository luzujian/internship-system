package com.gdmu.controller;

import com.gdmu.entity.Result;
import com.gdmu.entity.TeacherPermission;
import com.gdmu.entity.TeacherRole;
import com.gdmu.service.TeacherPermissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/teacher/permissions")
public class TeacherPermissionController {
    
    @Autowired
    private TeacherPermissionService teacherPermissionService;
    
    @GetMapping
    public Result getAllPermissions() {
        log.info("获取所有教师权限列表");
        List<TeacherPermission> permissions = teacherPermissionService.findAll();
        return Result.success(permissions);
    }
    
    @GetMapping("/{id}")
    public Result getPermissionById(@PathVariable Long id) {
        log.info("根据ID获取教师权限: {}", id);
        TeacherPermission permission = teacherPermissionService.findById(id);
        if (permission == null) {
            return Result.error("权限不存在");
        }
        return Result.success(permission);
    }
    
    @GetMapping("/code/{permissionCode}")
    public Result getPermissionByCode(@PathVariable String permissionCode) {
        log.info("根据权限代码获取教师权限: {}", permissionCode);
        TeacherPermission permission = teacherPermissionService.findByPermissionCode(permissionCode);
        if (permission == null) {
            return Result.error("权限不存在");
        }
        return Result.success(permission);
    }
    
    @GetMapping("/parent/{parentId}")
    public Result getPermissionsByParentId(@PathVariable Long parentId) {
        log.info("根据父权限ID获取教师权限列表: {}", parentId);
        List<TeacherPermission> permissions = teacherPermissionService.findByParentId(parentId);
        return Result.success(permissions);
    }
    
    @GetMapping("/role/{roleId}")
    public Result getPermissionsByRoleId(@PathVariable Long roleId) {
        log.info("根据角色ID获取教师权限列表: {}", roleId);
        List<TeacherPermission> permissions = teacherPermissionService.getPermissionsByRoleId(roleId);
        return Result.success(permissions);
    }
    
    @GetMapping("/role/{roleId}/codes")
    public Result getPermissionCodesByRoleId(@PathVariable Long roleId) {
        log.info("根据角色ID获取教师权限代码列表: {}", roleId);
        List<String> permissionCodes = teacherPermissionService.getPermissionCodesByRoleId(roleId);
        return Result.success(permissionCodes);
    }
    
    @GetMapping("/role-code/{roleCode}/codes")
    public Result getPermissionCodesByRoleCode(@PathVariable String roleCode) {
        log.info("根据角色代码获取教师权限代码列表: {}", roleCode);
        List<String> permissionCodes = teacherPermissionService.getPermissionCodesByRoleCode(roleCode);
        return Result.success(permissionCodes);
    }
    
    @GetMapping("/role-code/{roleCode}/permissions")
    public Result getPermissionsByRoleCode(@PathVariable String roleCode) {
        log.info("根据角色代码获取教师权限列表（带排序）: {}", roleCode);
        List<TeacherPermission> permissions = teacherPermissionService.getPermissionsByRoleCode(roleCode);
        return Result.success(permissions);
    }
    
    @PostMapping
    public Result createPermission(@RequestBody TeacherPermission teacherPermission) {
        log.info("创建教师权限: {}", teacherPermission.getPermissionName());
        try {
            teacherPermissionService.insert(teacherPermission);
            return Result.success("创建成功", teacherPermission);
        } catch (Exception e) {
            log.error("创建教师权限失败", e);
            return Result.error("创建失败: " + e.getMessage());
        }
    }
    
    @PutMapping("/{id}")
    public Result updatePermission(@PathVariable Long id, @RequestBody TeacherPermission teacherPermission) {
        log.info("更新教师权限: {}", id);
        try {
            teacherPermission.setId(id);
            teacherPermissionService.update(teacherPermission);
            return Result.success("更新成功");
        } catch (Exception e) {
            log.error("更新教师权限失败", e);
            return Result.error("更新失败: " + e.getMessage());
        }
    }
    
    @DeleteMapping("/{id}")
    public Result deletePermission(@PathVariable Long id) {
        log.info("删除教师权限: {}", id);
        try {
            teacherPermissionService.deleteById(id);
            return Result.success("删除成功");
        } catch (Exception e) {
            log.error("删除教师权限失败", e);
            return Result.error("删除失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public Result getAllRoles() {
        log.info("获取所有教师角色列表");
        List<TeacherRole> roles = teacherPermissionService.getAllRoles();
        return Result.success(roles);
    }
    
    @GetMapping("/roles/code/{roleCode}")
    public Result getRoleByCode(@PathVariable String roleCode) {
        log.info("根据角色代码获取教师角色: {}", roleCode);
        TeacherRole role = teacherPermissionService.getRoleByRoleCode(roleCode);
        if (role == null) {
            return Result.error("角色不存在");
        }
        return Result.success(role);
    }
    
    @GetMapping("/tree")
    public Result getPermissionTree() {
        log.info("获取教师权限树");
        List<Map<String, Object>> tree = teacherPermissionService.getPermissionTree();
        return Result.success(tree);
    }
    
    @PostMapping("/roles/{roleId}/permissions")
    @PreAuthorize("hasRole('ADMIN')")
    public Result assignPermissions(@PathVariable Long roleId, @RequestBody Map<String, Object> data) {
        log.info("为教师角色分配权限: roleId={}, data={}", roleId, data);
        try {
            @SuppressWarnings("unchecked")
            List<?> rawPermissionIds = (List<?>) data.get("permissionIds");
            List<Long> permissionIds = rawPermissionIds.stream()
                    .map(obj -> {
                        if (obj instanceof Integer) {
                            return ((Integer) obj).longValue();
                        } else if (obj instanceof Long) {
                            return (Long) obj;
                        } else if (obj instanceof String) {
                            return Long.parseLong((String) obj);
                        } else {
                            return Long.valueOf(obj.toString());
                        }
                    })
                    .collect(java.util.stream.Collectors.toList());
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> sortOrderList = (List<Map<String, Object>>) data.get("sortOrder");
            Map<Long, Integer> sortOrderMap = new java.util.HashMap<>();
            if (sortOrderList != null) {
                for (Map<String, Object> item : sortOrderList) {
                    Long permId = null;
                    Object idObj = item.get("id");
                    if (idObj instanceof Integer) {
                        permId = ((Integer) idObj).longValue();
                    } else if (idObj instanceof Long) {
                        permId = (Long) idObj;
                    }
                    Integer order = (Integer) item.get("sortOrder");
                    if (permId != null && order != null) {
                        sortOrderMap.put(permId, order);
                    }
                }
            }
            
            teacherPermissionService.assignPermissions(roleId, permissionIds, sortOrderMap);
            return Result.success("权限分配成功");
        } catch (Exception e) {
            log.error("分配教师权限失败", e);
            return Result.error("权限分配失败: " + e.getMessage());
        }
    }
}
