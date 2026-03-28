package com.gdmu.entity;

import lombok.Data;
import java.util.Date;

@Data
public class InternshipEvaluationDetail {
    private Long id;
    private Long evaluationId;
    private Long studentId;
    private String categoryCode;
    private String categoryName;
    private Integer score;
    private Integer weight;
    private Date createTime;
    private Date updateTime;
    
    private InternshipEvaluation evaluation;
    private StudentUser student;
}
