package com.gdmu.entity;

import lombok.Data;

import java.util.Date;

@Data
public class SystemSettings {
    
    private Long id;
    
    private Date createTime;
    
    private Date updateTime;
    
    private String creator;
    
    private String updater;
    
    private Integer deleted;
    
    private String systemName;
    
    private String systemDescription;
    
    private Integer pageSize;
    
    private String logo;
    
    private Integer systemStatus;
    
    private Integer minPasswordLength;
    
    private String passwordComplexity;
    
    private Integer passwordExpireDays;
    
    private Integer maxLoginAttempts;
    
    private Integer lockTime;
    
    private Integer sessionTimeout;
    
    private Integer enableTwoFactor;
    
    private java.util.Date dualSelectionStartDate;
    
    private java.util.Date dualSelectionEndDate;
    
    private java.util.Date internshipStartDate;
    
    private java.util.Date internshipEndDate;
    
    private Integer reportSubmissionCycle;
}
