package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.entity.Result;
import com.gdmu.entity.Department;
import com.gdmu.service.DepartmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 院系控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/departments")
public class DepartmentController {

    @Autowired
    private DepartmentService departmentService;

    // 获取所有院系
    @GetMapping
    @PreAuthorize("hasAuthority('department:view')")
    public Result getAllDepartments() {
        log.info("获取所有院系列表");
        List<Department> departments = departmentService.findAllWithUserCount();
        return Result.success(departments);
    }
    
    // 按名称模糊查询院系
    @GetMapping("/search")
    @PreAuthorize("hasAuthority('department:view')")
    public Result searchDepartmentsByName(@RequestParam String name) {
        log.info("按名称模糊查询院系: {}", name);
        List<Department> departments = departmentService.findByName(name);
        return Result.success(departments);
    }

    // 根据ID获取院系
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('department:view')")
    public Result getDepartmentById(@PathVariable Long id) {
        log.info("根据ID获取院系: {}", id);
        Department department = departmentService.findById(id);
        if (department == null) {
            return Result.error("院系不存在");
        }
        return Result.success(department);
    }

    // 添加院系
    @Log(operationType = "ADD", module = "DEPARTMENT_MANAGEMENT", description = "添加院系")
    @PostMapping
    @PreAuthorize("hasAuthority('department:add')")
    public Result addDepartment(@RequestBody Department department) {
        log.info("添加院系: {}", department.getName());
        try {
            departmentService.addDepartment(department);
            return Result.success("添加成功", department);
        } catch (Exception e) {
            log.warn("添加院系失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    // 更新院系
    @Log(operationType = "UPDATE", module = "DEPARTMENT_MANAGEMENT", description = "更新院系")
    @PutMapping
    @PreAuthorize("hasAuthority('department:edit')")
    public Result updateDepartment(@RequestBody Department department) {
        log.info("更新院系: {}", department.getName());
        try {
            departmentService.updateDepartment(department);
            return Result.success("更新成功");
        } catch (Exception e) {
            log.warn("更新院系失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    // 删除院系
    @Log(operationType = "DELETE", module = "DEPARTMENT_MANAGEMENT", description = "删除院系")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('department:delete')")
    public Result deleteDepartment(@PathVariable Long id) {
        log.info("删除院系，ID: {}", id);
        try {
            departmentService.deleteDepartment(id);
            return Result.success("删除成功");
        } catch (Exception e) {
            log.warn("删除院系失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }
}