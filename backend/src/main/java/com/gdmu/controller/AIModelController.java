package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.entity.AIModel;
import com.gdmu.entity.Result;
import com.gdmu.service.AIModelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/ai-model")
public class AIModelController {

    @Autowired
    private AIModelService aiModelService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "AI_MODEL", operationType = "INSERT", description = "添加AI模型")
    public Result addAIModel(@RequestBody AIModel aiModel) {
        log.info("添加AI模型: {}", aiModel.getModelName());
        try {
            int result = aiModelService.insert(aiModel);
            return Result.success("添加成功", aiModel);
        } catch (Exception e) {
            log.error("添加AI模型失败: {}", e.getMessage());
            return Result.error("添加失败: " + e.getMessage());
        }
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result getAIModels() {
        log.info("查询所有AI模型");
        try {
            List<AIModel> models = aiModelService.findAll();
            return Result.success(models);
        } catch (Exception e) {
            log.error("查询AI模型失败: {}", e.getMessage());
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/enabled")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "AI_MODEL", operationType = "SELECT", description = "查询启用的AI模型")
    public Result getEnabledAIModels() {
        log.info("查询启用的AI模型");
        try {
            List<AIModel> models = aiModelService.findEnabledModels();
            return Result.success(models);
        } catch (Exception e) {
            log.error("查询启用的AI模型失败: {}", e.getMessage());
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/default")
    @PreAuthorize("hasRole('ADMIN')")
    public Result getDefaultAIModel() {
        log.info("查询默认AI模型");
        try {
            AIModel model = aiModelService.findDefaultModel();
            return Result.success(model);
        } catch (Exception e) {
            log.error("查询默认AI模型失败: {}", e.getMessage());
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "AI_MODEL", operationType = "SELECT", description = "查询AI模型详情")
    public Result getAIModelById(@PathVariable Long id) {
        log.info("查询AI模型详情，ID: {}", id);
        try {
            AIModel model = aiModelService.findById(id);
            return Result.success(model);
        } catch (Exception e) {
            log.error("查询AI模型详情失败: {}", e.getMessage());
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "AI_MODEL", operationType = "UPDATE", description = "更新AI模型")
    public Result updateAIModel(@PathVariable Long id, @RequestBody AIModel aiModel) {
        log.info("更新AI模型，ID: {}", id);
        try {
            aiModel.setId(id);
            int result = aiModelService.update(aiModel);
            return Result.success("更新成功", aiModel);
        } catch (Exception e) {
            log.error("更新AI模型失败: {}", e.getMessage());
            return Result.error("更新失败: " + e.getMessage());
        }
    }

    @PutMapping("/{id}/set-default")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "AI_MODEL", operationType = "UPDATE", description = "设置默认AI模型")
    public Result setAsDefault(@PathVariable Long id, @RequestBody Map<String, String> request) {
        log.info("设置默认AI模型，ID: {}", id);
        try {
            String updater = request.get("updater");
            int result = aiModelService.setAsDefault(id, updater);
            return Result.success("设置默认模型成功");
        } catch (Exception e) {
            log.error("设置默认AI模型失败: {}", e.getMessage());
            return Result.error("设置失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "AI_MODEL", operationType = "DELETE", description = "删除AI模型")
    public Result deleteAIModel(@PathVariable Long id) {
        log.info("删除AI模型，ID: {}", id);
        try {
            int result = aiModelService.deleteById(id);
            return Result.success("删除成功");
        } catch (Exception e) {
            log.error("删除AI模型失败: {}", e.getMessage());
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "AI_MODEL", operationType = "DELETE", description = "删除所有AI模型")
    public Result deleteAllAIModels() {
        log.info("删除所有AI模型");
        try {
            int result = aiModelService.deleteAll("admin");
            return Result.success("删除成功，共删除" + result + "个模型");
        } catch (Exception e) {
            log.error("删除所有AI模型失败: {}", e.getMessage());
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    @GetMapping("/provider/{provider}")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "AI_MODEL", operationType = "SELECT", description = "按提供商查询AI模型")
    public Result getAIModelsByProvider(@PathVariable String provider) {
        log.info("按提供商查询AI模型，提供商: {}", provider);
        try {
            List<AIModel> models = aiModelService.findByProvider(provider);
            return Result.success(models);
        } catch (Exception e) {
            log.error("按提供商查询AI模型失败: {}", e.getMessage());
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "AI_MODEL", operationType = "SELECT", description = "按状态查询AI模型")
    public Result getAIModelsByStatus(@PathVariable String status) {
        log.info("按状态查询AI模型，状态: {}", status);
        try {
            Integer statusValue = null;
            if (status != null && !status.equals("undefined") && !status.trim().isEmpty()) {
                try {
                    statusValue = Integer.parseInt(status);
                } catch (NumberFormatException e) {
                    log.warn("状态参数格式错误: {}", status);
                    return Result.error("状态参数格式错误");
                }
            }
            
            List<AIModel> models = aiModelService.findByStatus(statusValue);
            return Result.success(models);
        } catch (Exception e) {
            log.error("按状态查询AI模型失败: {}", e.getMessage());
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/search/{keyword}")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "AI_MODEL", operationType = "SELECT", description = "搜索AI模型")
    public Result searchAIModels(@PathVariable String keyword) {
        log.info("搜索AI模型，关键词: {}", keyword);
        try {
            List<AIModel> models = aiModelService.searchByKeyword(keyword);
            return Result.success(models);
        } catch (Exception e) {
            log.error("搜索AI模型失败: {}", e.getMessage());
            return Result.error("搜索失败: " + e.getMessage());
        }
    }
}
