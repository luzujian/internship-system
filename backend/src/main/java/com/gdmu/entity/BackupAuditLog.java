package com.gdmu.entity;

import lombok.Data;
import java.util.Date;

@Data
public class BackupAuditLog {
    private Long id;
    private String operationType;
    private String operationDetail;
    private String operator;
    private String operatorIp;
    private Integer status;
    private String errorMessage;
    private Date operationTime;
    private Long duration;
}