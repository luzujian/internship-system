package com.gdmu.service.impl;

import com.gdmu.entity.ScoringRule;
import com.gdmu.exception.BusinessException;
import com.gdmu.mapper.ScoringRuleMapper;
import com.gdmu.service.EnhancedDeepSeekService;
import com.gdmu.service.ScoringRuleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class ScoringRuleServiceImpl implements ScoringRuleService {
    
    private final ScoringRuleMapper scoringRuleMapper;
    
    @Autowired
    @Lazy
    private EnhancedDeepSeekService enhancedDeepSeekService;
    
    @Autowired
    public ScoringRuleServiceImpl(ScoringRuleMapper scoringRuleMapper) {
        this.scoringRuleMapper = scoringRuleMapper;
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
}
