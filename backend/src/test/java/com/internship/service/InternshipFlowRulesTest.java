package com.internship.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternshipFlowRulesTest {

    @Test
    void continuingTheOriginalPlacementIsAutomaticallyConfirmed() {
        assertTrue(InternshipFlowRules.isAutomaticallyConfirmed("CONTINUE"));
        assertFalse(InternshipFlowRules.isAutomaticallyConfirmed("NEW_APPLICATION"));
    }

    @Test
    void quotaIsRestoredOnlyOnceWhenAStudentChangesPlacement() {
        assertEquals(1, InternshipFlowRules.quotaReleaseDelta(false));
        assertEquals(0, InternshipFlowRules.quotaReleaseDelta(true));
    }

    @Test
    void finalAppraisalRequiresAtLeastOneFile() {
        assertThrows(IllegalArgumentException.class, () -> InternshipFlowRules.validateAppraisalFileCount(0));
        assertTrue(InternshipFlowRules.validateAppraisalFileCount(1));
        assertTrue(InternshipFlowRules.validateAppraisalFileCount(3));
    }

    @Test
    void stageTwoSelectionAcceptsOnlyContinueOrChange() {
        assertTrue(InternshipFlowRules.isValidStageTwoSelection("CONTINUE"));
        assertTrue(InternshipFlowRules.isValidStageTwoSelection("CHANGE"));
        assertFalse(InternshipFlowRules.isValidStageTwoSelection("STAGE_ONE"));
    }

    @Test
    void stageTwoDecisionBecomesOverdueAfterSelectionEndDate() {
        LocalDate deadline = LocalDate.of(2026, 7, 20);

        assertFalse(InternshipFlowRules.isStageTwoDecisionOverdue(deadline, deadline));
        assertTrue(InternshipFlowRules.isStageTwoDecisionOverdue(deadline.plusDays(1), deadline));
        assertFalse(InternshipFlowRules.isStageTwoDecisionOverdue(deadline.plusDays(1), null));
    }

    @Test
    void studentsWithoutStageOneHistoryCanSelectAStageTwoPositionDirectly() {
        assertTrue(InternshipFlowRules.canStartDirectStageTwoSelection(false, false, true));
        assertFalse(InternshipFlowRules.canStartDirectStageTwoSelection(false, true, true));
        assertFalse(InternshipFlowRules.canStartDirectStageTwoSelection(true, false, true));
    }

    @Test
    void replacedStageOneCanBrowseStageTwoJobsUntilAnApplicationIsActive() {
        assertTrue(InternshipFlowRules.canBrowseStageTwoJobs(
                false, false, true, "REPLACED", true));
        assertFalse(InternshipFlowRules.canBrowseStageTwoJobs(
                false, true, true, "REPLACED", true));
        assertFalse(InternshipFlowRules.canBrowseStageTwoJobs(
                false, false, true, "CONFIRMED", true));
    }

    @Test
    void stageTwoApplicationSourceDependsOnStageOneHistory() {
        assertEquals(InternshipFlowRules.SOURCE_NEW_APPLICATION,
                InternshipFlowRules.resolveStageTwoApplicationSource(false, false));
        assertEquals(InternshipFlowRules.SOURCE_CHANGE,
                InternshipFlowRules.resolveStageTwoApplicationSource(true, true));
        assertThrows(IllegalArgumentException.class,
                () -> InternshipFlowRules.resolveStageTwoApplicationSource(true, false));
    }
}
