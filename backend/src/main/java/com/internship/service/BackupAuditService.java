package com.internship.service;

import com.internship.entity.BackupAuditLog;

public interface BackupAuditService {
    
    BackupAuditLog startAuditLog(String operationType, String operationDetail, String operator, String operatorIp);
    
    void completeAuditLog(Long logId, Integer status, String errorMessage, Long duration);
    
    void recordAuditLog(String operationType, String operationDetail, String operator, String operatorIp, 
                       Integer status, String errorMessage, Long duration);
}