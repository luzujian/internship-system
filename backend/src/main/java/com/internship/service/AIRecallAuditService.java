package com.internship.service;

import java.util.Map;

public interface AIRecallAuditService {

    Map<String, Object> autoAuditStudentRecall(Long studentInternshipStatusId, String recallReason);

    boolean isAIStudentRecallAuditEnabled();

    void recordAuditResult(Map<String, Object> auditResult, Long targetId, String targetType, String modelUsed, long duration);

    Map<String, Object> getAuditStatistics(String auditType, String startDate, String endDate);
}
