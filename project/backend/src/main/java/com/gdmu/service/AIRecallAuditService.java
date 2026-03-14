package com.gdmu.service;

import java.util.Map;

public interface AIRecallAuditService {

    Map<String, Object> autoAuditRecall(Long companyId, String recallReason);

    Map<String, Object> autoAuditStudentRecall(Long studentInternshipStatusId, String recallReason);

    boolean isAIRecallAuditEnabled();

    boolean isAIStudentRecallAuditEnabled();

    void recordAuditResult(Map<String, Object> auditResult, Long targetId, String targetType, String modelUsed, long duration);

    Map<String, Object> getAuditStatistics(String auditType, String startDate, String endDate);
}
