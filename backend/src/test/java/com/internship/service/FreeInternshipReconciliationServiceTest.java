package com.internship.service;

import com.internship.dto.FreeInternshipReconciliationReport;
import com.internship.dto.FreeInternshipReconciliationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FreeInternshipReconciliationServiceTest {

    @Test
    void rejectsMappingWhenConfirmationStudentNumberDoesNotMatchTarget() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FreeInternshipReconciliationService service = new FreeInternshipReconciliationService(jdbcTemplate);
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq(7L)))
                .thenReturn(List.of("s001"));
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq(1L)))
                .thenReturn(List.of("s999"));

        List<String> warnings = new ArrayList<>();
        List<FreeInternshipReconciliationReport.MappingAssessment> assessments = service.assessMappings(
                List.of(new FreeInternshipReconciliationRequest.StudentIdMapping(1L, 7L)), warnings);

        assertEquals(1, assessments.size());
        assertFalse(assessments.get(0).valid());
        assertTrue(assessments.get(0).message().contains("does not uniquely match"));
        assertFalse(warnings.isEmpty());
    }

    @Test
    void recognizesAnAlreadyAppliedMappingAndSkipsWhitelistUpdates() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FreeInternshipReconciliationService service = new FreeInternshipReconciliationService(jdbcTemplate);
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq(7L)))
                .thenReturn(List.of("s001"), List.of("s001"));
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq(1L)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq(1L))).thenReturn(0L);

        List<FreeInternshipReconciliationReport.MappingAssessment> assessments = service.assessMappings(
                List.of(new FreeInternshipReconciliationRequest.StudentIdMapping(1L, 7L)), new ArrayList<>());

        assertTrue(assessments.get(0).valid());
        assertTrue(assessments.get(0).alreadyApplied());
        Map<String, Integer> counts = new LinkedHashMap<>();
        service.updateMappedStudentIds(assessments, counts);
        assertTrue(counts.isEmpty());
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void mapsStudentIdOnlyInTheFiveApprovedHistoryTables() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FreeInternshipReconciliationService service = new FreeInternshipReconciliationService(jdbcTemplate);
        FreeInternshipReconciliationReport.MappingAssessment mapping =
                new FreeInternshipReconciliationReport.MappingAssessment(
                        1L, 7L, "s001", true, false, "verified");
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        service.updateMappedStudentIds(List.of(mapping), new LinkedHashMap<>());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, org.mockito.Mockito.times(6)).update(sql.capture(), any(Object[].class));
        Set<String> tables = sql.getAllValues().stream()
                .filter(statement -> statement.startsWith("UPDATE"))
                .map(statement -> statement.split(" ")[1])
                .collect(Collectors.toSet());
        assertEquals(Set.of(
                "internship_confirmation_record",
                "internship_stage_assignment",
                "interview_invitation",
                "internship_progress_record",
                "position_view_record"), tables);
        assertTrue(sql.getAllValues().stream().anyMatch(statement ->
                statement.startsWith("DELETE pv FROM position_view_record")), "must deduplicate view records first");
    }

    @Test
    void locksTargetStudentsInStableIdOrderBeforeMutation() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FreeInternshipReconciliationService service = new FreeInternshipReconciliationService(jdbcTemplate);

        service.lockTargetStudents(List.of(
                new FreeInternshipReconciliationRequest.StudentIdMapping(2L, 9L),
                new FreeInternshipReconciliationRequest.StudentIdMapping(1L, 7L)));

        org.mockito.InOrder order = inOrder(jdbcTemplate);
        order.verify(jdbcTemplate).queryForList(
                "SELECT id FROM student_users WHERE id = ? FOR UPDATE", Long.class, 7L);
        order.verify(jdbcTemplate).queryForList(
                "SELECT id FROM student_users WHERE id = ? FOR UPDATE", Long.class, 9L);
    }

    @Test
    void applyRequiresExactTokenBeforeReadingOrWritingDatabase() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FreeInternshipReconciliationService service = new FreeInternshipReconciliationService(jdbcTemplate);
        FreeInternshipReconciliationRequest request = new FreeInternshipReconciliationRequest(
                List.of(new FreeInternshipReconciliationRequest.StudentIdMapping(1L, 7L)), "wrong");

        assertThrows(IllegalArgumentException.class, () -> service.apply(request));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void mutationFailureEscapesTransactionalBoundaryAndStopsLaterSteps() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FreeInternshipReconciliationService service = spy(new FreeInternshipReconciliationService(jdbcTemplate));
        FreeInternshipReconciliationRequest request = new FreeInternshipReconciliationRequest(
                List.of(new FreeInternshipReconciliationRequest.StudentIdMapping(1L, 7L)),
                FreeInternshipReconciliationService.APPLY_CONFIRMATION_TOKEN);
        FreeInternshipReconciliationReport.MappingAssessment mapping =
                new FreeInternshipReconciliationReport.MappingAssessment(
                        1L, 7L, "s001", true, false, "verified");
        FreeInternshipReconciliationReport report = new FreeInternshipReconciliationReport(
                true, Map.of(), List.of(mapping), Map.of(), Map.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        doReturn(report).when(service).preview(any());
        doThrow(new DataAccessResourceFailureException("simulated failure"))
                .when(service).updateMappedStudentIds(anyList(), anyMap());

        assertThrows(DataAccessResourceFailureException.class, () -> service.apply(request));
        verify(service, never()).mergeContinueChains(anyList(), anyMap());
        verify(service, never()).rebuildPositionQuotas(anyMap());

        Method apply = FreeInternshipReconciliationService.class.getMethod(
                "apply", FreeInternshipReconciliationRequest.class);
        Transactional transactional = apply.getAnnotation(Transactional.class);
        assertTrue(transactional != null);
        assertEquals(List.of(Exception.class), List.of(transactional.rollbackFor()));
    }
}
