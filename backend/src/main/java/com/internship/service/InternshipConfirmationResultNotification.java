package com.internship.service;

/** One durable internship-confirmation outcome that may result in a student email. */
public record InternshipConfirmationResultNotification(
        Long recordId,
        Long studentId,
        Long companyId,
        String companyName,
        Long positionId,
        String positionName,
        int status,
        String rejectReason) {

    public static final int CONFIRMED = 1;
    public static final int REJECTED = 2;

    public boolean supportsEmail() {
        return recordId != null
                && studentId != null
                && (status == CONFIRMED || status == REJECTED);
    }

    public String todoId() {
        return "internship-confirmation-result:" + recordId + ":" + status;
    }

    public String deliveryId() {
        return "internship-confirmation-result-" + recordId + "-" + status;
    }
}
