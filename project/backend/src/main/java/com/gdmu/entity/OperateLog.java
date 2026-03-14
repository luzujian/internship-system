package com.gdmu.entity;

import lombok.Data;
import java.util.Date;

@Data
public class OperateLog {
    private Long id;
    
    private Long operateAdminId;
    
    private String operatorName;
    
    private String operatorUsername;
    
    private String operatorRole;
    
    private String ipAddress;
    
    private String operationType;
    
    private String module;
    
    private String description;
    
    private String operationResult;
    
    private Date operateTime;
}
