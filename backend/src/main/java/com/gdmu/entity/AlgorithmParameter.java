package com.gdmu.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 评分算法参数配置实体类
 */
@Data
public class AlgorithmParameter {
    
    private Long id;
    private Long courseId;
    private Long assignmentId;
    private BigDecimal difficultyFactor; // 作业难度系数
    private BigDecimal collaborationFactor; // 小组协作系数
    private BigDecimal minScore; // 最低保障分数
    private BigDecimal maxScore; // 最高分
    private Date createdAt;
    private Date updatedAt;
    private Long createdBy;
}