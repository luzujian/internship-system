package com.gdmu.enums;

import lombok.Getter;

/**
 * AI 评分等级枚举
 * 用于规范 AI 智能评分的等级判定
 */
@Getter
public enum AiGradeEnum {

    EXCELLENT(90, 100, "优秀", "内容详实，反思深刻，充分体现了实习收获和专业成长"),
    GOOD(80, 89, "良好", "内容完整，有一定反思，较好体现了实习收获"),
    AVERAGE(70, 79, "中等", "内容基本完整，反思一般，基本体现实习收获"),
    PASS(60, 69, "及格", "内容较为简单，反思较少，基本达到要求"),
    FAIL(0, 59, "不及格", "内容空洞，缺乏反思，未达到实习总结要求");

    private final Integer minScore;
    private final Integer maxScore;
    private final String name;
    private final String description;

    AiGradeEnum(Integer minScore, Integer maxScore, String name, String description) {
        this.minScore = minScore;
        this.maxScore = maxScore;
        this.name = name;
        this.description = description;
    }

    /**
     * 根据分数获取等级
     */
    public static AiGradeEnum of(Double score) {
        if (score == null || score < 0 || score > 100) {
            throw new IllegalArgumentException("无效的分数：" + score);
        }

        int intScore = score.intValue();
        for (AiGradeEnum grade : values()) {
            if (intScore >= grade.minScore && intScore <= grade.maxScore) {
                return grade;
            }
        }
        return FAIL;
    }

    /**
     * 根据分数获取等级名称
     */
    public static String getNameByScore(Double score) {
        return of(score).getName();
    }
}
