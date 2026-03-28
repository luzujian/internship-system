package com.gdmu.service;

import com.gdmu.entity.ScoringRule;
import java.util.List;

public interface ScoringRuleService {
    
    int insert(ScoringRule scoringRule);
    
    int batchInsert(List<ScoringRule> scoringRules);
    
    List<ScoringRule> findAll();
    
    ScoringRule findById(Long id);
    
    int update(ScoringRule scoringRule);
    
    int deleteById(Long id);
    
    int deleteByCategory(String category);
    
    List<ScoringRule> findByCategory(String category);
    
    List<ScoringRule> findByRuleType(String ruleType);
    
    List<ScoringRule> findByStatus(Integer status);
    
    List<ScoringRule> findByScenario(String scenario);

    int batchCreateWithAIDescription(String categoryName);

    int redistributeWeights();
}
