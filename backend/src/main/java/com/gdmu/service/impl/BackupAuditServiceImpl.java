package com.gdmu.service.impl;

import com.gdmu.entity.BackupAuditLog;
import com.gdmu.mapper.BackupAuditLogMapper;
import com.gdmu.service.BackupAuditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;

@Slf4j
@Service
public class BackupAuditServiceImpl implements BackupAuditService {

    @Autowired
    private BackupAuditLogMapper backupAuditLogMapper;

    @Override
    public BackupAuditLog startAuditLog(String operationType, String operationDetail, String operator, String operatorIp) {
        BackupAuditLog auditLog = new BackupAuditLog();
        auditLog.setOperationType(operationType);
        auditLog.setOperationDetail(operationDetail);
        auditLog.setOperator(operator);
        auditLog.setOperatorIp(operatorIp);
        auditLog.setStatus(0);
        auditLog.setOperationTime(new Date());
        auditLog.setDuration(0L);
        
        backupAuditLogMapper.insert(auditLog);
        log.info("开始记录审计日志: 操作类型={}, 操作详情={}, 操作人={}", operationType, operationDetail, operator);
        
        return auditLog;
    }

    @Override
    public void completeAuditLog(Long logId, Integer status, String errorMessage, Long duration) {
        BackupAuditLog auditLog = new BackupAuditLog();
        auditLog.setId(logId);
        auditLog.setStatus(status);
        auditLog.setErrorMessage(errorMessage);
        auditLog.setDuration(duration);
        
        backupAuditLogMapper.update(auditLog);
        log.info("完成审计日志记录: 日志ID={}, 状态={}, 耗时={}ms", logId, status, duration);
    }

    @Override
    public void recordAuditLog(String operationType, String operationDetail, String operator, String operatorIp, 
                              Integer status, String errorMessage, Long duration) {
        BackupAuditLog auditLog = new BackupAuditLog();
        auditLog.setOperationType(operationType);
        auditLog.setOperationDetail(operationDetail);
        auditLog.setOperator(operator);
        auditLog.setOperatorIp(operatorIp);
        auditLog.setStatus(status);
        auditLog.setErrorMessage(errorMessage);
        auditLog.setOperationTime(new Date());
        auditLog.setDuration(duration);
        
        backupAuditLogMapper.insert(auditLog);
        log.info("记录审计日志: 操作类型={}, 操作详情={}, 操作人={}, 状态={}, 耗时={}ms", 
            operationType, operationDetail, operator, status, duration);
    }
}