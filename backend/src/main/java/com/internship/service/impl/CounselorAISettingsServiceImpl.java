package com.internship.service.impl;

import com.internship.entity.CounselorAISettings;
import com.internship.entity.CounselorScoringRule;
import com.internship.entity.CounselorCategoryWeight;
import com.internship.exception.BusinessException;
import com.internship.mapper.CounselorAISettingsMapper;
import com.internship.mapper.CounselorScoringRuleMapper;
import com.internship.mapper.CounselorCategoryWeightMapper;
import com.internship.service.CounselorAISettingsService;
import com.internship.service.EnhancedDeepSeekService;
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
public class CounselorAISettingsServiceImpl implements CounselorAISettingsService {
    
    @Autowired
    private CounselorAISettingsMapper counselorAISettingsMapper;
    
    @Autowired
    private CounselorScoringRuleMapper counselorScoringRuleMapper;
    
    @Autowired
    private CounselorCategoryWeightMapper counselorCategoryWeightMapper;
    
    @Autowired
    @Lazy
    private EnhancedDeepSeekService enhancedDeepSeekService;
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int saveOrUpdate(CounselorAISettings settings) {
        CounselorAISettings existing = counselorAISettingsMapper.findByCounselorId(settings.getCounselorId());
        
        if (existing != null) {
            settings.setId(existing.getId());
            settings.setUpdateTime(new Date());
            return counselorAISettingsMapper.update(settings);
        } else {
            settings.setCreateTime(new Date());
            settings.setUpdateTime(new Date());
            return counselorAISettingsMapper.insert(settings);
        }
    }
    
    @Override
    public CounselorAISettings findByCounselorId(Long counselorId) {
        return counselorAISettingsMapper.findByCounselorId(counselorId);
    }
    
    @Override
    public int update(CounselorAISettings settings) {
        settings.setUpdateTime(new Date());
        return counselorAISettingsMapper.update(settings);
    }
    
    @Override
    public boolean isAiScoringEnabled(Long counselorId) {
        CounselorAISettings settings = counselorAISettingsMapper.findByCounselorId(counselorId);
        return settings != null && settings.getEnableAiScoring() != null && settings.getEnableAiScoring() == 1;
    }
    
    @Override
    public List<CounselorScoringRule> getScoringRules(Long counselorId) {
        return counselorScoringRuleMapper.findByCounselorId(counselorId);
    }
    
    @Override
    public List<CounselorScoringRule> getScoringRulesByCategory(Long counselorId, String category) {
        return counselorScoringRuleMapper.findByCounselorIdAndCategory(counselorId, category);
    }
    
    @Override
    public List<String> getCategories(Long counselorId) {
        return counselorScoringRuleMapper.findCategoriesByCounselorId(counselorId);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int saveScoringRules(Long counselorId, List<CounselorScoringRule> rules) {
        counselorScoringRuleMapper.deleteByCounselorId(counselorId);
        
        if (rules == null || rules.isEmpty()) {
            return 0;
        }
        
        Date now = new Date();
        int sortOrder = 0;
        for (CounselorScoringRule rule : rules) {
            rule.setCounselorId(counselorId);
            rule.setCreateTime(now);
            rule.setUpdateTime(now);
            rule.setDeleted(0);
            if (rule.getStatus() == null) {
                rule.setStatus(1);
            }
            if (rule.getSortOrder() == null) {
                rule.setSortOrder(sortOrder++);
            }
        }
        
        return counselorScoringRuleMapper.batchInsert(rules);
    }
    
    @Override
    public int updateScoringRule(CounselorScoringRule rule) {
        rule.setUpdateTime(new Date());
        return counselorScoringRuleMapper.update(rule);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteScoringRule(Long ruleId) {
        log.info("删除评分规则，规则ID: {}", ruleId);
        int result = counselorScoringRuleMapper.deleteById(ruleId);
        log.info("删除评分规则结果: {}", result);
        return result;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteScoringRulesByCategory(Long counselorId, String category) {
        log.info("删除分类下所有评分规则，辅导员ID: {}, 分类: {}", counselorId, category);
        int result = counselorScoringRuleMapper.deleteByCounselorIdAndCategory(counselorId, category);
        log.info("删除分类下所有评分规则结果: {}", result);
        return result;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchCreateWithAIDescription(Long counselorId, String categoryName) {
        log.info("批量创建辅导员评分规则并生成AI描述，辅导员ID: {}, 类别: {}", counselorId, categoryName);

        if (counselorId == null) {
            throw new BusinessException("辅导员ID不能为空");
        }

        if (categoryName == null || categoryName.trim().isEmpty()) {
            throw new BusinessException("类别名称不能为空");
        }

        String categoryCode = categoryName.toLowerCase().replace(" ", "_");

        List<CounselorScoringRule> rules = new ArrayList<>();
        Date now = new Date();

        String[][] levels = {
            {"优秀", "90", "100"},
            {"良好", "80", "89"},
            {"中等", "70", "79"},
            {"及格", "60", "69"},
            {"不及格", "0", "59"}
        };

        for (int i = 0; i < levels.length; i++) {
            String level = levels[i][0];
            int minScore = Integer.parseInt(levels[i][1]);
            int maxScore = Integer.parseInt(levels[i][2]);

            CounselorScoringRule rule = new CounselorScoringRule();
            rule.setCounselorId(counselorId);
            rule.setRuleName(level);
            rule.setRuleCode(categoryCode + "_" + level.toLowerCase());
            rule.setCategory(categoryCode);
            rule.setWeight(20);
            rule.setMinScore(minScore);
            rule.setMaxScore(maxScore);
            rule.setStatus(1);
            rule.setSortOrder(i);
            rule.setCreateTime(now);
            rule.setUpdateTime(now);
            rule.setDeleted(0);

            String description = generateDescription(categoryName, level, minScore, maxScore);
            rule.setDescription(description);

            rules.add(rule);
        }

        int result = counselorScoringRuleMapper.batchInsert(rules);

        redistributeWeights(counselorId);

        log.info("批量创建辅导员评分规则成功，创建数量: {}", result);
        return result;
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
    
    @Override
    public List<CounselorCategoryWeight> getCategoryWeights(Long counselorId) {
        return counselorCategoryWeightMapper.findByCounselorId(counselorId);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int saveCategoryWeights(Long counselorId, List<CounselorCategoryWeight> weights) {
        if (weights == null || weights.isEmpty()) {
            return 0;
        }
        
        Date now = new Date();
        for (CounselorCategoryWeight weight : weights) {
            weight.setCounselorId(counselorId);
            weight.setCreateTime(now);
            weight.setUpdateTime(now);
            weight.setDeleted(0);
            if (weight.getStatus() == null) {
                weight.setStatus(1);
            }
        }
        
        return counselorCategoryWeightMapper.batchInsert(weights);
    }
    
    @Override
    public int deleteCategoryWeightByCategoryCode(Long counselorId, String categoryCode) {
        return counselorCategoryWeightMapper.deleteByCounselorIdAndCategoryCode(counselorId, categoryCode);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int redistributeWeights(Long counselorId) {
        log.info("自动分配辅导员分类权重，辅导员ID: {}", counselorId);
        
        List<String> categories = counselorScoringRuleMapper.findCategoriesByCounselorId(counselorId);
        
        if (categories == null || categories.isEmpty()) {
            return 0;
        }
        
        int categoryCount = categories.size();
        int weightPerCategory = Math.floorDiv(100, categoryCount);
        int remainder = 100 % categoryCount;
        
        log.info("每个类别的权重: {}, 余数: {}", weightPerCategory, remainder);
        
        counselorCategoryWeightMapper.deleteByCounselorId(counselorId);
        
        Date now = new Date();
        List<CounselorCategoryWeight> weights = new ArrayList<>();
        
        for (int i = 0; i < categories.size(); i++) {
            String categoryCode = categories.get(i);
            String categoryName = getCategoryDisplayName(categoryCode);
            int weight = weightPerCategory + (i < remainder ? 1 : 0);
            
            CounselorCategoryWeight categoryWeight = new CounselorCategoryWeight();
            categoryWeight.setCounselorId(counselorId);
            categoryWeight.setCategoryCode(categoryCode);
            categoryWeight.setCategoryName(categoryName);
            categoryWeight.setWeight(weight);
            categoryWeight.setStatus(1);
            categoryWeight.setCreateTime(now);
            categoryWeight.setUpdateTime(now);
            categoryWeight.setDeleted(0);
            
            weights.add(categoryWeight);
        }
        
        return counselorCategoryWeightMapper.batchInsert(weights);
    }
    
    @Override
    public Integer getTotalWeight(Long counselorId) {
        return counselorCategoryWeightMapper.getTotalWeightByCounselorId(counselorId);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ensureDefaultRulesExist(Long counselorId) {
        // 规则和 AI 设置独立初始化，互不依赖
        List<CounselorScoringRule> existing = getScoringRules(counselorId);
        if (existing == null || existing.isEmpty()) {
            log.info("辅导员 {} 无评分规则，自动初始化3个默认维度", counselorId);
            initDefaultRulesAndWeights(counselorId);
        }
        // AI 设置独立检查，无论规则是否存在
        if (counselorAISettingsMapper.findByCounselorId(counselorId) == null) {
            CounselorAISettings settings = new CounselorAISettings();
            settings.setCounselorId(counselorId);
            settings.setEnableAiScoring(1);
            settings.setCreateTime(new Date());
            settings.setUpdateTime(new Date());
            counselorAISettingsMapper.insert(settings);
            log.info("辅导员 {} AI评分已默认开启", counselorId);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    private void initDefaultRulesAndWeights(Long counselorId) {
        String[][] dims = {{"实习态度", "40"}, {"专业能力", "35"}, {"学习成长", "25"}};
        String[][] levels = {
            {"优秀","90","100","表现卓越，远超预期，可作为榜样"},
            {"良好","80","89","表现良好，达到要求并有一定亮点"},
            {"中等","70","79","表现一般，基本完成要求"},
            {"及格","60","69","勉强达标，存在明显不足"},
            {"不及格","0","59","未达标，需要大幅改进"}
        };
        // 维度描述
        java.util.Map<String, String> dimDescriptions = new java.util.HashMap<>();
        dimDescriptions.put("实习态度", "评估学生在实习过程中的工作态度、积极性和责任心");
        dimDescriptions.put("专业能力", "评估学生在实习中展现的专业技能和知识应用能力");
        dimDescriptions.put("学习成长", "评估学生在实习期间的学习进步和个人成长");
        List<CounselorScoringRule> rules = new ArrayList<>();
        List<CounselorCategoryWeight> weights = new ArrayList<>();
        Date now = new Date();
        for (int di = 0; di < dims.length; di++) {
            String[] dim = dims[di];
            for (int li = 0; li < levels.length; li++) {
                String[] lv = levels[li];
                CounselorScoringRule r = new CounselorScoringRule();
                r.setCounselorId(counselorId); r.setRuleName(lv[0]);
                r.setRuleCode(dim[0] + "_" + lv[0]); r.setCategory(dim[0]);
                r.setMinScore(Integer.parseInt(lv[1])); r.setMaxScore(Integer.parseInt(lv[2]));
                r.setDescription(dim[0] + lv[0] + "：" + lv[3]);
                r.setSortOrder(li); r.setStatus(1); r.setDeleted(0);
                r.setCreateTime(now); r.setUpdateTime(now);
                rules.add(r);
            }
            CounselorCategoryWeight w = new CounselorCategoryWeight();
            w.setCounselorId(counselorId); w.setCategoryCode(dim[0]); w.setCategoryName(dim[0]);
            w.setWeight(Integer.parseInt(dim[1])); w.setStatus(1); w.setDeleted(0);
            w.setCreateTime(now); w.setUpdateTime(now);
            weights.add(w);
        }
        counselorScoringRuleMapper.batchInsert(rules);
        counselorCategoryWeightMapper.batchInsert(weights);
    }

    private String getCategoryDisplayName(String categoryCode) {
        java.util.Map<String, String> categoryMap = new java.util.HashMap<>();
        categoryMap.put("internship_performance", "实习表现");
        categoryMap.put("academic_achievement", "学术成果");
        categoryMap.put("team_collaboration", "团队协作");
        categoryMap.put("innovation_ability", "创新能力");
        categoryMap.put("teamwork", "团队协作能力");
        categoryMap.put("problemSolving", "问题解决能力");
        categoryMap.put("problemsolving", "问题解决能力");
        categoryMap.put("communication", "沟通表达能力");
        categoryMap.put("learningAbility", "学习能力");
        categoryMap.put("learningability", "学习能力");
        categoryMap.put("professionalism", "职业素养");
        categoryMap.put("attitude", "实习态度");
        categoryMap.put("report", "心得报告");
        categoryMap.put("实习态度", "实习态度");
        categoryMap.put("工作能力", "工作能力");
        categoryMap.put("团队协作", "团队协作");
        categoryMap.put("沟通能力", "沟通能力");
        categoryMap.put("学习能力", "学习能力");
        categoryMap.put("创新思维", "创新思维");
        categoryMap.put("责任心", "责任心");
        categoryMap.put("专业技能", "专业技能");

        // 如果在硬编码映射中找不到，调用AI翻译
        if (!categoryMap.containsKey(categoryCode)) {
            return translateToChinese(categoryCode);
        }

        return categoryMap.get(categoryCode);
    }

    /**
     * 调用AI将英文类别名称翻译成中文
     */
    private String translateToChinese(String categoryCode) {
        try {
            String prompt = String.format("请将以下英文类别名称翻译成中文（只返回翻译结果，不要其他内容）：\n%s", categoryCode);
            String result = enhancedDeepSeekService.chatWithResources(prompt, false);
            if (result != null && !result.trim().isEmpty()) {
                return result.trim();
            }
        } catch (Exception e) {
            log.warn("AI翻译类别名称失败: {}", e.getMessage());
        }
        // 翻译失败时返回原始code
        return categoryCode;
    }
}
