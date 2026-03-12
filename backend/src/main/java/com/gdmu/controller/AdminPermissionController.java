package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.entity.Result;
import com.gdmu.entity.Role;
import com.gdmu.entity.Permission;
import com.gdmu.service.AdminPermissionService;
import com.gdmu.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/admin/permissions")
public class AdminPermissionController {

    @Autowired
    private AdminPermissionService adminPermissionService;
    
    @Autowired
    private UserService userService;
    
    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('permission:view')")
    public Result getAllRoles() {
        log.info("获取所有角色列表");
        try {
            List<Role> roles = adminPermissionService.getAllRoles();
            return Result.success(roles);
        } catch (Exception e) {
            log.error("获取角色列表失败: {}", e.getMessage());
            return Result.error("获取角色列表失败");
        }
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('permission:add')")
    @Log(module = "PERMISSION", operationType = "CREATE", description = "创建角色")
    public Result createRole(@RequestBody Role role) {
        log.info("创建角色: {}", role.getRoleName());
        try {
            return Result.error("系统仅支持管理员角色，不允许创建新角色");
        } catch (Exception e) {
            log.error("创建角色失败: {}", e.getMessage());
            return Result.error("创建角色失败");
        }
    }

    @PutMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('permission:edit')")
    @Log(module = "PERMISSION", operationType = "UPDATE", description = "更新角色")
    public Result updateRole(@PathVariable Long id, @RequestBody Role role) {
        log.info("更新角色: {}", id);
        try {
            return Result.error("系统仅支持管理员角色，不允许编辑角色");
        } catch (Exception e) {
            log.error("更新角色失败: {}", e.getMessage());
            return Result.error("更新角色失败");
        }
    }

    @DeleteMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('permission:delete')")
    @Log(module = "PERMISSION", operationType = "DELETE", description = "删除角色")
    public Result deleteRole(@PathVariable Long id) {
        log.info("删除角色: {}", id);
        try {
            return Result.error("系统仅支持管理员角色，不允许删除角色");
        } catch (Exception e) {
            log.error("删除角色失败: {}", e.getMessage());
            return Result.error("删除角色失败");
        }
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('permission:view')")
    public Result getAllPermissions() {
        log.info("获取所有权限列表");
        try {
            List<Permission> permissions = adminPermissionService.getAllPermissions();
            return Result.success(permissions);
        } catch (Exception e) {
            log.error("获取权限列表失败: {}", e.getMessage());
            return Result.error("获取权限列表失败");
        }
    }

    @GetMapping("/roles/{roleId}/permissions")
    @PreAuthorize("hasAuthority('permission:view')")
    public Result getRolePermissions(@PathVariable Long roleId) {
        log.info("获取角色权限: {}", roleId);
        try {
            List<Permission> permissions = adminPermissionService.getRolePermissions(roleId);
            return Result.success(permissions);
        } catch (Exception e) {
            log.error("获取角色权限失败: {}", e.getMessage());
            return Result.error("获取角色权限失败");
        }
    }

    @PostMapping("/roles/{roleId}/permissions")
    @PreAuthorize("hasAuthority('permission:edit')")
    @Log(module = "PERMISSION", operationType = "ASSIGN", description = "分配权限")
    public Result assignPermissions(@PathVariable Long roleId, @RequestBody Map<String, Object> data) {
        log.info("为角色分配权限: {}, 数据: {}", roleId, data);
        try {
            @SuppressWarnings("unchecked")
            List<Long> permissionIds = (List<Long>) data.get("permissionIds");
            log.info("解析到的权限ID列表: {}", permissionIds);
            adminPermissionService.assignPermissions(roleId, permissionIds);
            return Result.success("权限分配成功");
        } catch (Exception e) {
            log.error("分配权限失败: {}", e.getMessage(), e);
            return Result.error("分配权限失败: " + e.getMessage());
        }
    }

    @GetMapping("/permission-tree")
    @PreAuthorize("hasAuthority('permission:view')")
    public Result getPermissionTree() {
        log.info("获取权限树");
        try {
            List<Map<String, Object>> tree = adminPermissionService.getPermissionTree();
            return Result.success(tree);
        } catch (Exception e) {
            log.error("获取权限树失败: {}", e.getMessage());
            return Result.error("获取权限树失败");
        }
    }

    @DeleteMapping("/clear")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "PERMISSION", operationType = "DELETE", description = "清除角色权限数据")
    public Result clearRolePermissions() {
        log.info("清除角色权限数据");
        try {
            int count = adminPermissionService.clearRolePermissions();
            log.info("清除角色权限数据成功，共清除{}条记录", count);
            return Result.success("清除成功，共清除" + count + "条记录");
        } catch (Exception e) {
            log.error("清除角色权限数据失败: {}", e.getMessage(), e);
            return Result.error("清除失败");
        }
    }
    
    @PostMapping("/clear-cache")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "PERMISSION", operationType = "CLEAR", description = "清除用户缓存")
    public Result clearUserCache() {
        log.info("清除用户缓存");
        try {
            userService.clearAllUserDetailsCache();
            
            // 清除 Redis 缓存
            if (redisTemplate != null) {
                try {
                    Set<String> keys = redisTemplate.keys("cache:user:*");
                    if (keys != null && !keys.isEmpty()) {
                        redisTemplate.delete(keys);
                        log.info("已清除 Redis 用户缓存: {} 条", keys.size());
                    }
                } catch (Exception e) {
                    log.warn("清除 Redis 缓存失败: {}", e.getMessage());
                }
            }
            
            log.info("清除用户缓存成功");
            return Result.success("清除缓存成功");
        } catch (Exception e) {
            log.error("清除用户缓存失败: {}", e.getMessage(), e);
            return Result.error("清除缓存失败");
        }
    }
    
    @PostMapping("/clear-all-cache")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "PERMISSION", operationType = "CLEAR", description = "清除所有缓存")
    public Result clearAllCache() {
        log.info("清除所有缓存");
        try {
            // 清除 UserDetails 缓存
            userService.clearAllUserDetailsCache();
            
            // 清除 Redis 所有缓存
            if (redisTemplate != null) {
                try {
                    Set<String> keys = redisTemplate.keys("*");
                    if (keys != null && !keys.isEmpty()) {
                        redisTemplate.delete(keys);
                        log.info("已清除 Redis 所有缓存: {} 条", keys.size());
                    }
                } catch (Exception e) {
                    log.warn("清除 Redis 缓存失败: {}", e.getMessage());
                }
            }
            
            log.info("清除所有缓存成功");
            return Result.success("清除所有缓存成功，请重新登录");
        } catch (Exception e) {
            log.error("清除所有缓存失败: {}", e.getMessage(), e);
            return Result.error("清除缓存失败");
        }
    }
}
