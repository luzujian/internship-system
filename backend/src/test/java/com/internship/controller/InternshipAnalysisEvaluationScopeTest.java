package com.internship.controller;

import com.internship.entity.InternshipAIAnalysis;
import com.internship.entity.InternshipEvaluation;
import com.internship.entity.PageResult;
import com.internship.entity.Result;
import com.internship.entity.TeacherUser;
import com.internship.entity.User;
import com.internship.mapper.TeacherAutomationScopeMapper;
import com.internship.mapper.TeacherUserMapper;
import com.internship.service.InternshipAIAnalysisService;
import com.internship.service.InternshipEvaluationService;
import com.internship.utils.CurrentHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InternshipAnalysisEvaluationScopeTest {
    private final InternshipAIAnalysisController analyses = new InternshipAIAnalysisController();
    private final InternshipEvaluationController evaluations = new InternshipEvaluationController();
    private final InternshipAIAnalysisService analysisService = mock(InternshipAIAnalysisService.class);
    private final InternshipEvaluationService evaluationService = mock(InternshipEvaluationService.class);
    private final TeacherUserMapper teachers = mock(TeacherUserMapper.class);
    private final TeacherAutomationScopeMapper scopes = mock(TeacherAutomationScopeMapper.class);

    InternshipAnalysisEvaluationScopeTest() {
        ReflectionTestUtils.setField(analyses, "internshipAIAnalysisService", analysisService);
        ReflectionTestUtils.setField(analyses, "teacherUserMapper", teachers);
        ReflectionTestUtils.setField(analyses, "teacherScopeMapper", scopes);
        ReflectionTestUtils.setField(evaluations, "internshipEvaluationService", evaluationService);
        ReflectionTestUtils.setField(evaluations, "teacherUserMapper", teachers);
        ReflectionTestUtils.setField(evaluations, "teacherScopeMapper", scopes);
    }

    @AfterEach
    void clearIdentity() {
        CurrentHolder.remove();
    }

    @Test
    void teacherAnalysisListFiltersOtherStudentsAndDetailDeniesCrossScope() {
        TeacherUser teacher = loginTeacher();
        when(scopes.studentIds(teacher)).thenReturn(List.of(7L));
        when(analysisService.findAll()).thenReturn(List.of(analysis(1L, 7L), analysis(2L, 8L)));
        when(analysisService.findById(2L)).thenReturn(analysis(2L, 8L));

        Result result = analyses.getAllAnalysis();
        assertEquals(List.of(1L), ((List<InternshipAIAnalysis>) result.getData()).stream()
                .map(InternshipAIAnalysis::getId).toList());
        assertThrows(AccessDeniedException.class, () -> analyses.getAnalysisById(2L));
    }

    @Test
    void teacherCannotReassignAnalysisOrEvaluationToAnotherStudent() {
        TeacherUser teacher = loginTeacher();
        when(scopes.contains(teacher, 7L)).thenReturn(1);
        when(analysisService.findById(1L)).thenReturn(analysis(1L, 7L));
        when(evaluationService.findById(3L)).thenReturn(evaluation(3L, 7L));

        assertThrows(AccessDeniedException.class,
                () -> analyses.updateAnalysis(1L, analysis(1L, 8L)));
        assertThrows(AccessDeniedException.class,
                () -> evaluations.updateEvaluation(3L, evaluation(3L, 8L)));
        verify(analysisService, never()).update(org.mockito.ArgumentMatchers.any());
        verify(evaluationService, never()).update(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void teacherEvaluationPageCountsOnlyOwnStudents() {
        TeacherUser teacher = loginTeacher();
        when(scopes.studentIds(teacher)).thenReturn(List.of(7L));
        when(evaluationService.findAll()).thenReturn(List.of(evaluation(3L, 7L), evaluation(4L, 8L)));

        PageResult<?> page = (PageResult<?>) evaluations.getEvaluationsByPage(1, 10, null).getData();
        assertEquals(1L, page.getTotal());
        assertEquals(1, page.getRows().size());
        assertEquals(3L, ((InternshipEvaluation) page.getRows().get(0)).getId());
    }

    @Test
    void studentCanReadOnlyOwnPublishedEvaluation() {
        loginStudent(7L);
        InternshipEvaluation unpublished = evaluation(3L, 7L);
        unpublished.setGradePublished(0);
        when(evaluationService.findByStudentId(7L)).thenReturn(unpublished);

        assertThrows(AccessDeniedException.class, () -> evaluations.getEvaluationByStudentId(8L));
        assertThrows(AccessDeniedException.class, () -> evaluations.getEvaluationByStudentId(7L));
        unpublished.setGradePublished(1);
        assertEquals(200, evaluations.getEvaluationByStudentId(7L).getCode());
    }

    private TeacherUser loginTeacher() {
        User user = new User();
        user.setId(3L);
        user.setRole("ROLE_TEACHER_COUNSELOR");
        CurrentHolder.setUser(user);
        TeacherUser teacher = new TeacherUser();
        teacher.setId(3L);
        teacher.setStatus(1);
        teacher.setTeacherType("COUNSELOR");
        when(teachers.findById(3L)).thenReturn(teacher);
        return teacher;
    }

    private void loginStudent(Long id) {
        User user = new User();
        user.setId(id);
        user.setRole("ROLE_STUDENT");
        CurrentHolder.setUser(user);
    }

    private InternshipAIAnalysis analysis(Long id, Long studentId) {
        InternshipAIAnalysis item = new InternshipAIAnalysis();
        item.setId(id);
        item.setStudentId(studentId);
        return item;
    }

    private InternshipEvaluation evaluation(Long id, Long studentId) {
        InternshipEvaluation item = new InternshipEvaluation();
        item.setId(id);
        item.setStudentId(studentId);
        return item;
    }
}
