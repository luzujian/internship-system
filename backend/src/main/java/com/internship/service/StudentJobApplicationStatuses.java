package com.internship.service;

import com.internship.exception.BusinessException;

import java.util.Set;

/** Shared status classification for duplicate checks and compatibility synchronization. */
public final class StudentJobApplicationStatuses {

    private static final Set<String> ACTIVE = Set.of(
            "pending", "approved", "interview", "pending_interview", "interview_passed");
    private static final Set<String> RETRYABLE_TERMINAL = Set.of(
            "rejected", "interview_failed", "withdrawn", "cancelled", "canceled");

    private StudentJobApplicationStatuses() {
    }

    public static boolean isActive(String status) {
        return status != null && ACTIVE.contains(status);
    }

    public static boolean canRetry(String status) {
        return status != null && RETRYABLE_TERMINAL.contains(status);
    }

    public static boolean blocksRetry(String status) {
        return !canRetry(status);
    }

    public static String normalizeRejectReason(String status, String rejectReason) {
        if (!"rejected".equals(status)) {
            return null;
        }
        String normalized = rejectReason == null ? "" : rejectReason.trim();
        if (normalized.isEmpty()) {
            throw new BusinessException("拒绝学生申请时必须填写拒绝理由");
        }
        if (normalized.length() > 500) {
            throw new BusinessException("拒绝理由不能超过500个字");
        }
        return normalized;
    }
}
