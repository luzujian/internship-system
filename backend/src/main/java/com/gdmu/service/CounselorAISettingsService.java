package com.gdmu.service;

import com.gdmu.entity.CounselorAISettings;
import com.gdmu.entity.CounselorScoringRule;
import com.gdmu.entity.CounselorCategoryWeight;
import java.util.List;

public interface CounselorAISettingsService {
    
    int saveOrUpdate(CounselorAISettings settings);
    
    CounselorAISettings findByCounselorId(Long counselorId);
    
    int update(CounselorAISettings settings);
    
    boolean isAiScoringEnabled(Long counselorId);
    
    List<CounselorScoringRule> getScoringRules(Long counselorId);
    
    List<CounselorScoringRule> getScoringRulesByCategory(Long counselorId, String category);
    
    List<String> getCategories(Long counselorId);
    
    int saveScoringRules(Long counselorId, List<CounselorScoringRule> rules);
    
    int updateScoringRule(CounselorScoringRule rule);
    
    int deleteScoringRule(Long ruleId);
    
    int deleteScoringRulesByCategory(Long counselorId, String category);
    
    int batchCreateWithAIDescription(Long counselorId, String categoryName);
    
    List<CounselorCategoryWeight> getCategoryWeights(Long counselorId);
    
    int saveCategoryWeights(Long counselorId, List<CounselorCategoryWeight> weights);
    
    int deleteCategoryWeightByCategoryCode(Long counselorId, String categoryCode);
    
    int redistributeWeights(Long counselorId);
    
    Integer getTotalWeight(Long counselorId);
}
