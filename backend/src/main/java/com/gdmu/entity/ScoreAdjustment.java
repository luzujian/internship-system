package com.gdmu.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 教师调分记录实体类
 */
@Data
public class ScoreAdjustment {
    private Long id;
    private Long submissionId;
    private Long teacherId;
    private Double originalScore = 0.00; // 原始算法评分
    private Double adjustedScore = 0.00; // 调整后评分
    private String adjustmentReason; // 调分原因
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime adjustmentTime;
}