package com.internship.service;

import java.util.Set;

/** One durable business outcome that may result in a student email. */
public record JobApplicationResultNotification(
        Long applicationId,
        Long studentId,
        Long companyId,
        String companyName,
        Long positionId,
        String positionName,
        String status,
        boolean directHire,
        String rejectReason) {

    public JobApplicationResultNotification(
            Long applicationId,
            Long studentId,
            Long companyId,
            String companyName,
            Long positionId,
            String positionName,
            String status,
            boolean directHire) {
        this(applicationId, studentId, companyId, companyName,
                positionId, positionName, status, directHire, null);
    }

    private static final Set<String> EMAIL_STATUSES = Set.of(
            "approved", "rejected", "interview_passed", "interview_failed", "hired");

    public boolean supportsEmail() {
        return applicationId != null
                && studentId != null
                && status != null
                && EMAIL_STATUSES.contains(status);
    }

    public String todoId() {
        return "job-application-result:" + applicationId + ":" + status
                + (directHire ? ":direct-hire" : "");
    }

    public String deliveryId() {
        return "job-application-result-" + applicationId + "-" + status
                + (directHire ? "-direct-hire" : "");
    }
}
