package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.entity.Position;
import com.gdmu.entity.PositionCategory;
import com.gdmu.entity.Result;
import com.gdmu.service.PositionCategoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/position-categories")
public class PositionCategoryController {

    @Autowired
    private PositionCategoryService positionCategoryService;

    @GetMapping
    @PreAuthorize("hasAuthority('position-category:view')")
    public Result getAllCategories(@RequestParam(required = false) String name) {
        log.info("获取岗位类别列表，名称: {}", name);
        try {
            List<PositionCategory> categories;
            if (name != null && !name.trim().isEmpty()) {
                categories = positionCategoryService.list(name);
            } else {
                categories = positionCategoryService.findAll();
            }
            return Result.success(categories);
        } catch (Exception e) {
            log.error("获取岗位类别列表失败: {}", e.getMessage(), e);
            return Result.error("获取岗位类别列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('position-category:view')")
    public Result getCategoryById(@PathVariable Long id) {
        log.info("根据ID获取岗位类别: {}", id);
        try {
            PositionCategory category = positionCategoryService.findById(id);
            if (category == null) {
                return Result.error("岗位类别不存在");
            }
            return Result.success(category);
        } catch (Exception e) {
            log.error("获取岗位类别详情失败: {}", e.getMessage(), e);
            return Result.error("获取岗位类别详情失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}/positions")
    @PreAuthorize("hasAuthority('position-category:view')")
    public Result getPositionsByCategoryId(@PathVariable Long id) {
        log.info("根据类别ID获取岗位列表: {}", id);
        try {
            List<Position> positions = positionCategoryService.getPositionsByCategoryId(id);
            return Result.success(positions);
        } catch (Exception e) {
            log.error("获取岗位列表失败: {}", e.getMessage(), e);
            return Result.error("获取岗位列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasAuthority('position-category:view')")
    public Result getStatistics() {
        log.info("获取岗位类别统计数据");
        try {
            Map<String, Object> statistics = positionCategoryService.getCategoryStatistics();
            return Result.success(statistics);
        } catch (Exception e) {
            log.error("获取统计数据失败: {}", e.getMessage(), e);
            return Result.error("获取统计数据失败: " + e.getMessage());
        }
    }

    @Log(operationType = "ADD", module = "POSITION_CATEGORY_MANAGEMENT", description = "新增岗位类别")
    @PostMapping
    @PreAuthorize("hasAuthority('position-category:add')")
    public Result addCategory(@RequestBody PositionCategory category) {
        log.info("新增岗位类别: {}", category.getName());
        try {
            positionCategoryService.insert(category);
            return Result.success("添加岗位类别成功", category);
        } catch (Exception e) {
            log.error("添加岗位类别失败: {}", e.getMessage(), e);
            return Result.error("添加岗位类别失败: " + e.getMessage());
        }
    }

    @Log(operationType = "UPDATE", module = "POSITION_CATEGORY_MANAGEMENT", description = "更新岗位类别")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('position-category:edit')")
    public Result updateCategory(@PathVariable Long id, @RequestBody PositionCategory category) {
        log.info("更新岗位类别: ID={}", id);
        try {
            category.setId(id);
            int result = positionCategoryService.update(category);
            return Result.success("更新岗位类别成功", result);
        } catch (Exception e) {
            log.error("更新岗位类别失败: {}", e.getMessage(), e);
            return Result.error("更新岗位类别失败: " + e.getMessage());
        }
    }

    @Log(operationType = "DELETE", module = "POSITION_CATEGORY_MANAGEMENT", description = "删除岗位类别")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('position-category:delete')")
    public Result deleteCategory(@PathVariable Long id) {
        log.info("删除岗位类别: ID={}", id);
        try {
            int result = positionCategoryService.delete(id);
            return Result.success("删除岗位类别成功", result);
        } catch (Exception e) {
            log.error("删除岗位类别失败: {}", e.getMessage(), e);
            return Result.error("删除岗位类别失败: " + e.getMessage());
        }
    }
}
