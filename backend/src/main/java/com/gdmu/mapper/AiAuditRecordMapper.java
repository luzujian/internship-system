package com.gdmu.mapper;

import com.gdmu.entity.AiAuditRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AiAuditRecordMapper {

    int insert(AiAuditRecord record);

    int update(AiAuditRecord record);

    AiAuditRecord findById(Long id);

    List<AiAuditRecord> findByTargetIdAndType(@Param("targetId") Long targetId, @Param("targetType") String targetType);

    List<AiAuditRecord> findByAuditType(@Param("auditType") String auditType);

    List<AiAuditRecord> findByConditions(Map<String, Object> params);

    int countByConditions(Map<String, Object> params);

    Map<String, Object> getAuditStatistics(@Param("auditType") String auditType, @Param("startDate") String startDate, @Param("endDate") String endDate);

    int deleteById(Long id);

    int deleteByAuditType(@Param("auditType") String auditType);

    int deleteAll();
}
