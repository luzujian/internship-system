package com.gdmu.service.impl;

import com.gdmu.entity.CounselorScoringRule;
import com.gdmu.entity.StudentReflectionAIAnalysis;
import com.gdmu.entity.StudentReflectionEvaluation;
import com.gdmu.mapper.StudentReflectionEvaluationMapper;
import com.gdmu.service.CounselorAISettingsService;
import com.gdmu.service.StudentReflectionAIAnalysisService;
import com.gdmu.service.StudentReflectionEvaluationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class StudentReflectionEvaluationServiceImpl implements StudentReflectionEvaluationService {
    
    @Autowired
    private StudentReflectionEvaluationMapper evaluationMapper;
    
    @Autowired
    private CounselorAISettingsService counselorAISettingsService;
    
    @Autowired
    private StudentReflectionAIAnalysisService aiAnalysisService;
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insert(StudentReflectionEvaluation evaluation) {
        if (evaluation.getCreateTime() == null) {
            evaluation.setCreateTime(new Date());
        }
        if (evaluation.getUpdateTime() == null) {
            evaluation.setUpdateTime(new Date());
        }
        return evaluationMapper.insert(evaluation);
    }
    
    @Override
    public StudentReflectionEvaluation findById(Long id) {
        return evaluationMapper.findById(id);
    }
    
    @Override
    public StudentReflectionEvaluation findByReflectionId(Long reflectionId) {
        return evaluationMapper.findByReflectionId(reflectionId);
    }
    
    @Override
    public StudentReflectionEvaluation findByStudentId(Long studentId) {
        return evaluationMapper.findByStudentId(studentId);
    }
    
    @Override
    public List<StudentReflectionEvaluation> findByCounselorId(Long counselorId) {
        return evaluationMapper.findByCounselorId(counselorId);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int update(StudentReflectionEvaluation evaluation) {
        evaluation.setUpdateTime(new Date());
        return evaluationMapper.update(evaluation);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteById(Long id) {
        return evaluationMapper.deleteById(id);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByReflectionId(Long reflectionId) {
        return evaluationMapper.deleteByReflectionId(reflectionId);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int saveEvaluation(StudentReflectionEvaluation evaluation) {
        StudentReflectionEvaluation existing = evaluationMapper.findByReflectionId(evaluation.getReflectionId());
        
        Map<String, Object> calculated = calculateTotalScoreAndGrade(evaluation.getScoreDetails(), evaluation.getCounselorId());
        evaluation.setTotalScore((BigDecimal) calculated.get("totalScore"));
        evaluation.setGrade((String) calculated.get("grade"));
        
        if (evaluation.getAiAnalysisId() != null) {
            StudentReflectionAIAnalysis aiAnalysis = aiAnalysisService.findById(evaluation.getAiAnalysisId());
            if (aiAnalysis != null && aiAnalysis.getScoreDetails() != null) {
                boolean isModified = !aiAnalysis.getScoreDetails().equals(evaluation.getScoreDetails());
                evaluation.setIsAiScoreModified(isModified ? 1 : 0);
            }
        }
        
        evaluation.setEvaluateTime(new Date());
        evaluation.setUpdateTime(new Date());
        
        if (existing != null) {
            evaluation.setId(existing.getId());
            evaluation.setCreateTime(existing.getCreateTime());
            return evaluationMapper.update(evaluation);
        } else {
            evaluation.setCreateTime(new Date());
            return evaluationMapper.insert(evaluation);
        }
    }
    
    @Override
    public Map<String, Object> calculateTotalScoreAndGrade(Map<String, Object> scoreDetails, Long counselorId) {
        Map<String, Object> result = new HashMap<>();
        
        if (scoreDetails == null || scoreDetails.isEmpty()) {
            result.put("totalScore", BigDecimal.ZERO);
            result.put("grade", "未评级");
            return result;
        }
        
        List<CounselorScoringRule> rules = counselorAISettingsService.getScoringRules(counselorId);
        
        if (rules == null || rules.isEmpty()) {
            BigDecimal avgScore = calculateAverageScore(scoreDetails);
            result.put("totalScore", avgScore);
            result.put("grade", calculateGrade(avgScore));
            return result;
        }
        
        BigDecimal totalWeightedScore = BigDecimal.ZERO;
        int totalWeight = 0;
        
        for (CounselorScoringRule rule : rules) {
            if (rule.getStatus() != null && rule.getStatus() == 1) {
                String ruleCode = rule.getRuleCode();
                Object scoreObj = scoreDetails.get(ruleCode);
                
                if (scoreObj != null) {
                    BigDecimal score = new BigDecimal(scoreObj.toString());
                    int weight = rule.getWeight() != null ? rule.getWeight() : 1;
                    totalWeightedScore = totalWeightedScore.add(score.multiply(new BigDecimal(weight)));
                    totalWeight += weight;
                }
            }
        }
        
        BigDecimal totalScore;
        if (totalWeight == 0) {
            totalScore = calculateAverageScore(scoreDetails);
        } else {
            totalScore = totalWeightedScore.divide(new BigDecimal(totalWeight), 2, RoundingMode.HALF_UP);
        }
        
        result.put("totalScore", totalScore);
        result.put("grade", calculateGrade(totalScore));
        
        return result;
    }
    
    private BigDecimal calculateAverageScore(Map<String, Object> scoreDetails) {
        if (scoreDetails == null || scoreDetails.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        
        for (Object value : scoreDetails.values()) {
            if (value != null) {
                try {
                    total = total.add(new BigDecimal(value.toString()));
                    count++;
                } catch (NumberFormatException e) {
                    log.warn("无法解析分数: {}", value);
                }
            }
        }
        
        if (count == 0) {
            return BigDecimal.ZERO;
        }
        
        return total.divide(new BigDecimal(count), 2, RoundingMode.HALF_UP);
    }
    
    private String calculateGrade(BigDecimal score) {
        if (score == null) {
            return "未评级";
        }
        int scoreInt = score.intValue();
        if (scoreInt >= 90) {
            return "优秀";
        } else if (scoreInt >= 80) {
            return "良好";
        } else if (scoreInt >= 70) {
            return "中等";
        } else if (scoreInt >= 60) {
            return "及格";
        } else {
            return "不及格";
        }
    }
}
