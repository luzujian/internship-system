package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.dto.ScoringRuleBatchDTO;
import com.gdmu.entity.Result;
import com.gdmu.entity.ScoringRule;
import com.gdmu.service.ScoringRuleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/scoring-rule")
public class ScoringRuleController {

    @Autowired
    private ScoringRuleService scoringRuleService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "SCORING_RULE", operationType = "INSERT", description = "添加评分规则")
    public Result addRule(@RequestBody ScoringRule scoringRule) {
        log.info("添加评分规则: {}", scoringRule.getRuleName());
        try {
            int result = scoringRuleService.insert(scoringRule);
            return Result.success("添加成功", scoringRule);
        } catch (Exception e) {
            log.error("添加评分规则失败: {}", e.getMessage());
            return Result.error("添加失败: " + e.getMessage());
        }
    }

    @PostMapping("/batch")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "SCORING_RULE", operationType = "INSERT", description = "批量添加评分规则")
    public Result batchAddRules(@RequestBody ScoringRuleBatchDTO batchDTO) {
        log.info("批量添加评分规则，分类: {}, 规则数量: {}", batchDTO.getCategory(), batchDTO.getRules().size());
        try {
            int result = scoringRuleService.batchInsert(batchDTO.getRules());
            return Result.success("批量添加成功", result);
        } catch (Exception e) {
            log.error("批量添加评分规则失败: {}", e.getMessage());
            return Result.error("批量添加失败: " + e.getMessage());
        }
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result getRules() {
        log.info("查询所有评分规则");
        try {
            List<ScoringRule> rules = scoringRuleService.findAll();
            return Result.success(rules);
        } catch (Exception e) {
            log.error("查询评分规则失败: {}", e.getMessage());
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "SCORING_RULE", operationType = "SELECT", description = "查询评分规则详情")
    public Result getRuleById(@PathVariable Long id) {
        log.info("查询评分规则详情，ID: {}", id);
        try {
            ScoringRule rule = scoringRuleService.findById(id);
            return Result.success(rule);
        } catch (Exception e) {
            log.error("查询评分规则详情失败: {}", e.getMessage());
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "SCORING_RULE", operationType = "UPDATE", description = "更新评分规则")
    public Result updateRule(@PathVariable Long id, @RequestBody ScoringRule scoringRule) {
        log.info("更新评分规则，ID: {}", id);
        try {
            scoringRule.setId(id);
            int result = scoringRuleService.update(scoringRule);
            return Result.success("更新成功", scoringRule);
        } catch (Exception e) {
            log.error("更新评分规则失败: {}", e.getMessage());
            return Result.error("更新失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "SCORING_RULE", operationType = "DELETE", description = "删除评分规则")
    public Result deleteRule(@PathVariable Long id) {
        log.info("删除评分规则，ID: {}", id);
        try {
            int result = scoringRuleService.deleteById(id);
            return Result.success("删除成功");
        } catch (Exception e) {
            log.error("删除评分规则失败: {}", e.getMessage());
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/category/{category}")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "SCORING_RULE", operationType = "DELETE", description = "删除分类下所有评分规则")
    public Result deleteRulesByCategory(@PathVariable String category) {
        log.info("删除分类下所有评分规则，分类: {}", category);
        try {
            int result = scoringRuleService.deleteByCategory(category);
            return Result.success("删除成功，共删除" + result + "条规则");
        } catch (Exception e) {
            log.error("删除分类下所有评分规则失败: {}", e.getMessage());
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    @GetMapping("/category/{category}")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "SCORING_RULE", operationType = "SELECT", description = "按分类查询评分规则")
    public Result getRulesByCategory(@PathVariable String category) {
        log.info("按分类查询评分规则，分类: {}", category);
        try {
            List<ScoringRule> rules = scoringRuleService.findByCategory(category);
            return Result.success(rules);
        } catch (Exception e) {
            log.error("按分类查询评分规则失败: {}", e.getMessage());
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/rule-type/{ruleType}")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "SCORING_RULE", operationType = "SELECT", description = "按规则类型查询评分规则")
    public Result getRulesByRuleType(@PathVariable String ruleType) {
        log.info("按规则类型查询评分规则，类型: {}", ruleType);
        try {
            List<ScoringRule> rules = scoringRuleService.findByRuleType(ruleType);
            return Result.success(rules);
        } catch (Exception e) {
            log.error("按规则类型查询评分规则失败: {}", e.getMessage());
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "SCORING_RULE", operationType = "SELECT", description = "按状态查询评分规则")
    public Result getRulesByStatus(@PathVariable Integer status) {
        log.info("按状态查询评分规则，状态: {}", status);
        try {
            List<ScoringRule> rules = scoringRuleService.findByStatus(status);
            return Result.success(rules);
        } catch (Exception e) {
            log.error("按状态查询评分规则失败: {}", e.getMessage());
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/scenario/{scenario}")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "SCORING_RULE", operationType = "SELECT", description = "按场景查询评分规则")
    public Result getRulesByScenario(@PathVariable String scenario) {
        log.info("按场景查询评分规则，场景: {}", scenario);
        try {
            List<ScoringRule> rules = scoringRuleService.findByScenario(scenario);
            return Result.success(rules);
        } catch (Exception e) {
            log.error("按场景查询评分规则失败: {}", e.getMessage());
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @PostMapping("/batch-create-with-ai")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "SCORING_RULE", operationType = "INSERT", description = "批量创建评分规则并生成AI描述")
    public Result batchCreateWithAIDescription(@RequestBody Map<String, String> request) {
        log.info("批量创建评分规则并生成AI描述: {}", request);
        try {
            String categoryName = request.get("categoryName");
            int result = scoringRuleService.batchCreateWithAIDescription(categoryName);
            return Result.success("批量创建成功，共创建" + result + "条规则");
        } catch (Exception e) {
            log.error("批量创建评分规则失败: {}", e.getMessage());
            return Result.error("批量创建失败: " + e.getMessage());
        }
    }

    @PostMapping("/redistribute-weights")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "SCORING_RULE", operationType = "UPDATE", description = "重新分配类别权重")
    public Result redistributeWeights() {
        log.info("重新分配类别权重");
        try {
            int result = scoringRuleService.redistributeWeights();
            return Result.success("权重重新分配成功");
        } catch (Exception e) {
            log.error("重新分配权重失败: {}", e.getMessage());
            return Result.error("重新分配权重失败: " + e.getMessage());
        }
    }
}
