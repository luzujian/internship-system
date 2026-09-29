package com.internship.dto;

import java.util.Map;

public record FreeInternshipReconciliationApplyResult(
        FreeInternshipReconciliationReport before,
        FreeInternshipReconciliationReport after,
        Map<String, Integer> updatedRows
) {
}
