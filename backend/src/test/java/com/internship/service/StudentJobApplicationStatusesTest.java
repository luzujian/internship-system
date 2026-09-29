package com.internship.service;

import com.internship.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StudentJobApplicationStatusesTest {

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
