package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.entity.KeywordLibrary;
import com.gdmu.entity.Result;
import com.gdmu.entity.PageResult;
import com.gdmu.service.KeywordLibraryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/admin/keyword-library")
public class KeywordLibraryController {

    @Autowired
    private KeywordLibraryService keywordLibraryService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "KEYWORD_LIBRARY", operationType = "INSERT", description = "添加关键词")
    public Result addKeyword(@RequestBody KeywordLibrary keywordLibrary) {
        log.info("添加关键词: {}", keywordLibrary.getKeyword());
        try {
            int result = keywordLibraryService.insert(keywordLibrary);
            return Result.success("添加成功", keywordLibrary);
        } catch (Exception e) {
            log.error("添加关键词失败: {}", e.getMessage());
            return Result.error("添加失败: " + e.getMessage());
        }
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "KEYWORD_LIBRARY", operationType = "SELECT", description = "查询关键词列表")
    public Result getKeywords() {
        log.info("查询所有关键词");
        try {
            List<KeywordLibrary> keywords = keywordLibraryService.findAll();
            return Result.success(keywords);
        } catch (Exception e) {
            log.error("查询关键词失败: {}", e.getMessage());
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "KEYWORD_LIBRARY", operationType = "SELECT", description = "查询关键词详情")
    public Result getKeywordById(@PathVariable Long id) {
        log.info("查询关键词详情，ID: {}", id);
        try {
            KeywordLibrary keyword = keywordLibraryService.findById(id);
            return Result.success(keyword);
        } catch (Exception e) {
            log.error("查询关键词详情失败: {}", e.getMessage());
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "KEYWORD_LIBRARY", operationType = "UPDATE", description = "更新关键词")
    public Result updateKeyword(@PathVariable Long id, @RequestBody KeywordLibrary keywordLibrary) {
        log.info("更新关键词，ID: {}", id);
        try {
            keywordLibrary.setId(id);
            int result = keywordLibraryService.update(keywordLibrary);
            return Result.success("更新成功", keywordLibrary);
        } catch (Exception e) {
            log.error("更新关键词失败: {}", e.getMessage());
            return Result.error("更新失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "KEYWORD_LIBRARY", operationType = "DELETE", description = "删除关键词")
    public Result deleteKeyword(@PathVariable Long id) {
        log.info("删除关键词，ID: {}", id);
        try {
            int result = keywordLibraryService.deleteById(id);
            return Result.success("删除成功");
        } catch (Exception e) {
            log.error("删除关键词失败: {}", e.getMessage());
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    @GetMapping("/category/{category}")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "KEYWORD_LIBRARY", operationType = "SELECT", description = "按分类查询关键词")
    public Result getKeywordsByCategory(@PathVariable String category) {
        log.info("按分类查询关键词，分类: {}", category);
        try {
            List<KeywordLibrary> keywords = keywordLibraryService.findByCategory(category);
            return Result.success(keywords);
        } catch (Exception e) {
            log.error("按分类查询关键词失败: {}", e.getMessage());
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/usage-type/{usageType}")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "KEYWORD_LIBRARY", operationType = "SELECT", description = "按使用类型查询关键词")
    public Result getKeywordsByUsageType(@PathVariable String usageType) {
        log.info("按使用类型查询关键词，类型: {}", usageType);
        try {
            List<KeywordLibrary> keywords = keywordLibraryService.findByUsageType(usageType);
            return Result.success(keywords);
        } catch (Exception e) {
            log.error("按使用类型查询关键词失败: {}", e.getMessage());
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "KEYWORD_LIBRARY", operationType = "SELECT", description = "按状态查询关键词")
    public Result getKeywordsByStatus(@PathVariable Integer status) {
        log.info("按状态查询关键词，状态: {}", status);
        try {
            List<KeywordLibrary> keywords = keywordLibraryService.findByStatus(status);
            return Result.success(keywords);
        } catch (Exception e) {
            log.error("按状态查询关键词失败: {}", e.getMessage());
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/search/{keyword}")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "KEYWORD_LIBRARY", operationType = "SELECT", description = "搜索关键词")
    public Result searchKeywords(@PathVariable String keyword) {
        log.info("搜索关键词，关键词: {}", keyword);
        try {
            List<KeywordLibrary> keywords = keywordLibraryService.searchByKeyword(keyword);
            return Result.success(keywords);
        } catch (Exception e) {
            log.error("搜索关键词失败: {}", e.getMessage());
            return Result.error("搜索失败: " + e.getMessage());
        }
    }
}
