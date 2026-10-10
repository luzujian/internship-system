package com.internship.controller;

import com.internship.anno.Log;
import com.internship.entity.AIModel;
import com.internship.entity.Result;
import com.internship.exception.BusinessException;
import com.internship.service.AIModelService;
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

    @Autowired private com.internship.agent.business.AgentVoiceSettings voiceSettings;

    @GetMapping("/capabilities")
    @PreAuthorize("hasRole('ADMIN')")
    public Result capabilities(@RequestParam String modelCode) {
        return Result.success(com.internship.util.AIModelCapabilities.supported(modelCode.trim()));
    }

    @GetMapping("/voice-model")
    @PreAuthorize("hasRole('ADMIN')")
    public Result getVoiceModel(){return Result.success(voiceSettings.selection());}

    @PutMapping("/voice-model")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "AI_MODEL", operationType = "UPDATE", description = "切换实时语音模型")
    public Result setVoiceModel(@RequestBody Map<String,Long> body){
        Long id=body.get("modelId");
        if(id==null)return Result.error("请选择语音模型。");
        return Result.success(voiceSettings.save(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "AI_MODEL", operationType = "INSERT", description = "添加AI模型")
    public Result addAIModel(@RequestBody AIModel aiModel) {
        log.info("添加AI模型: {}", aiModel.getModelName());
        try {
            int result = aiModelService.insert(aiModel);
            return Result.success("添加成功", aiModelService.maskForOutput(aiModel));
        } catch (Exception e) {
            return configurationError("添加失败", e);
        }
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result getAIModels() {
        log.info("查询所有AI模型");
        try {
            List<AIModel> models = aiModelService.findAll();
            return Result.success(aiModelService.maskForOutput(models));
        } catch (Exception e) {
            log.error("查询AI模型失败: {}", e.getMessage());
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/public/enabled")
    public Result getPublicEnabledAIModels() {
        try {
            List<AIModel> models = aiModelService.findEnabledModels();
            return Result.success(aiModelService.maskForOutput(models));
        } catch (Exception e) {
            log.error("查询启用的AI模型失败: {}", e.getMessage());
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
            return Result.success(aiModelService.maskForOutput(models));
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
            return Result.success(aiModelService.maskForOutput(model));
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
            return Result.success(aiModelService.maskForOutput(model));
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
            return Result.success("更新成功", aiModelService.maskForOutput(aiModel));
        } catch (Exception e) {
            return configurationError("更新失败", e);
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
            return Result.success(aiModelService.maskForOutput(models));
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
            return Result.success(aiModelService.maskForOutput(models));
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
            return Result.success(aiModelService.maskForOutput(models));
        } catch (Exception e) {
            log.error("搜索AI模型失败: {}", e.getMessage());
            return Result.error("搜索失败: " + e.getMessage());
        }
    }

    /**
     * 一键测连：用表单里的地址/Key/实际请求模型向厂商发一次最小请求。
     * 传 id 且 Key 留空时沿用该模型已保存的 Key；不传 id 则纯测表单值，不写库。
     */
    @PostMapping("/test")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "AI_MODEL", operationType = "SELECT", description = "测试AI模型连接")
    public Result testAIModelConnection(@RequestBody AIModel probe) {
        log.info("测试AI模型连接");
        try {
            Map<String, Object> result = aiModelService.testConnection(probe);
            boolean ok = Boolean.TRUE.equals(result.get("ok"));
            // 厂商侧拒绝（401/404 等）属于“测出了结果”，仍用 200 返回安全错误与延迟；
            // 只有参数缺失等根本没发出去的情况才走 error。
            return Result.success(ok ? "连接成功" : String.valueOf(result.get("error")), result);
        } catch (Exception e) {
            return configurationError("测试失败", e);
        }
    }

    @PostMapping("/models")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "AI_MODEL", operationType = "SELECT", description = "获取网关模型列表")
    public Result discoverAIModels(@RequestBody AIModel probe) {
        try {
            return Result.success(aiModelService.discoverModels(probe));
        } catch (Exception e) {
            return configurationError("获取模型列表失败", e);
        }
    }

    /**
     * 提取未保存模板；真实模型 ID 不改写，不复制密钥。
     */
    @PostMapping("/{id}/duplicate")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "AI_MODEL", operationType = "SELECT", description = "提取AI模型配置模板")
    public Result duplicateAIModel(@PathVariable Long id, @RequestBody(required = false) Map<String, String> request) {
        log.info("复制AI模型配置，ID: {}", id);
        try {
            String creator = request == null ? "admin" : request.getOrDefault("creator", "admin");
            AIModel copy = aiModelService.duplicateModel(id, creator);
            return Result.success("已生成未保存模板，请填写真实模型 ID 和 API Key 后保存", aiModelService.maskForOutput(copy));
        } catch (Exception e) {
            return configurationError("复制失败", e);
        }
    }

    private Result configurationError(String action, Exception exception) {
        // JDBC/HTTP 异常可能携带完整参数，不能将异常原文写日志或交给调用者。
        log.warn("{}，异常类型: {}", action, exception.getClass().getSimpleName());
        return Result.error(action + "：" + (exception instanceof BusinessException
                ? exception.getMessage() : "操作未完成，请检查配置或联系管理员。"));
    }
}
