package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.entity.Result;
import com.gdmu.entity.Major;
import com.gdmu.service.MajorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/majors")
public class MajorController {
    
    @Autowired
    private MajorService majorService;
    
    // 获取所有专业
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'COMPANY') or hasAuthority('major:view')")
    public Result getAllMajors() {
        log.info("获取所有专业列表");
        try {
            List<Major> majors = majorService.findAllWithUserCount();
            return Result.success(majors);
        } catch (Exception e) {
            log.error("获取专业列表失败: {}", e.getMessage(), e);
            return Result.error("获取专业列表失败: " + e.getMessage());
        }
    }
    
    // 根据ID获取专业
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('major:view')")
    public Result getMajorById(@PathVariable Long id) {
        log.info("根据ID获取专业: {}", id);
        try {
            Major major = majorService.findById(id);
            if (major == null) {
                return Result.error("专业不存在");
            }
            return Result.success(major);
        } catch (Exception e) {
            log.error("获取专业详情失败: {}", e.getMessage(), e);
            return Result.error("获取专业详情失败: " + e.getMessage());
        }
    }
    
    // 根据名称搜索专业
    @GetMapping("/search")
    @PreAuthorize("hasAuthority('major:view')")
    public Result searchMajorsByName(@RequestParam String name) {
        log.info("根据名称搜索专业: {}", name);
        try {
            List<Major> majors = majorService.findByName(name);
            return Result.success(majors);
        } catch (Exception e) {
            log.error("搜索专业失败: {}", e.getMessage(), e);
            return Result.error("搜索专业失败: " + e.getMessage());
        }
    }
    
    // 新增专业
    @Log(operationType = "ADD", module = "MAJOR_MANAGEMENT", description = "新增专业")
    @PostMapping
    @PreAuthorize("hasAuthority('major:add')")
    public Result addMajor(@RequestBody Major major) {
        log.info("新增专业: {}", major.getName());
        try {
            int result = majorService.save(major);
            return Result.success("添加专业成功", result);
        } catch (Exception e) {
            log.error("添加专业失败: {}", e.getMessage(), e);
            return Result.error("添加专业失败: " + e.getMessage());
        }
    }
    
    // 更新专业
    @Log(operationType = "UPDATE", module = "MAJOR_MANAGEMENT", description = "更新专业")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('major:edit')")
    public Result updateMajor(@PathVariable Long id, @RequestBody Major major) {
        log.info("更新专业: ID={}", id);
        try {
            major.setId(id);
            int result = majorService.update(major);
            return Result.success("更新专业成功", result);
        } catch (Exception e) {
            log.error("更新专业失败: {}", e.getMessage(), e);
            return Result.error("更新专业失败: " + e.getMessage());
        }
    }
    
    // 删除专业
    @Log(operationType = "DELETE", module = "MAJOR_MANAGEMENT", description = "删除专业")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('major:delete')")
    public Result deleteMajor(@PathVariable Long id) {
        log.info("删除专业: ID={}", id);
        try {
            int result = majorService.delete(id);
            return Result.success("删除专业成功", result);
        } catch (Exception e) {
            log.error("删除专业失败: {}", e.getMessage(), e);
            return Result.error("删除专业失败: " + e.getMessage());
        }
    }
}