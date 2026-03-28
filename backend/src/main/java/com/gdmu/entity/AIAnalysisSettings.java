package com.gdmu.entity;

import lombok.Data;

import java.util.Date;

@Data
public class AIAnalysisSettings {
    
    private Long id;
    
    private Date createTime;
    
    private Date updateTime;
    
    private String creator;
    
    private String updater;
    
    private Long createAdminId;
    
    private Long updateAdminId;
    
    private Integer deleted;
    
    private Integer enableKeywordLibrary;
    
    private Integer enableScoringRules;
}
