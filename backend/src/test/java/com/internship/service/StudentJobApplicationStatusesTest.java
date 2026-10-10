package com.internship.service;

import com.internship.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class StudentJobApplicationStatusesTest {

    @ParameterizedTest
    @CsvSource({"pending,approved", "pending,rejected", "approved,interview_passed",
            "approved,interview_failed", "interview,interview_passed", "pending_interview,interview_failed"})
    void acceptsSupportedReviewTransitions(String current, String next) {
        assertDoesNotThrow(() -> StudentJobApplicationStatuses.requireReviewTransition(current, next));
    }

    @ParameterizedTest
    @CsvSource({"pending,unknown", "pending,", "pending,interview_passed", "pending,interview_failed",
            "approved,pending", "approved,approved", "interview_passed,rejected",
            "interview_passed,approved", "interview_passed,interview_failed", "rejected,approved"})
    void rejectsUnknownSkippedOrReversedTransitions(String current, String next) {
        assertThrows(BusinessException.class,
                () -> StudentJobApplicationStatuses.requireReviewTransition(current, next));
    }

    @Test
    void rejectionRequiresNonBlankReason() {
        BusinessException error = assertThrows(
                BusinessException.class,
                () -> StudentJobApplicationStatuses.normalizeRejectReason("rejected", "   "));
        assertEquals("拒绝学生申请时必须填写拒绝理由", error.getMessage());
    }

    @Test
    void rejectionTrimsAndLimitsReason() {
        assertEquals("专业不符",
                StudentJobApplicationStatuses.normalizeRejectReason("rejected", "  专业不符  "));
        assertThrows(BusinessException.class,
                () -> StudentJobApplicationStatuses.normalizeRejectReason("rejected", "x".repeat(501)));
    }

    @Test
    void nonRejectionStatusesClearReason() {
        assertNull(StudentJobApplicationStatuses.normalizeRejectReason("approved", "不应该保留"));
        assertNull(StudentJobApplicationStatuses.normalizeRejectReason("interview_failed", "不应该保留"));
    }
}
