package com.gdmu.utils;

import com.gdmu.entity.Contribution;
import com.gdmu.entity.FinalScore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 智能评分算法工具类
 * 实现基于贡献度的智能评分算法
 */
@Slf4j
@Component
public class ScoreCalculationAlgorithm {

    /**
     * 计算最终成绩
     * 基于小组得分和成员贡献度计算个人最终成绩
     * 
     * @param groupScore 小组得分
     * @param contributions 成员贡献度列表
     * @return 个人最终成绩列表
     */
    public Map<Long, Double> calculateFinalScores(Double groupScore, List<Contribution> contributions) {
        if (groupScore == null || contributions == null || contributions.isEmpty()) {
            log.warn("计算最终成绩参数不完整: groupScore={}, contributions={}", groupScore, contributions);
            return null;
        }

        // 计算总贡献度
        double totalContribution = contributions.stream()
                .mapToDouble(Contribution::getScore)
                .sum();

        if (totalContribution <= 0) {
            log.warn("总贡献度为零或负数: {}", totalContribution);
            return null;
        }

        // 计算每个成员的个人成绩
        return contributions.stream()
                .collect(Collectors.toMap(
                        Contribution::getStudentId,
                        contribution -> {
                            double contributionRatio = contribution.getScore() / totalContribution;
                            return groupScore * contributionRatio;
                        }
                ));
    }

    /**
     * 计算平均贡献度
     * 用于处理多个评分者对同一成员的评分
     * 
     * @param contributions 贡献度列表
     * @return 平均贡献度映射
     */
    public Map<Long, Double> calculateAverageContributions(List<Contribution> contributions) {
        if (contributions == null || contributions.isEmpty()) {
            return null;
        }

        // 按学生ID分组，计算平均分
        return contributions.stream()
                .collect(Collectors.groupingBy(
                        Contribution::getStudentId,
                        Collectors.averagingDouble(Contribution::getScore)
                ));
    }

    /**
     * 验证贡献度评分
     * 检查评分是否合理，避免极端值
     * 
     * @param contributions 贡献度列表
     * @return 是否有效
     */
    public boolean validateContributions(List<Contribution> contributions) {
        if (contributions == null || contributions.isEmpty()) {
            return false;
        }

        // 检查评分范围
        for (Contribution contribution : contributions) {
            if (contribution.getScore() < 0 || contribution.getScore() > 100) {
                log.warn("贡献度评分超出范围: {}", contribution.getScore());
                return false;
            }
        }

        // 检查评分者是否给自己评分
        for (Contribution contribution : contributions) {
            if (contribution.getStudentId().equals(contribution.getRatedBy())) {
                log.warn("评分者不能给自己评分: studentId={}, ratedBy={}", 
                        contribution.getStudentId(), contribution.getRatedBy());
                return false;
            }
        }

        return true;
    }

    /**
     * 计算成绩等级
     * 根据分数计算等级（优秀、良好、中等、及格、不及格）
     * 
     * @param score 分数
     * @return 等级
     */
    public String calculateGradeLevel(Double score) {
        if (score == null) {
            return "未知";
        }

        if (score >= 90) {
            return "优秀";
        } else if (score >= 80) {
            return "良好";
        } else if (score >= 70) {
            return "中等";
        } else if (score >= 60) {
            return "及格";
        } else {
            return "不及格";
        }
    }

    /**
     * 计算成绩分布统计
     * 
     * @param finalScores 最终成绩列表
     * @return 统计信息
     */
    public Map<String, Object> calculateScoreStatistics(List<FinalScore> finalScores) {
        if (finalScores == null || finalScores.isEmpty()) {
            return null;
        }

        Map<String, Object> statistics = new java.util.HashMap<>();

        // 计算平均分
        double average = finalScores.stream()
                .mapToDouble(FinalScore::getScore)
                .average()
                .orElse(0.0);
        statistics.put("average", average);

        // 计算最高分
        double max = finalScores.stream()
                .mapToDouble(FinalScore::getScore)
                .max()
                .orElse(0.0);
        statistics.put("max", max);

        // 计算最低分
        double min = finalScores.stream()
                .mapToDouble(FinalScore::getScore)
                .min()
                .orElse(0.0);
        statistics.put("min", min);

        // 计算及格率
        long passCount = finalScores.stream()
                .filter(score -> score.getScore() >= 60)
                .count();
        double passRate = (double) passCount / finalScores.size() * 100;
        statistics.put("passRate", passRate);

        // 计算优秀率（90分以上）
        long excellentCount = finalScores.stream()
                .filter(score -> score.getScore() >= 90)
                .count();
        double excellentRate = (double) excellentCount / finalScores.size() * 100;
        statistics.put("excellentRate", excellentRate);

        // 计算成绩分布
        Map<String, Long> distribution = finalScores.stream()
                .collect(Collectors.groupingBy(
                        score -> calculateGradeLevel(score.getScore()),
                        Collectors.counting()
                ));
        statistics.put("distribution", distribution);

        return statistics;
    }

    /**
     * 计算贡献度公平性指标
     * 评估评分是否公平（标准差越小越公平）
     * 
     * @param contributions 贡献度列表
     * @return 公平性指标
     */
    public double calculateFairnessIndex(List<Contribution> contributions) {
        if (contributions == null || contributions.isEmpty()) {
            return 0.0;
        }

        // 计算平均分
        double average = contributions.stream()
                .mapToDouble(Contribution::getScore)
                .average()
                .orElse(0.0);

        // 计算标准差
        double variance = contributions.stream()
                .mapToDouble(contribution -> Math.pow(contribution.getScore() - average, 2))
                .average()
                .orElse(0.0);

        double standardDeviation = Math.sqrt(variance);

        // 标准差越小越公平，这里返回公平性指标（1 - 标准化标准差）
        return 1 - (standardDeviation / 100); // 假设满分100
    }
}