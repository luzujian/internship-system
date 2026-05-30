package com.internship.controller;

import com.internship.anno.Log;
import com.internship.entity.Result;
import com.internship.entity.Division;
import com.internship.service.DivisionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/admin/divisions")
public class DivisionController {

    @Autowired
    private DivisionService divisionService;

    // 获取所有系
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public Result getAllDivisions() {
        log.info("获取所有系列表");
        try {
            List<Division> divisions = divisionService.findAllWithUserCount();
            return Result.success(divisions);
        } catch (Exception e) {
            log.error("获取系列表失败: {}", e.getMessage(), e);
            return Result.error("获取系列表失败: " + e.getMessage());
        }
    }

    // 根据ID获取系
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public Result getDivisionById(@PathVariable Long id) {
        log.info("根据ID获取系: {}", id);
        try {
            Division division = divisionService.findById(id);
            if (division == null) {
                return Result.error("系不存在");
            }
            return Result.success(division);
        } catch (Exception e) {
            log.error("获取系详情失败: {}", e.getMessage(), e);
            return Result.error("获取系详情失败: " + e.getMessage());
        }
    }

    // 根据学院ID获取系列表
    @GetMapping("/department/{departmentId}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public Result getDivisionsByDepartmentId(@PathVariable Long departmentId) {
        log.info("根据学院ID获取系列表: {}", departmentId);
        try {
            List<Division> divisions = divisionService.findByDepartmentId(departmentId);
            return Result.success(divisions);
        } catch (Exception e) {
            log.error("获取系列表失败: {}", e.getMessage(), e);
            return Result.error("获取系列表失败: " + e.getMessage());
        }
    }

    // 新增系
    @Log(operationType = "ADD", module = "DIVISION_MANAGEMENT", description = "新增系")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public Result addDivision(@RequestBody Division division) {
        log.info("新增系: {}", division.getName());
        try {
            int result = divisionService.addDivision(division);
            return Result.success("添加系成功", result);
        } catch (Exception e) {
            log.error("添加系失败: {}", e.getMessage(), e);
            return Result.error("添加系失败: " + e.getMessage());
        }
    }

    // 更新系
    @Log(operationType = "UPDATE", module = "DIVISION_MANAGEMENT", description = "更新系")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public Result updateDivision(@PathVariable Long id, @RequestBody Division division) {
        log.info("更新系: ID={}", id);
        try {
            division.setId(id);
            int result = divisionService.updateDivision(division);
            return Result.success("更新系成功", result);
        } catch (Exception e) {
            log.error("更新系失败: {}", e.getMessage(), e);
            return Result.error("更新系失败: " + e.getMessage());
        }
    }

    // 删除系
    @Log(operationType = "DELETE", module = "DIVISION_MANAGEMENT", description = "删除系")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public Result deleteDivision(@PathVariable Long id) {
        log.info("删除系: ID={}", id);
        try {
            int result = divisionService.deleteDivision(id);
            return Result.success("删除系成功", result);
        } catch (Exception e) {
            log.error("删除系失败: {}", e.getMessage(), e);
            return Result.error("删除系失败: " + e.getMessage());
        }
    }
}
