package com.internship.dto;

import java.util.List;
import java.util.Map;

public record FreeInternshipReconciliationReport(
        boolean executable,
        Map<String, Integer> counts,
        List<MappingAssessment> mappings,
        Map<String, List<Long>> mappingImpacts,
        Map<String, List<Long>> remainingOrphans,
        List<ChainMerge> chainMerges,
        List<PositionQuotaChange> positionQuotaChanges,
        List<Long> enabledDualPhaseSettingIds,
        List<Long> invalidEmptyAutoReflectionIds,
        List<Long> preservedSubmittedAppraisalIds,
        List<String> warnings
) {
    public record MappingAssessment(
            Long legacyStudentId,
            Long targetStudentId,
            String studentUserId,
            boolean valid,
            boolean alreadyApplied,
            String message
    ) {
    }

    public record ChainMerge(
            Long studentId,
            Long companyId,
            Long positionId,
            Long retainedAssignmentId,
            Long retainedConfirmationRecordId,
            List<Long> retiredAssignmentIds,
            List<Long> clearedConfirmationAssignmentIds
    ) {
    }

    public record PositionQuotaChange(
            Long positionId,
            int beforeRecruited,
            int afterRecruited,
            int beforeRemaining,
            int afterRemaining
    ) {
    }
}
