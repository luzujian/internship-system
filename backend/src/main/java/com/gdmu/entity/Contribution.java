package com.gdmu.entity;

import lombok.Data;
import java.util.Date;

/**
 * 小组成员贡献度实体类
 */
@Data
public class Contribution {
    private Long id;
    private Long submissionId;
    private Long studentId;
    private Double score;

    // 新增字段：评分人ID（可能是小组组长或其他成员）
    private Long ratedBy;
    
    // 新增字段：评分理由/说明
    private String reason;
    
    // 新增字段：评分类型（如：SELF_EVALUATION自评、PEER_EVALUATION互评、LEADER_EVALUATION组长评分）
    private String type;
    
    // 新增字段：是否有效（可能用于需要审核的评分系统）
    private Boolean isValid = true;
    
    private Date createTime;
    private Date updateTime;
}