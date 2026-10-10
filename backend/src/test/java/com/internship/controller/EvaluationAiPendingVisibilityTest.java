package com.internship.controller;

import com.internship.entity.*;
import com.internship.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EvaluationAiPendingVisibilityTest {
    private final EvaluationManagementController controller = new EvaluationManagementController();
    private final InternshipReflectionService reflections = mock(InternshipReflectionService.class);
    private final CounselorAISettingsService settings = mock(CounselorAISettingsService.class);
    private final StudentReflectionAIAnalysisService ai = mock(StudentReflectionAIAnalysisService.class);
    private final InternshipReflection reflection = new InternshipReflection();

    EvaluationAiPendingVisibilityTest() {
        var scope = mock(EvaluationAccessPolicy.class);
        var statuses = mock(StudentInternshipStatusService.class);
        StudentUser student = new StudentUser(); student.setId(7L); student.setName("学生");
        StudentInternshipStatus status = new StudentInternshipStatus(); status.setStudent(student);
        when(scope.readableStudentIds()).thenReturn(java.util.Set.of(7L));
        when(statuses.list(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of(status));
        reflection.setId(10L); reflection.setPeriodNumber(2); reflection.setContent("实习心得");
        reflection.setStatus(0); reflection.setSubmitTime(new Date());
        when(reflections.list(eq(7L), any(), any(), any())).thenReturn(List.of(reflection));
        ReflectionTestUtils.setField(controller, "evaluationAccessPolicy", scope);
        ReflectionTestUtils.setField(controller, "studentInternshipStatusService", statuses);
        ReflectionTestUtils.setField(controller, "internshipReflectionService", reflections);
        ReflectionTestUtils.setField(controller, "counselorAISettingsService", settings);
        ReflectionTestUtils.setField(controller, "studentReflectionAIAnalysisService", ai);
        ReflectionTestUtils.setField(controller, "internshipAIAnalysisService", mock(InternshipAIAnalysisService.class));
        ReflectionTestUtils.setField(controller, "studentReflectionEvaluationService", mock(StudentReflectionEvaluationService.class));
        ReflectionTestUtils.setField(controller, "internshipEvaluationService", mock(InternshipEvaluationService.class));
    }

    @Test void submittedReportStaysVisibleToTeachersWithoutAnAiReport() {
        var list = rows("2", "true");
        assertEquals(1, list.size());
        assertTrue(list.get(0).getHasCurrentPeriodReport());
        verifyNoInteractions(settings, ai); // AI availability must not determine submission
    }

    @Test void zeroManualScoreIsEvaluatedWhileItsGradeIsStillUnpublished() {
        var evaluations = (StudentReflectionEvaluationService) ReflectionTestUtils.getField(controller, "studentReflectionEvaluationService");
        StudentReflectionEvaluation evaluation = new StudentReflectionEvaluation();
        evaluation.setTotalScore(java.math.BigDecimal.ZERO);
        when(evaluations.findByReflectionId(10L)).thenReturn(evaluation);
        var student = rows("2", "true").getFirst();
        assertTrue(student.getIsEvaluated());
        assertFalse(student.getGradePublished());
    }

    @ParameterizedTest @ValueSource(strings = {"draft", "unsubmitted", "blank", "different-period"})
    void draftsEmptyTasksAndOtherPeriodsAreNotShownAsCurrentSubmittedReports(String state) {
        switch (state) {
            case "draft" -> reflection.setRemark("0");
            case "unsubmitted" -> reflection.setSubmitTime(null);
            case "blank" -> reflection.setContent(" ");
            case "different-period" -> reflection.setPeriodNumber(1);
        }
        assertTrue(rows("2", "true").isEmpty());
        assertFalse(rows("2", "false").get(0).getHasCurrentPeriodReport());
    }

    @SuppressWarnings("unchecked")
    private List<EvaluationManagementController.StudentEvaluationInfo> rows(String period, String hasReport) {
        Result result = controller.getStudentList(period, null, null, null, null, null, hasReport, null, null, null);
        assertEquals(200, result.getCode());
        return (List<EvaluationManagementController.StudentEvaluationInfo>) result.getData();
    }
}
