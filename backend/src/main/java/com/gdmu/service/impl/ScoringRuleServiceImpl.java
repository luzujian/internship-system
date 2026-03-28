package com.gdmu.service.impl;

import com.gdmu.entity.CategoryWeight;
import com.gdmu.entity.ScoringRule;
import com.gdmu.exception.BusinessException;
import com.gdmu.mapper.CategoryWeightMapper;
import com.gdmu.mapper.ScoringRuleMapper;
import com.gdmu.service.EnhancedDeepSeekService;
import com.gdmu.service.ScoringRuleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class ScoringRuleServiceImpl implements ScoringRuleService {
    
    private final ScoringRuleMapper scoringRuleMapper;
    private final CategoryWeightMapper categoryWeightMapper;

    @Autowired
    @Lazy
    private EnhancedDeepSeekService enhancedDeepSeekService;

    @Autowired
    public ScoringRuleServiceImpl(ScoringRuleMapper scoringRuleMapper, CategoryWeightMapper categoryWeightMapper) {
        this.scoringRuleMapper = scoringRuleMapper;
        this.categoryWeightMapper = categoryWeightMapper;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insert(ScoringRule scoringRule) {
        log.debug("插入评分规则");
        
        if (scoringRule == null) {
            throw new BusinessException("评分规则不能为空");
        }
        
        if (scoringRule.getRuleName() == null || scoringRule.getRuleName().trim().isEmpty()) {
            throw new BusinessException("规则名称不能为空");
        }
        
        int result = scoringRuleMapper.insert(scoringRule);
        log.info("评分规则插入成功，ID: {}", scoringRule.getId());
        return result;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchInsert(List<ScoringRule> scoringRules) {
        log.debug("批量插入评分规则，数量: {}", scoringRules.size());
        
        if (scoringRules == null || scoringRules.isEmpty()) {
            throw new BusinessException("评分规则列表不能为空");
        }
        
        int totalInserted = 0;
        for (ScoringRule scoringRule : scoringRules) {
            if (scoringRule.getRuleName() == null || scoringRule.getRuleName().trim().isEmpty()) {
                throw new BusinessException("规则名称不能为空");
            }
            
            String categoryName = getCategoryName(scoringRule.getCategory());
            String description = generateDescription(categoryName, scoringRule.getRuleName(), scoringRule.getMinScore(), scoringRule.getMaxScore());
            scoringRule.setDescription(description);
            
            int result = scoringRuleMapper.insert(scoringRule);
            totalInserted += result;
        }
        
        log.info("批量插入评分规则成功，插入数量: {}", totalInserted);
        return totalInserted;
    }
    
    private String generateDescription(String categoryName, String level, int minScore, int maxScore) {
        try {
            String prompt = String.format("请为实习管理系统的%s评分等级生成一段简短的中文描述（50-100字）：\n" +
                    "类别：%s\n" +
                    "等级：%s\n" +
                    "分数范围：%d-%d分\n" +
                    "描述要求：简洁明了，突出该等级的特点和评价标准。",
                    categoryName, categoryName, level, minScore, maxScore);
            
            String aiDescription = enhancedDeepSeekService.chatWithResources(prompt, false);
            
            if (aiDescription != null && !aiDescription.trim().isEmpty()) {
                return aiDescription.trim();
            }
        } catch (Exception e) {
            log.warn("AI生成描述失败，使用默认描述: {}", e.getMessage());
        }
        
        return String.format("%s - %s等级（%d-%d分）", categoryName, level, minScore, maxScore);
    }
    
    private String getCategoryName(String category) {
        switch (category) {
            case "internship_performance":
                return "实习表现";
            case "academic_achievement":
                return "学术成果";
            case "team_collaboration":
                return "团队协作";
            case "innovation_ability":
                return "创新能力";
            case "teamwork":
                return "团队协作能力";
            case "problemSolving":
                return "问题解决能力";
            case "communication":
                return "沟通表达能力";
            case "learningAbility":
                return "学习能力";
            case "professionalism":
                return "职业素养";
            default:
                return category;
        }
    }
    
    @Override
    public List<ScoringRule> findAll() {
        log.debug("查询所有评分规则");
        return scoringRuleMapper.findAll();
    }
    
    @Override
    public ScoringRule findById(Long id) {
        log.debug("根据ID查询评分规则，ID: {}", id);
        
        if (id == null || id <= 0) {
            throw new BusinessException("评分规则ID无效");
        }
        
        return scoringRuleMapper.findById(id);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int update(ScoringRule scoringRule) {
        log.debug("更新评分规则，ID: {}", scoringRule.getId());
        
        if (scoringRule == null || scoringRule.getId() == null) {
            throw new BusinessException("评分规则或ID不能为空");
        }
        
        ScoringRule existing = scoringRuleMapper.findById(scoringRule.getId());
        if (existing == null) {
            throw new BusinessException("评分规则不存在");
        }
        
        int result = scoringRuleMapper.update(scoringRule);
        log.info("评分规则更新成功，ID: {}", scoringRule.getId());
        return result;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteById(Long id) {
        log.debug("删除评分规则，ID: {}", id);
        
        if (id == null || id <= 0) {
            throw new BusinessException("评分规则ID无效");
        }
        
        int result = scoringRuleMapper.deleteById(id);
        log.info("评分规则删除成功，ID: {}", id);
        return result;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByCategory(String category) {
        log.info("删除分类下所有评分规则，分类: {}", category);
        
        int result;
        if (category == null || category.trim().isEmpty()) {
            result = scoringRuleMapper.deleteByCategoryIsNull();
        } else {
            result = scoringRuleMapper.deleteByCategory(category);
        }
        
        log.info("删除分类下所有评分规则成功，删除数量: {}", result);
        return result;
    }
    
    @Override
    public List<ScoringRule> findByCategory(String category) {
        log.debug("根据分类查询评分规则，分类: {}", category);
        return scoringRuleMapper.findByCategory(category);
    }
    
    @Override
    public List<ScoringRule> findByRuleType(String ruleType) {
        log.debug("根据规则类型查询评分规则，类型: {}", ruleType);
        return scoringRuleMapper.findByRuleType(ruleType);
    }
    
    @Override
    public List<ScoringRule> findByStatus(Integer status) {
        log.debug("根据状态查询评分规则，状态: {}", status);
        return scoringRuleMapper.findByStatus(status);
    }
    
    @Override
    public List<ScoringRule> findByScenario(String scenario) {
        log.debug("根据场景查询评分规则，场景: {}", scenario);
        return scoringRuleMapper.findByScenario(scenario);
    }

    /**
     * 批量创建评分规则并自动生成AI描述
     * 每个category下创建5个等级规则（优秀、良好、中等、及格、不及格）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchCreateWithAIDescription(String categoryName) {
        log.info("批量创建评分规则并生成AI描述，类别: {}", categoryName);

        if (categoryName == null || categoryName.trim().isEmpty()) {
            throw new BusinessException("类别名称不能为空");
        }

        String categoryCode = categoryName.toLowerCase().replace(" ", "_");
        Date now = new Date();

        String[][] levels = {
            {"优秀", "90", "100"},
            {"良好", "80", "89"},
            {"中等", "70", "79"},
            {"及格", "60", "69"},
            {"不及格", "0", "59"}
        };

        List<ScoringRule> rules = new ArrayList<>();
        for (int i = 0; i < levels.length; i++) {
            String level = levels[i][0];
            int minScore = Integer.parseInt(levels[i][1]);
            int maxScore = Integer.parseInt(levels[i][2]);

            ScoringRule rule = new ScoringRule();
            rule.setRuleName(level);
            rule.setRuleCode(categoryCode + "_" + level.toLowerCase());
            rule.setCategory(categoryCode);
            rule.setWeight(20);
            rule.setSortOrder(i);
            rule.setMinScore(minScore);
            rule.setMaxScore(maxScore);
            rule.setStatus(1);
            rule.setApplicableScenarios("实习心得分析");

            String description = generateDescription(categoryName, level, minScore, maxScore);
            rule.setDescription(description);

            rules.add(rule);
        }

        int result = scoringRuleMapper.batchInsert(rules);
        redistributeWeights();
        log.info("批量创建评分规则成功，创建数量: {}", result);
        return result;
    }

    /**
     * 自动分配类别权重，使总权重为100
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int redistributeWeights() {
        log.info("自动分配类别权重");

        List<String> categories = scoringRuleMapper.findCategories();
        if (categories == null || categories.isEmpty()) {
            return 0;
        }

        int categoryCount = categories.size();
        int weightPerCategory = Math.floorDiv(100, categoryCount);
        int remainder = 100 % categoryCount;

        categoryWeightMapper.deleteAll();
        // 物理删除已标记为deleted的记录，避免唯一键冲突
        categoryWeightMapper.deletePhysicallyAll();

        Date now = new Date();
        List<CategoryWeight> weights = new ArrayList<>();

        for (int i = 0; i < categories.size(); i++) {
            String categoryCode = categories.get(i);
            int weight = weightPerCategory + (i < remainder ? 1 : 0);

            CategoryWeight categoryWeight = new CategoryWeight();
            categoryWeight.setCategoryCode(categoryCode);
            categoryWeight.setCategoryName(getCategoryDisplayName(categoryCode));
            categoryWeight.setWeight(weight);
            categoryWeight.setStatus(1);
            categoryWeight.setCreateTime(now);
            categoryWeight.setUpdateTime(now);

            weights.add(categoryWeight);
        }

        return categoryWeightMapper.batchInsert(weights);
    }

    private String getCategoryDisplayName(String categoryCode) {
        java.util.Map<String, String> categoryMap = new java.util.HashMap<>();
        categoryMap.put("internship_performance", "实习表现");
        categoryMap.put("academic_achievement", "学术成果");
        categoryMap.put("team_collaboration", "团队协作");
        categoryMap.put("innovation_ability", "创新能力");
        categoryMap.put("teamwork", "团队协作能力");
        categoryMap.put("problemSolving", "问题解决能力");
        categoryMap.put("communication", "沟通表达能力");
        categoryMap.put("learningAbility", "学习能力");
        categoryMap.put("professionalism", "职业素养");
        return categoryMap.getOrDefault(categoryCode, categoryCode);
    }
}
