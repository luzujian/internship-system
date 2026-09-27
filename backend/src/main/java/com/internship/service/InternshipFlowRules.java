package com.internship.service;

import java.time.LocalDate;

/**
 * Shared, side-effect-free rules for the two-stage internship flow.
 * Keeping these decisions in one place makes controller and service code
 * consistent and keeps the most important state transitions easy to test.
 */
public final class InternshipFlowRules {

    public static final String SOURCE_CONTINUE = "CONTINUE";
    public static final String SOURCE_NEW_APPLICATION = "NEW_APPLICATION";
    public static final String SOURCE_CHANGE = "CHANGE";

    private InternshipFlowRules() {
    }

    public static boolean isAutomaticallyConfirmed(String sourceType) {
        return SOURCE_CONTINUE.equalsIgnoreCase(sourceType);
    }

    public static int quotaReleaseDelta(boolean alreadyReleased) {
        return alreadyReleased ? 0 : 1;
    }

    public static boolean validateAppraisalFileCount(int fileCount) {
        if (fileCount < 1) {
            throw new IllegalArgumentException("至少上传一份实习鉴定表");
        }
        return true;
    }

    public static boolean isValidStageTwoSelection(String selection) {
        return SOURCE_CONTINUE.equalsIgnoreCase(selection)
                || SOURCE_CHANGE.equalsIgnoreCase(selection);
    }

    public static boolean isStageTwoDecisionOverdue(LocalDate today, LocalDate selectionEndDate) {
        return today != null && selectionEndDate != null && today.isAfter(selectionEndDate);
    }

    public static boolean canStartDirectStageTwoSelection(boolean stageTwoExists,
                                                           boolean hasStageOneHistory,
                                                           boolean selectionOpen) {
        return !stageTwoExists && !hasStageOneHistory && selectionOpen;
    }

    public static boolean canBrowseStageTwoJobs(boolean stageTwoExists,
                                                boolean hasActiveStageTwoApplication,
                                                boolean hasStageOneHistory,
                                                String latestStageOneStatus,
                                                boolean selectionOpen) {
        if (stageTwoExists || hasActiveStageTwoApplication || !selectionOpen) return false;
        return !hasStageOneHistory || "REPLACED".equals(latestStageOneStatus);
    }

    public static String resolveStageTwoApplicationSource(boolean hasStageOneHistory,
                                                           boolean hasReplacedStageOne) {
        if (!hasStageOneHistory) return SOURCE_NEW_APPLICATION;
        if (hasReplacedStageOne) return SOURCE_CHANGE;
        throw new IllegalArgumentException("存在第一阶段记录时必须先完成第二阶段岗位选择");
    }
}
