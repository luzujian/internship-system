package com.gdmu.utils;

import com.gdmu.entity.Contribution;
import com.gdmu.entity.Submission;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import java.util.Map;

/**
 * 智能评分计算器
 * 根据贡献度、小组人数、教师评分等信息进行差异化评分
 */
@Slf4j
public class IntelligentScoreCalculator {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 计算每个学生的最终成绩
     * @param submission 作业提交信息
     * @param contributions 贡献度评分列表
     * @return Map<学生ID, 最终成绩>
     */
    public static Map<Long, Double> calculateFinalScores(Submission submission, List<Contribution> contributions) {
        try {
            Double teacherScore = submission.getTeacherScore();
            if (teacherScore == null) {
                throw new IllegalArgumentException("教师评分不能为空");
            }
            
            Integer groupMemberCount = submission.getGroupMemberCount();
            if (groupMemberCount == null || groupMemberCount <= 0) {
                throw new IllegalArgumentException("小组人数必须大于0");
            }
            
            // 解析贡献度评分JSON
            Map<Long, Double> contributionScores = parseContributionScores(submission.getContributionScores());
            
            // 验证贡献度评分数据完整性
            validateContributionData(contributionScores, groupMemberCount);
            
            // 计算每个学生的最终成绩
            return calculateIndividualScores(teacherScore, contributionScores, groupMemberCount);
            
        } catch (Exception e) {
            log.error("计算最终成绩失败: {}", e.getMessage(), e);
            throw new RuntimeException("成绩计算失败: " + e.getMessage());
        }
    }
    
    /**
     * 解析贡献度评分JSON
     */
    private static Map<Long, Double> parseContributionScores(String contributionScoresJson) {
        try {
            if (contributionScoresJson == null || contributionScoresJson.trim().isEmpty()) {
                throw new IllegalArgumentException("贡献度评分数据不能为空");
            }
            return objectMapper.readValue(contributionScoresJson, new TypeReference<Map<Long, Double>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("贡献度评分数据格式错误: " + e.getMessage());
        }
    }
    
    /**
     * 验证贡献度数据完整性
     */
    private static void validateContributionData(Map<Long, Double> contributionScores, Integer groupMemberCount) {
        if (contributionScores.size() != groupMemberCount) {
            throw new IllegalArgumentException("贡献度评分数量与小组人数不匹配");
        }
        
        // 验证每个贡献度分数在0-100之间
        for (Map.Entry<Long, Double> entry : contributionScores.entrySet()) {
            Double score = entry.getValue();
            if (score == null || score < 0 || score > 100) {
                throw new IllegalArgumentException("贡献度评分必须在0-100之间，学生ID: " + entry.getKey());
            }
        }
        
        // 验证贡献度总和是否为100
        double totalContribution = contributionScores.values().stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(totalContribution - 100.0) > 0.01) {
            throw new IllegalArgumentException("贡献度评分总和必须为100，当前总和: " + totalContribution);
        }
    }
    
    /**
     * 计算每个学生的最终成绩
     */
    private static Map<Long, Double> calculateIndividualScores(Double teacherScore, 
                                                              Map<Long, Double> contributionScores,
                                                              Integer groupMemberCount) {
        Map<Long, Double> finalScores = new java.util.HashMap<>();
        
        // 计算平均贡献度（理想情况下应该是100/小组人数）
        double averageContribution = 100.0 / groupMemberCount;
        
        for (Map.Entry<Long, Double> entry : contributionScores.entrySet()) {
            Long studentId = entry.getKey();
            Double contribution = entry.getValue();
            
            // 计算贡献度调整系数
            double adjustmentFactor = calculateAdjustmentFactor(contribution, averageContribution);
            
            // 计算最终成绩：教师评分 × 贡献度调整系数
            double finalScore = teacherScore * adjustmentFactor;
            
            // 确保成绩在合理范围内（0-100）
            finalScore = Math.max(0, Math.min(100, finalScore));
            
            finalScores.put(studentId, Math.round(finalScore * 100.0) / 100.0); // 保留两位小数
        }
        
        return finalScores;
    }
    
    /**
     * 计算贡献度调整系数
     * 使用非线性函数，避免极端情况下的分数波动过大
     */
    private static double calculateAdjustmentFactor(double actualContribution, double averageContribution) {
        // 计算相对贡献度比例
        double ratio = actualContribution / averageContribution;
        
        // 使用sigmoid函数的变体来平滑调整
        // 当ratio=1时，调整系数为1；当ratio>1时，调整系数>1；当ratio<1时，调整系数<1
        // 使用平滑函数避免极端值的影响
        return 1.0 + 0.3 * Math.tanh((ratio - 1.0) * 2.0);
    }
    
    /**
     * 验证贡献度评分是否合理（用于提交前的验证）
     */
    public static boolean validateContributionScores(Map<Long, Double> contributionScores, Integer groupMemberCount) {
        try {
            validateContributionData(contributionScores, groupMemberCount);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 获取评分算法说明
     */
    public static String getAlgorithmDescription() {
        return "智能评分算法说明：\n" +
               "1. 基于教师评分和成员贡献度进行差异化评分\n" +
               "2. 贡献度总和必须为100%\n" +
               "3. 使用平滑函数避免极端分数波动\n" +
               "4. 最终成绩 = 教师评分 × 贡献度调整系数\n" +
               "5. 调整系数基于实际贡献度与平均贡献度的比例计算";
    }
}