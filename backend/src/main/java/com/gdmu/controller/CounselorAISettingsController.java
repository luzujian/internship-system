package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.entity.CounselorAISettings;
import com.gdmu.entity.CounselorScoringRule;
import com.gdmu.entity.CounselorCategoryWeight;
import com.gdmu.entity.Result;
import com.gdmu.service.CounselorAISettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/counselor/ai-settings")
public class CounselorAISettingsController {
    
    @Autowired
    private CounselorAISettingsService counselorAISettingsService;
    
    @GetMapping("/{counselorId}")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN')")
    @Log(module = "COUNSELOR_AI_SETTINGS", operationType = "SELECT", description = "查询辅导员AI评分设置")
    public Result getSettings(@PathVariable Long counselorId) {
        log.info("查询辅导员AI评分设置，辅导员ID: {}", counselorId);
        try {
            CounselorAISettings settings = counselorAISettingsService.findByCounselorId(counselorId);
            List<CounselorScoringRule> rules = counselorAISettingsService.getScoringRules(counselorId);
            List<CounselorCategoryWeight> weights = counselorAISettingsService.getCategoryWeights(counselorId);
            
            Map<String, Object> data = new HashMap<>();
            data.put("settings", settings);
            data.put("scoringRules", rules);
            data.put("categoryWeights", weights);
            
            return Result.success(data);
        } catch (Exception e) {
            log.error("查询辅导员AI评分设置失败: {}", e.getMessage());
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN')")
    @Log(module = "COUNSELOR_AI_SETTINGS", operationType = "INSERT", description = "保存辅导员AI评分设置")
    public Result saveSettings(@RequestBody Map<String, Object> request) {
        log.info("保存辅导员AI评分设置: {}", request);
        try {
            Long counselorId = Long.parseLong(request.get("counselorId").toString());
            
            CounselorAISettings settings = new CounselorAISettings();
            settings.setCounselorId(counselorId);
            
            if (request.containsKey("enableAiScoring")) {
                settings.setEnableAiScoring(Integer.parseInt(request.get("enableAiScoring").toString()));
            }
            if (request.containsKey("aiModelCode")) {
                settings.setAiModelCode(request.get("aiModelCode").toString());
            }
            
            counselorAISettingsService.saveOrUpdate(settings);
            
            if (request.containsKey("scoringRules")) {
                List<Map<String, Object>> rulesData = (List<Map<String, Object>>) request.get("scoringRules");
                List<CounselorScoringRule> rules = new java.util.ArrayList<>();
                
                for (Map<String, Object> ruleData : rulesData) {
                    CounselorScoringRule rule = new CounselorScoringRule();
                    rule.setRuleName((String) ruleData.get("ruleName"));
                    rule.setRuleCode((String) ruleData.get("ruleCode"));
                    rule.setCategory((String) ruleData.get("category"));
                    rule.setWeight(Integer.parseInt(ruleData.get("weight").toString()));
                    rule.setMinScore(Integer.parseInt(ruleData.get("minScore").toString()));
                    rule.setMaxScore(Integer.parseInt(ruleData.get("maxScore").toString()));
                    rule.setDescription((String) ruleData.get("description"));
                    rule.setEvaluationCriteria((String) ruleData.get("evaluationCriteria"));
                    rule.setSortOrder(Integer.parseInt(ruleData.get("sortOrder").toString()));
                    rule.setStatus(1);
                    rules.add(rule);
                }
                
                counselorAISettingsService.saveScoringRules(counselorId, rules);
            }
            
            return Result.success("保存成功");
        } catch (Exception e) {
            log.error("保存辅导员AI评分设置失败: {}", e.getMessage());
            return Result.error("保存失败: " + e.getMessage());
        }
    }
    
    @PutMapping
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN')")
    @Log(module = "COUNSELOR_AI_SETTINGS", operationType = "UPDATE", description = "更新辅导员AI评分设置")
    public Result updateSettings(@RequestBody CounselorAISettings settings) {
        log.info("更新辅导员AI评分设置: {}", settings);
        try {
            int result = counselorAISettingsService.update(settings);
            if (result > 0) {
                return Result.success("更新成功");
            }
            return Result.error("更新失败");
        } catch (Exception e) {
            log.error("更新辅导员AI评分设置失败: {}", e.getMessage());
            return Result.error("更新失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/scoring-rules/{counselorId}")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN')")
    @Log(module = "COUNSELOR_AI_SETTINGS", operationType = "SELECT", description = "查询辅导员评分规则")
    public Result getScoringRules(@PathVariable Long counselorId) {
        log.info("查询辅导员评分规则，辅导员ID: {}", counselorId);
        try {
            List<CounselorScoringRule> rules = counselorAISettingsService.getScoringRules(counselorId);
            return Result.success(rules);
        } catch (Exception e) {
            log.error("查询辅导员评分规则失败: {}", e.getMessage());
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/scoring-rules/{counselorId}/category/{category}")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN')")
    @Log(module = "COUNSELOR_AI_SETTINGS", operationType = "SELECT", description = "按分类查询辅导员评分规则")
    public Result getScoringRulesByCategory(@PathVariable Long counselorId, @PathVariable String category) {
        log.info("按分类查询辅导员评分规则，辅导员ID: {}, 分类: {}", counselorId, category);
        try {
            List<CounselorScoringRule> rules = counselorAISettingsService.getScoringRulesByCategory(counselorId, category);
            return Result.success(rules);
        } catch (Exception e) {
            log.error("按分类查询辅导员评分规则失败: {}", e.getMessage());
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/scoring-rules/{counselorId}/categories")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN')")
    @Log(module = "COUNSELOR_AI_SETTINGS", operationType = "SELECT", description = "查询辅导员评分规则分类")
    public Result getCategories(@PathVariable Long counselorId) {
        log.info("查询辅导员评分规则分类，辅导员ID: {}", counselorId);
        try {
            List<String> categories = counselorAISettingsService.getCategories(counselorId);
            return Result.success(categories);
        } catch (Exception e) {
            log.error("查询辅导员评分规则分类失败: {}", e.getMessage());
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/scoring-rules")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN')")
    @Log(module = "COUNSELOR_AI_SETTINGS", operationType = "INSERT", description = "保存辅导员评分规则")
    public Result saveScoringRules(@RequestBody Map<String, Object> request) {
        log.info("保存辅导员评分规则: {}", request);
        try {
            Long counselorId = Long.parseLong(request.get("counselorId").toString());
            List<Map<String, Object>> rulesData = (List<Map<String, Object>>) request.get("rules");
            
            List<CounselorScoringRule> rules = new java.util.ArrayList<>();
            for (Map<String, Object> ruleData : rulesData) {
                CounselorScoringRule rule = new CounselorScoringRule();
                rule.setRuleName((String) ruleData.get("ruleName"));
                rule.setRuleCode((String) ruleData.get("ruleCode"));
                rule.setCategory((String) ruleData.get("category"));
                rule.setWeight(Integer.parseInt(ruleData.get("weight").toString()));
                rule.setMinScore(Integer.parseInt(ruleData.get("minScore").toString()));
                rule.setMaxScore(Integer.parseInt(ruleData.get("maxScore").toString()));
                rule.setDescription((String) ruleData.get("description"));
                rule.setEvaluationCriteria((String) ruleData.get("evaluationCriteria"));
                rule.setSortOrder(Integer.parseInt(ruleData.get("sortOrder").toString()));
                rule.setStatus(1);
                rules.add(rule);
            }
            
            int result = counselorAISettingsService.saveScoringRules(counselorId, rules);
            return Result.success("保存成功，共保存" + result + "条规则");
        } catch (Exception e) {
            log.error("保存辅导员评分规则失败: {}", e.getMessage());
            return Result.error("保存失败: " + e.getMessage());
        }
    }
    
    @PutMapping("/scoring-rules/{id}")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN')")
    @Log(module = "COUNSELOR_AI_SETTINGS", operationType = "UPDATE", description = "更新辅导员评分规则")
    public Result updateScoringRule(@PathVariable Long id, @RequestBody CounselorScoringRule rule) {
        log.info("更新辅导员评分规则，ID: {}", id);
        try {
            rule.setId(id);
            int result = counselorAISettingsService.updateScoringRule(rule);
            if (result > 0) {
                return Result.success("更新成功");
            }
            return Result.error("更新失败");
        } catch (Exception e) {
            log.error("更新辅导员评分规则失败: {}", e.getMessage());
            return Result.error("更新失败: " + e.getMessage());
        }
    }
    
    @DeleteMapping("/scoring-rules/{ruleId}")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN')")
    @Log(module = "COUNSELOR_AI_SETTINGS", operationType = "DELETE", description = "删除辅导员评分规则")
    public Result deleteScoringRule(@PathVariable Long ruleId) {
        log.info("删除辅导员评分规则，规则ID: {}", ruleId);
        try {
            int result = counselorAISettingsService.deleteScoringRule(ruleId);
            if (result > 0) {
                return Result.success("删除成功");
            }
            return Result.error("删除失败");
        } catch (Exception e) {
            log.error("删除辅导员评分规则失败: {}", e.getMessage());
            return Result.error("删除失败: " + e.getMessage());
        }
    }
    
    @DeleteMapping("/scoring-rules/{counselorId}/category/{category}")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN')")
    @Log(module = "COUNSELOR_AI_SETTINGS", operationType = "DELETE", description = "删除辅导员分类下所有评分规则")
    public Result deleteScoringRulesByCategory(@PathVariable Long counselorId, @PathVariable String category) {
        log.info("删除辅导员分类下所有评分规则，辅导员ID: {}, 分类: {}", counselorId, category);
        try {
            int result = counselorAISettingsService.deleteScoringRulesByCategory(counselorId, category);
            counselorAISettingsService.deleteCategoryWeightByCategoryCode(counselorId, category);
            counselorAISettingsService.redistributeWeights(counselorId);
            return Result.success("删除成功，共删除" + result + "条规则");
        } catch (Exception e) {
            log.error("删除辅导员分类下所有评分规则失败: {}", e.getMessage());
            return Result.error("删除失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/scoring-rules/batch-create")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN')")
    @Log(module = "COUNSELOR_AI_SETTINGS", operationType = "INSERT", description = "批量创建辅导员评分规则并生成AI描述")
    public Result batchCreateWithAIDescription(@RequestBody Map<String, Object> request) {
        log.info("批量创建辅导员评分规则并生成AI描述: {}", request);
        try {
            Long counselorId = Long.parseLong(request.get("counselorId").toString());
            String categoryName = request.get("categoryName").toString();
            
            int result = counselorAISettingsService.batchCreateWithAIDescription(counselorId, categoryName);
            return Result.success("批量创建成功，共创建" + result + "条规则");
        } catch (Exception e) {
            log.error("批量创建辅导员评分规则失败: {}", e.getMessage());
            return Result.error("批量创建失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/category-weight/{counselorId}")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN')")
    @Log(module = "COUNSELOR_AI_SETTINGS", operationType = "SELECT", description = "查询辅导员分类权重")
    public Result getCategoryWeights(@PathVariable Long counselorId) {
        log.info("查询辅导员分类权重，辅导员ID: {}", counselorId);
        try {
            List<CounselorCategoryWeight> weights = counselorAISettingsService.getCategoryWeights(counselorId);
            return Result.success(weights);
        } catch (Exception e) {
            log.error("查询辅导员分类权重失败: {}", e.getMessage());
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/category-weight/{counselorId}/active")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN')")
    @Log(module = "COUNSELOR_AI_SETTINGS", operationType = "SELECT", description = "查询辅导员启用的分类权重")
    public Result getActiveCategoryWeights(@PathVariable Long counselorId) {
        log.info("查询辅导员启用的分类权重，辅导员ID: {}", counselorId);
        try {
            List<CounselorCategoryWeight> weights = counselorAISettingsService.getCategoryWeights(counselorId);
            Integer totalWeight = counselorAISettingsService.getTotalWeight(counselorId);
            
            Map<String, Object> data = new HashMap<>();
            data.put("weights", weights);
            data.put("totalWeight", totalWeight);
            
            return Result.success(data);
        } catch (Exception e) {
            log.error("查询辅导员启用的分类权重失败: {}", e.getMessage());
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/category-weight/batch")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN')")
    @Log(module = "COUNSELOR_AI_SETTINGS", operationType = "UPDATE", description = "批量更新辅导员分类权重")
    public Result batchUpdateCategoryWeights(@RequestBody List<Map<String, Object>> request) {
        log.info("批量更新辅导员分类权重，数量: {}", request.size());
        try {
            if (request.isEmpty()) {
                return Result.error("权重数据不能为空");
            }
            
            Long counselorId = Long.parseLong(request.get(0).get("counselorId").toString());
            
            List<CounselorCategoryWeight> weights = new java.util.ArrayList<>();
            for (Map<String, Object> weightData : request) {
                CounselorCategoryWeight weight = new CounselorCategoryWeight();
                weight.setCounselorId(Long.parseLong(weightData.get("counselorId").toString()));
                weight.setCategoryCode((String) weightData.get("categoryCode"));
                weight.setCategoryName((String) weightData.get("categoryName"));
                weight.setWeight(Integer.parseInt(weightData.get("weight").toString()));
                weight.setStatus(weightData.containsKey("status") ? Integer.parseInt(weightData.get("status").toString()) : 1);
                weights.add(weight);
            }
            
            counselorAISettingsService.deleteCategoryWeightByCategoryCode(counselorId, null);
            int result = counselorAISettingsService.saveCategoryWeights(counselorId, weights);
            return Result.success("批量更新成功");
        } catch (Exception e) {
            log.error("批量更新辅导员分类权重失败: {}", e.getMessage());
            return Result.error("批量更新失败: " + e.getMessage());
        }
    }
    
    @DeleteMapping("/category-weight/{counselorId}/category/{categoryCode}")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN')")
    @Log(module = "COUNSELOR_AI_SETTINGS", operationType = "DELETE", description = "删除辅导员分类权重")
    public Result deleteCategoryWeightByCategoryCode(@PathVariable Long counselorId, @PathVariable String categoryCode) {
        log.info("删除辅导员分类权重，辅导员ID: {}, 分类代码: {}", counselorId, categoryCode);
        try {
            int result = counselorAISettingsService.deleteCategoryWeightByCategoryCode(counselorId, categoryCode);
            return Result.success("删除成功");
        } catch (Exception e) {
            log.error("删除辅导员分类权重失败: {}", e.getMessage());
            return Result.error("删除失败: " + e.getMessage());
        }
    }
}
