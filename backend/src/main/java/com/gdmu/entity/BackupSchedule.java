package com.gdmu.entity;

import lombok.Data;
import java.util.Date;

@Data
public class BackupSchedule {
    private Long id;
    private Boolean enabled;
    private String frequency;
    private String backupTime;
    private Integer retentionDays;
    private String remark;
    private Date lastExecutionDate;
}