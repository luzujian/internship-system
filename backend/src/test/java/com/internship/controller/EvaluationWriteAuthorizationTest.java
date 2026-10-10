package com.internship.controller;

import com.internship.entity.InternshipEvaluation;
import com.internship.entity.InternshipReflection;
import com.internship.entity.Result;
import com.internship.entity.StudentReflectionEvaluation;
import com.internship.service.CounselorAISettingsService;
import com.internship.service.EvaluationAccessPolicy;
import com.internship.service.InternshipEvaluationService;
import com.internship.service.InternshipReflectionService;
import com.internship.service.InternshipProgressRecordService;
import com.internship.service.StudentReflectionEvaluationService;
import com.internship.utils.CurrentHolder;
import com.internship.entity.User;
import com.internship.mapper.InternshipReflectionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.Map;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluationWriteAuthorizationTest {
    @InjectMocks private EvaluationManagementController management;
    @InjectMocks private StudentReflectionEvaluationController reflections;
    @InjectMocks private CounselorEvaluationController counselor;
    @Mock private EvaluationAccessPolicy policy;
    @Mock private InternshipEvaluationService grades;
    @Mock private InternshipReflectionService reflectionService;
    @Mock private StudentReflectionEvaluationService reflectionGrades;
    @Mock private CounselorAISettingsService settings;
    @Mock private InternshipReflectionMapper reflectionMapper;
    @Mock private InternshipProgressRecordService progressRecordService;

    @AfterEach
    void clear() {
        CurrentHolder.remove();
    }

    @Test
    void managementEndpointsRequireCounselorOrAdminRole() throws Exception {
        for (String name : List.of("submitEvaluation", "batchUploadGrades", "getEvaluationStatistics")) {
            Class<?> parameter = name.equals("getEvaluationStatistics") ? null : Map.class;
            PreAuthorize guard = parameter == null
                    ? EvaluationManagementController.class.getMethod(name).getAnnotation(PreAuthorize.class)
                    : EvaluationManagementController.class.getMethod(name, parameter).getAnnotation(PreAuthorize.class);
            assertEquals("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN')", guard.value());
        }
    }

    @Test
    void outOfScopeSubmitAndBatchCannotWriteOrPublish() {
        login(23L, "ROLE_TEACHER_COUNSELOR");
        doThrow(new AccessDeniedException("out of scope")).when(policy).requireStudent(8L, false);

        assertThrows(AccessDeniedException.class,
                () -> management.submitEvaluation(Map.of("studentId", 8, "comment", "test", "attitude", 90)));
        assertThrows(AccessDeniedException.class,
                () -> management.batchUploadGrades(Map.of("studentIds", List.of(7, 8))));
        verify(grades, never()).insert(any());
        verify(grades, never()).update(any());
        verify(grades, never()).publishGrades(any());
    }

    @Test
    void inScopeCounselorCanSubmitOrdinaryScore() {
        login(23L, "ROLE_TEACHER_COUNSELOR");
        when(settings.getCategoryWeights(23L)).thenReturn(List.of());
        when(grades.insert(any(InternshipEvaluation.class))).thenReturn(1);
        when(reflectionService.list(7L, null, null, null)).thenReturn(List.of());

        Result result = management.submitEvaluation(Map.of("studentId", 7, "comment", "good", "attitude", 90));
        assertEquals(200, result.getCode());
        verify(policy).requireStudent(7L, false);
        verify(grades).insert(any(InternshipEvaluation.class));
    }

    @Test
    void manualScoreLocksTheCurrentReportBeforeAnyGradeWriteAndKeepsItUnpublished() {
        login(23L, "ROLE_TEACHER_COUNSELOR");
        InternshipReflection submitted = submitted();
        when(reflectionService.calculatePeriodNumber(any(Date.class))).thenReturn(2);
        when(reflectionService.list(7L, null, null, null)).thenReturn(List.of(submitted));
        when(reflectionMapper.lockById(99L)).thenReturn(submitted);
        when(settings.getCategoryWeights(23L)).thenReturn(List.of());
        when(grades.insert(any(InternshipEvaluation.class))).thenReturn(1);

        Result result = management.submitEvaluation(Map.of("studentId", 7, "comment", "good", "attitude", 90));
        assertEquals(200, result.getCode());
        var order = inOrder(reflectionMapper, grades, reflectionGrades);
        order.verify(reflectionMapper).lockById(99L);
        order.verify(grades).insert(any(InternshipEvaluation.class));
        order.verify(reflectionGrades).findByReflectionId(99L);
        order.verify(reflectionGrades).saveEvaluation(any(StudentReflectionEvaluation.class));
        assertNull(submitted.getRemark());
        verify(reflectionService, never()).updateById(any());
        verify(grades, never()).publishGrades(any());
    }

    @Test
    void reportWithdrawnBeforeManualLockCannotSaveAnOverallOrPeriodGrade() {
        login(23L, "ROLE_TEACHER_COUNSELOR");
        InternshipReflection original = submitted();
        InternshipReflection current = submitted(); current.setRemark("0");
        when(reflectionService.calculatePeriodNumber(any(Date.class))).thenReturn(2);
        when(reflectionService.list(7L, null, null, null)).thenReturn(List.of(original));
        when(reflectionMapper.lockById(99L)).thenReturn(current);

        assertEquals(400, management.submitEvaluation(Map.of("studentId", 7, "comment", "good", "attitude", 90)).getCode());
        verify(grades, never()).insert(any());
        verify(grades, never()).update(any());
        verify(reflectionGrades, never()).saveEvaluation(any());
    }

    @Test
    void reflectionSaveUsesStoredStudentAndCurrentCounselor() {
        StudentReflectionEvaluation evaluation = new StudentReflectionEvaluation();
        evaluation.setReflectionId(99L);
        evaluation.setCounselorId(999L);
        when(policy.requireReflection(99L, null, false)).thenReturn(7L);
        when(policy.evaluatorId(999L)).thenReturn(23L);
        when(reflectionGrades.saveEvaluation(evaluation)).thenReturn(1);

        assertEquals(200, reflections.saveEvaluation(evaluation).getCode());
        assertEquals(7L, evaluation.getStudentId());
        assertEquals(23L, evaluation.getCounselorId());
    }

    @Test
    void reflectionCannotBeReassignedToAnotherStudentOrReflection() {
        StudentReflectionEvaluation existing = new StudentReflectionEvaluation();
        existing.setId(3L);
        existing.setReflectionId(99L);
        existing.setStudentId(7L);
        when(reflectionGrades.findById(3L)).thenReturn(existing);
        StudentReflectionEvaluation changed = new StudentReflectionEvaluation();
        changed.setReflectionId(100L);
        changed.setStudentId(8L);

        assertThrows(AccessDeniedException.class, () -> reflections.updateEvaluation(3L, changed));
        verify(reflectionGrades, never()).saveEvaluation(any());
    }

    @Test
    void counselorLegacyEndpointBindsSubmittedEvaluatorToActor() {
        when(policy.requireReflection(99L, null, false)).thenReturn(7L);
        when(policy.evaluatorId(999L)).thenReturn(23L);
        when(reflectionGrades.insert(any(StudentReflectionEvaluation.class))).thenAnswer(invocation -> {
            StudentReflectionEvaluation row = invocation.getArgument(0);
            assertEquals(7L, row.getStudentId());
            assertEquals(23L, row.getCounselorId());
            return 1;
        });

        assertEquals(200, counselor.saveEvaluation(Map.of("reflectionId", 99,
                "counselorId", 999, "totalScore", 90, "grade", "优秀", "scoreDetails", "{}" )).getCode());
        verify(reflectionGrades).insert(any(StudentReflectionEvaluation.class));
    }

    @Test
    void crossStudentDetailRejectedBeforeSensitiveReads() {
        doThrow(new AccessDeniedException("out of scope")).when(policy).requireStudent(8L, false);
        assertThrows(AccessDeniedException.class, () -> management.getStudentEvaluationInfo(8L, null));
        verify(reflectionService, never()).list(any(), any(), any(), any());
    }

    @Test
    void crossStudentReflectionReadAndWriteRejectedBeforeRecordAccess() {
        doThrow(new AccessDeniedException("out of scope")).when(policy).requireStudent(8L, true);
        assertThrows(AccessDeniedException.class, () -> reflections.getEvaluationByStudentId(8L));
        verify(reflectionGrades, never()).findByStudentId(8L);

        doThrow(new AccessDeniedException("out of scope")).when(policy).requireReflection(99L, 8L, false);
        StudentReflectionEvaluation forged = new StudentReflectionEvaluation();
        forged.setReflectionId(99L);
        forged.setStudentId(8L);
        assertThrows(AccessDeniedException.class, () -> reflections.saveEvaluation(forged));
        verify(reflectionGrades, never()).saveEvaluation(any());
    }

    private void login(Long id, String role) {
        User actor = new User();
        actor.setId(id);
        actor.setRole(role);
        CurrentHolder.setUser(actor);
    }

    private InternshipReflection submitted() {
        InternshipReflection reflection = new InternshipReflection();
        reflection.setId(99L); reflection.setStudentId(7L); reflection.setPeriodNumber(2);
        reflection.setContent("实习心得"); reflection.setSubmitTime(new Date());
        reflection.setDeleted(0); reflection.setStatus(0);
        return reflection;
    }
}
