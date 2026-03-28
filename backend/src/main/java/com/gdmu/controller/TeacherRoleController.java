package com.gdmu.controller;

import com.gdmu.entity.Result;
import com.gdmu.entity.TeacherRole;
import com.gdmu.service.TeacherRoleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/teacher/roles")
public class TeacherRoleController {
    
    @Autowired
    private TeacherRoleService teacherRoleService;
    
    @GetMapping
    public Result getAllRoles() {
        log.info("获取所有教师角色列表");
        List<TeacherRole> roles = teacherRoleService.findAll();
        return Result.success(roles);
    }
    
    @GetMapping("/{id}")
    public Result getRoleById(@PathVariable Long id) {
        log.info("根据ID获取教师角色: {}", id);
        TeacherRole role = teacherRoleService.findById(id);
        if (role == null) {
            return Result.error("角色不存在");
        }
        return Result.success(role);
    }
    
    @GetMapping("/code/{roleCode}")
    public Result getRoleByCode(@PathVariable String roleCode) {
        log.info("根据角色代码获取教师角色: {}", roleCode);
        TeacherRole role = teacherRoleService.findByRoleCode(roleCode);
        if (role == null) {
            return Result.error("角色不存在");
        }
        return Result.success(role);
    }
    
    @PostMapping
    public Result createRole(@RequestBody TeacherRole teacherRole) {
        log.info("创建教师角色: {}", teacherRole.getRoleName());
        try {
            teacherRoleService.insert(teacherRole);
            return Result.success("创建成功", teacherRole);
        } catch (Exception e) {
            log.error("创建教师角色失败", e);
            return Result.error("创建失败: " + e.getMessage());
        }
    }
    
    @PutMapping("/{id}")
    public Result updateRole(@PathVariable Long id, @RequestBody TeacherRole teacherRole) {
        log.info("更新教师角色: {}", id);
        try {
            teacherRole.setId(id);
            teacherRoleService.update(teacherRole);
            return Result.success("更新成功");
        } catch (Exception e) {
            log.error("更新教师角色失败", e);
            return Result.error("更新失败: " + e.getMessage());
        }
    }
    
    @DeleteMapping("/{id}")
    public Result deleteRole(@PathVariable Long id) {
        log.info("删除教师角色: {}", id);
        try {
            teacherRoleService.deleteById(id);
            return Result.success("删除成功");
        } catch (Exception e) {
            log.error("删除教师角色失败", e);
            return Result.error("删除失败: " + e.getMessage());
        }
    }
}
