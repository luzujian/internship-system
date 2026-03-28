package com.gdmu.mapper;

import com.gdmu.entity.ScoringRule;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ScoringRuleMapper {
    
    @Insert("INSERT INTO scoring_rule (rule_name, rule_code, rule_type, category, min_score, max_score, description, evaluation_criteria, weight, sort_order, status, applicable_scenarios, creator) " +
            "VALUES (#{ruleName}, #{ruleCode}, #{ruleType}, #{category}, #{minScore}, #{maxScore}, #{description}, #{evaluationCriteria}, #{weight}, #{sortOrder}, #{status}, #{applicableScenarios}, #{creator})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ScoringRule scoringRule);
    
    @Select("SELECT * FROM scoring_rule WHERE deleted = 0 ORDER BY create_time DESC")
    List<ScoringRule> findAll();
    
    @Select("SELECT * FROM scoring_rule WHERE id = #{id} AND deleted = 0")
    ScoringRule findById(Long id);
    
    @Update("UPDATE scoring_rule SET rule_name = #{ruleName}, rule_type = #{ruleType}, category = #{category}, " +
            "min_score = #{minScore}, max_score = #{maxScore}, description = #{description}, evaluation_criteria = #{evaluationCriteria}, " +
            "weight = #{weight}, status = #{status}, applicable_scenarios = #{applicableScenarios}, updater = #{updater}, update_time = NOW() " +
            "WHERE id = #{id}")
    int update(ScoringRule scoringRule);
    
    @Update("UPDATE scoring_rule SET deleted = 1, updater = #{updater}, update_time = NOW() WHERE id = #{id}")
    int deleteById(Long id);
    
    @Delete("UPDATE scoring_rule SET deleted = 1, update_time = NOW() WHERE category = #{category} AND deleted = 0")
    int deleteByCategory(@Param("category") String category);
    
    @Delete("UPDATE scoring_rule SET deleted = 1, update_time = NOW() WHERE category IS NULL AND deleted = 0")
    int deleteByCategoryIsNull();
    
    @Select("SELECT * FROM scoring_rule WHERE deleted = 0 AND category = #{category} ORDER BY create_time DESC")
    List<ScoringRule> findByCategory(String category);
    
    @Select("SELECT * FROM scoring_rule WHERE deleted = 0 AND rule_type = #{ruleType} ORDER BY create_time DESC")
    List<ScoringRule> findByRuleType(String ruleType);
    
    @Select("SELECT * FROM scoring_rule WHERE deleted = 0 AND status = #{status} ORDER BY create_time DESC")
    List<ScoringRule> findByStatus(Integer status);
    
    @Select("SELECT * FROM scoring_rule WHERE deleted = 0 AND applicable_scenarios LIKE CONCAT('%', #{scenario}, '%') ORDER BY create_time DESC")
    List<ScoringRule> findByScenario(String scenario);

    @Select("SELECT DISTINCT category FROM scoring_rule WHERE deleted = 0 AND category IS NOT NULL AND category != ''")
    List<String> findCategories();

    @Insert("<script>" +
            "INSERT INTO scoring_rule (rule_name, rule_code, rule_type, category, min_score, max_score, description, evaluation_criteria, weight, sort_order, status, applicable_scenarios, creator, create_time) " +
            "VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.ruleName}, #{item.ruleCode}, #{item.ruleType}, #{item.category}, #{item.minScore}, #{item.maxScore}, #{item.description}, #{item.evaluationCriteria}, #{item.weight}, #{item.sortOrder}, #{item.status}, #{item.applicableScenarios}, #{item.creator}, NOW())" +
            "</foreach>" +
            "</script>")
    int batchInsert(@Param("list") List<ScoringRule> scoringRules);
}
