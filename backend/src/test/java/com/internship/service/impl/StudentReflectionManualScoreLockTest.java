package com.internship.service.impl;

import com.internship.entity.InternshipReflection;
import com.internship.entity.StudentReflectionEvaluation;
import com.internship.exception.BusinessException;
import com.internship.mapper.InternshipReflectionMapper;
import com.internship.mapper.StudentReflectionEvaluationMapper;
import com.internship.service.CounselorAISettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StudentReflectionManualScoreLockTest {
    private final StudentReflectionEvaluationServiceImpl service = new StudentReflectionEvaluationServiceImpl();
    private final InternshipReflectionMapper reflections = mock(InternshipReflectionMapper.class);
    private final StudentReflectionEvaluationMapper evaluations = mock(StudentReflectionEvaluationMapper.class);
    private final InternshipReflection submitted = new InternshipReflection();
    private final StudentReflectionEvaluation score = new StudentReflectionEvaluation();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "reflectionMapper", reflections);
        ReflectionTestUtils.setField(service, "evaluationMapper", evaluations);
        ReflectionTestUtils.setField(service, "counselorAISettingsService", mock(CounselorAISettingsService.class));
        submitted.setId(10L); submitted.setStudentId(42L); submitted.setContent("实习心得");
        submitted.setStatus(0); submitted.setDeleted(0); submitted.setSubmitTime(new Date(1000));
        when(reflections.lockById(10L)).thenReturn(submitted);
        score.setId(50L); score.setReflectionId(10L); score.setStudentId(42L); score.setCounselorId(9L);
        score.setScoreDetails(Map.of("attitude", 0)); score.setTotalScore(BigDecimal.ZERO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"insert", "update", "save"})
    void everyManualWriteLocksTheActualReportBeforeSavingIncludingZeroScores(String entry) {
        write(entry);
        var order = inOrder(reflections, evaluations);
        order.verify(reflections).lockById(10L);
        if ("save".equals(entry)) order.verify(evaluations).lockByReflectionId(10L);
        if ("update".equals(entry)) order.verify(evaluations).update(score);
        else order.verify(evaluations).insert(score);
        assertEquals(0, score.getTotalScore().compareTo(BigDecimal.ZERO));
        assertNull(submitted.getRemark());
        verify(reflections, never()).updateById(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"insert", "update", "save"})
    void aReportWithdrawnDuringManualPreparationCannotReceiveAStoredScore(String entry) {
        submitted.setRemark("0");
        assertThrows(BusinessException.class, () -> write(entry));
        verifyNoInteractions(evaluations);
    }

    @Test
    void concurrentManualUpsertUsesTheExistingRowFromTheCurrentRead() {
        StudentReflectionEvaluation current = new StudentReflectionEvaluation();
        current.setId(61L); current.setReflectionId(10L); current.setCreateTime(new Date(500));
        when(evaluations.lockByReflectionId(10L)).thenReturn(current);
        when(evaluations.update(score)).thenReturn(1);
        assertEquals(1, service.saveEvaluation(score));
        assertEquals(61L, score.getId());
        assertEquals(new Date(500), score.getCreateTime());
        verify(evaluations, never()).insert(any());
        verify(evaluations, never()).findByReflectionId(10L);
    }

    @Test
    void anUpdateContainingOnlyTheEvaluationIdLocksItsStoredReport() {
        score.setReflectionId(null);
        StudentReflectionEvaluation existing = new StudentReflectionEvaluation();
        existing.setId(50L); existing.setReflectionId(10L);
        when(evaluations.findById(50L)).thenReturn(existing);
        service.update(score);
        var order = inOrder(evaluations, reflections);
        order.verify(evaluations).findById(50L);
        order.verify(reflections).lockById(10L);
        order.verify(evaluations).update(score);
    }

    @Test
    void deletedMissingEmptyOrUnsubmittedReportsCannotBeScored() {
        submitted.setDeleted(1);
        assertThrows(BusinessException.class, () -> service.saveEvaluation(score));
        submitted.setDeleted(0); submitted.setContent(" ");
        assertThrows(BusinessException.class, () -> service.saveEvaluation(score));
        submitted.setContent("实习心得"); submitted.setSubmitTime(null);
        assertThrows(BusinessException.class, () -> service.saveEvaluation(score));
        when(reflections.lockById(10L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.saveEvaluation(score));
        verifyNoInteractions(evaluations);
    }

    private void write(String entry) {
        switch (entry) {
            case "insert" -> service.insert(score);
            case "update" -> service.update(score);
            case "save" -> service.saveEvaluation(score);
            default -> throw new AssertionError(entry);
        }
    }
}
