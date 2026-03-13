package com.gdmu.mapper;

import com.gdmu.entity.BackupAuditLog;
import org.apache.ibatis.annotations.*;

@Mapper
public interface BackupAuditLogMapper {
    
    @Insert("INSERT INTO backup_audit_log (operation_type, operation_detail, operator, operator_ip, status, error_message, operation_time, duration) " +
            "VALUES (#{operationType}, #{operationDetail}, #{operator}, #{operatorIp}, #{status}, #{errorMessage}, #{operationTime}, #{duration})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(BackupAuditLog log);
    
    @Update("UPDATE backup_audit_log SET status = #{status}, error_message = #{errorMessage}, duration = #{duration} WHERE id = #{id}")
    void update(BackupAuditLog log);
}