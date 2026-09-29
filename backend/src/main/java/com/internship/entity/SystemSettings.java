package com.internship.entity;

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

    /** 是否开启两个实习阶段（0-关闭，1-开启） */
    private Integer enableDualPhase;

    /** 系统邮件提醒总开关（0-关闭，1-开启）；null 视为开启（历史数据兼容） */
    private Integer emailNotifyEnabled;

    /** 启用邮件提醒的业务范围，逗号分隔的 EmailNotifyScope 代码；null/空视为全部启用 */
    private String emailNotifyScopes;
}
