package com.gdmu.entity;

import lombok.Data;

import java.util.Date;

@Data
public class InternshipTimeSettings {
    
    private Long id;
    
    private Date createTime;
    
    private Date updateTime;
    
    private String creator;
    
    private String updater;
    
    private Long createAdminId;
    
    private Long updateAdminId;
    
    private Integer deleted;
    
    private String applicationStartDate;
    
    private String applicationEndDate;
    
    private String companyConfirmationDeadline;
    
    private String delayApplicationDeadline;
    
    private Integer reportCycle;

    private String startDate;
    
    private String endDate;
    
    private String reportDeadline;
    
    private String evaluationDeadline;

    private Integer approvalTimeLimit; // 审核时限（天）
}
