package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.entity.PageResult;
import com.gdmu.entity.Result;
import com.gdmu.entity.TemplateFile;
import com.gdmu.service.TemplateFileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 模板文件管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/templates")
public class TemplateFileController {

    @Autowired
    private TemplateFileService templateFileService;

    /**
     * 分页查询模板列表
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result getTemplates(@RequestParam(defaultValue = "1") Integer page,
                               @RequestParam(defaultValue = "10") Integer pageSize,
                               @RequestParam(required = false) String name,
                               @RequestParam(required = false) String category,
                               @RequestParam(required = false) Integer status) {
        log.info("分页查询模板列表，页码: {}, 每页: {}", page, pageSize);
        PageResult<TemplateFile> pageResult = templateFileService.findPage(page, pageSize, name, category, status);
        return Result.success(pageResult);
    }

    /**
     * 获取模板详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result getTemplateById(@PathVariable Long id) {
        TemplateFile template = templateFileService.findById(id);
        if (template == null) {
            return Result.error("模板不存在");
        }
        return Result.success(template);
    }

    /**
     * 新增模板
     */
    @Log(operationType = "ADD", module = "TEMPLATE_MANAGEMENT", description = "新增模板文件")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result addTemplate(@RequestBody @Validated TemplateFile template) {
        log.info("新增模板: {}", template.getName());
        try {
            templateFileService.insert(template);
            return Result.success("添加成功");
        } catch (Exception e) {
            log.error("新增模板失败: {}", e.getMessage());
            return Result.error("添加失败: " + e.getMessage());
        }
    }

    /**
     * 更新模板
     */
    @Log(operationType = "UPDATE", module = "TEMPLATE_MANAGEMENT", description = "更新模板文件")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result updateTemplate(@PathVariable Long id, @RequestBody TemplateFile template) {
        log.info("更新模板，ID: {}", id);
        try {
            template.setId(id);
            templateFileService.update(template);
            return Result.success("更新成功");
        } catch (Exception e) {
            log.error("更新模板失败: {}", e.getMessage());
            return Result.error("更新失败: " + e.getMessage());
        }
    }

    /**
     * 删除模板
     */
    @Log(operationType = "DELETE", module = "TEMPLATE_MANAGEMENT", description = "删除模板文件")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result deleteTemplate(@PathVariable Long id) {
        log.info("删除模板，ID: {}", id);
        try {
            templateFileService.delete(id);
            return Result.success("删除成功");
        } catch (Exception e) {
            log.error("删除模板失败: {}", e.getMessage());
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 批量删除模板
     */
    @Log(operationType = "DELETE", module = "TEMPLATE_MANAGEMENT", description = "批量删除模板文件")
    @DeleteMapping("/batch")
    @PreAuthorize("hasRole('ADMIN')")
    public Result batchDeleteTemplates(@RequestBody List<Long> ids) {
        log.info("批量删除模板，ID列表: {}", ids);
        try {
            if (ids == null || ids.isEmpty()) {
                return Result.error("请选择要删除的模板");
            }
            int count = templateFileService.batchDelete(ids);
            return Result.success("成功删除" + count + "个模板");
        } catch (Exception e) {
            log.error("批量删除模板失败: {}", e.getMessage());
            return Result.error("批量删除失败: " + e.getMessage());
        }
    }

    /**
     * 下载模板（增加下载次数）
     */
    @PostMapping("/{id}/download")
    public Result downloadTemplate(@PathVariable Long id) {
        log.info("下载模板，ID: {}", id);
        TemplateFile template = templateFileService.findById(id);
        if (template == null) {
            return Result.error("模板不存在");
        }
        templateFileService.incrementDownloadCount(id);
        return Result.success(template);
    }
}
