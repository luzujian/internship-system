package com.gdmu.entity;

import lombok.Data;
import java.util.Date;

@Data
public class CounselorScoringRule {
    private Long id;
    private Long counselorId;
    private String ruleName;
    private String ruleCode;
    private String category;
    private Integer weight;
    private Integer minScore;
    private Integer maxScore;
    private String description;
    private String evaluationCriteria;
    private Integer sortOrder;
    private Integer status;
    private Date createTime;
    private Date updateTime;
    private Integer deleted;
    
    private TeacherUser counselor;
}
