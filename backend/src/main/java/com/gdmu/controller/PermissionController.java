package com.gdmu.controller;

import com.gdmu.entity.Permission;
import com.gdmu.entity.Result;
import com.gdmu.service.PermissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/permissions")
public class PermissionController {
    
    @Autowired
    private PermissionService permissionService;
    
    @GetMapping
    public Result getAllPermissions() {
        log.info("获取所有权限列表");
        List<Permission> permissions = permissionService.getAllPermissions();
        return Result.success(permissions);
    }
    
    @GetMapping("/by-role/{roleCode}")
    public Result getPermissionsByRole(@PathVariable String roleCode) {
        log.info("根据角色代码获取权限列表: {}", roleCode);
        List<Permission> permissions = permissionService.getPermissionsByRole(roleCode);
        return Result.success(permissions);
    }
    
    @GetMapping("/by-teacher-type/{teacherType}")
    public Result getPermissionsByTeacherType(@PathVariable String teacherType) {
        log.info("根据教师类型获取权限列表: {}", teacherType);
        List<Permission> permissions = permissionService.getPermissionsByTeacherType(teacherType);
        return Result.success(permissions);
    }
}
