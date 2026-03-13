package com.gdmu.entity;

import lombok.Data;
import java.util.Date;

@Data
public class ScoringRule {
    
    private Long id;
    
    private Date createTime;
    
    private Date updateTime;
    
    private String creator;
    
    private String updater;
    
    private Integer deleted;
    
    private String ruleName;
    
    private String ruleType;
    
    private String category;
    
    private Integer minScore;
    
    private Integer maxScore;
    
    private String description;
    
    private String evaluationCriteria;
    
    private Integer weight;
    
    private Integer status;
    
    private String applicableScenarios;
}
