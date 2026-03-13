package com.gdmu.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 差异化评分记录实体类
 */
@Data
public class IndividualScore {
    
    private Long id;
    private Long submissionId;
    private Long studentUserId;
    private BigDecimal teacherScore; // 教师给小组的评分
    private BigDecimal contributionRatio; // 个人贡献度百分比
    private BigDecimal individualFinalScore; // 个人最终得分
    private BigDecimal difficultyFactor;
    private BigDecimal collaborationFactor;
    private Date calculatedAt;
    private Long calculatedBy;
    private boolean isPublished;
    private Date publishTime;
}