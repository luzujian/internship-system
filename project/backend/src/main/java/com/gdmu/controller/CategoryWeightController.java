package com.gdmu.controller;

import com.gdmu.entity.CategoryWeight;
import com.gdmu.entity.Result;
import com.gdmu.service.CategoryWeightService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/category-weight")
public class CategoryWeightController {
    
    private static final Logger log = LoggerFactory.getLogger(CategoryWeightController.class);
    
    @Autowired
    private CategoryWeightService categoryWeightService;
    
    @GetMapping
    public Result findAll() {
        log.info("查询所有类别权重");
        List<CategoryWeight> list = categoryWeightService.findAll();
        return Result.success(list);
    }
    
    @GetMapping("/active")
    public Result findActive() {
        log.info("查询所有启用的类别权重");
        List<CategoryWeight> list = categoryWeightService.findActive();
        return Result.success(list);
    }
    
    @GetMapping("/{id}")
    public Result findById(@PathVariable Long id) {
        log.info("根据ID查询类别权重，ID: {}", id);
        CategoryWeight categoryWeight = categoryWeightService.findById(id);
        return Result.success(categoryWeight);
    }
    
    @GetMapping("/category/{categoryCode}")
    public Result findByCategoryCode(@PathVariable String categoryCode) {
        log.info("根据类别代码查询权重，代码: {}", categoryCode);
        CategoryWeight categoryWeight = categoryWeightService.findByCategoryCode(categoryCode);
        return Result.success(categoryWeight);
    }
    
    @PostMapping
    public Result insert(@RequestBody CategoryWeight categoryWeight) {
        log.info("新增类别权重");
        categoryWeightService.insert(categoryWeight);
        return Result.success("新增成功");
    }
    
    @PutMapping("/{id}")
    public Result update(@PathVariable Long id, @RequestBody CategoryWeight categoryWeight) {
        log.info("更新类别权重，ID: {}", id);
        categoryWeight.setId(id);
        categoryWeightService.update(categoryWeight);
        return Result.success("更新成功");
    }
    
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Long id) {
        log.info("删除类别权重，ID: {}", id);
        categoryWeightService.deleteById(id);
        return Result.success("删除成功");
    }
    
    @DeleteMapping("/category/{categoryCode}")
    public Result deleteByCategoryCode(@PathVariable String categoryCode) {
        log.info("根据类别代码删除权重，代码: {}", categoryCode);
        categoryWeightService.deleteByCategoryCode(categoryCode);
        return Result.success("删除成功");
    }
    
    @GetMapping("/total")
    public Result getTotalWeight() {
        log.info("获取总权重");
        Integer totalWeight = categoryWeightService.getTotalWeight();
        return Result.success(totalWeight);
    }
    
    @GetMapping("/summary")
    public Result getWeightSummary() {
        log.info("获取权重汇总信息");
        List<CategoryWeight> activeWeights = categoryWeightService.findActive();
        Integer totalWeight = categoryWeightService.getTotalWeight();
        
        Map<String, Object> summary = new HashMap<>();
        summary.put("categories", activeWeights);
        summary.put("totalWeight", totalWeight);
        summary.put("isNormalized", totalWeight == 100);
        
        return Result.success(summary);
    }
    
    @PostMapping("/batch")
    public Result batchUpdate(@RequestBody List<CategoryWeight> categoryWeights) {
        log.info("批量更新类别权重，数量: {}", categoryWeights.size());
        categoryWeightService.batchUpdate(categoryWeights);
        return Result.success("批量更新成功");
    }
}
