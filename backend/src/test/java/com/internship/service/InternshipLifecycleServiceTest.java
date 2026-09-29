package com.internship.service;

import com.internship.entity.InternshipProgressRecord;
import com.internship.entity.InternshipStageAssignment;
import com.internship.entity.StudentInternshipStatus;
import com.internship.mapper.PositionMapper;
import com.internship.mapper.StudentInternshipStatusMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternshipLifecycleServiceTest {
    @Mock private StudentInternshipStatusMapper statusMapper;
    @Mock private PositionMapper positionMapper;
    @Mock private InternshipStageAssignmentService assignmentService;
    @Mock private InternshipProgressRecordService progressRecordService;

    private InternshipLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new InternshipLifecycleService(statusMapper, positionMapper, assignmentService, progressRecordService);
    }

    @Test
    void startsInternshipAndUsesConfiguredStartAsEventTime() {
        Date occurredAt = new Date(System.currentTimeMillis() - 60_000);
        StudentInternshipStatus status = status(2, occurredAt, null);
        InternshipStageAssignment assignment = assignment(2);
        when(statusMapper.findById(10L)).thenReturn(status);
        when(statusMapper.transitionStatus(10L, 2, 3)).thenReturn(1);
        when(assignmentService.findByStudentId(20L)).thenReturn(java.util.List.of(assignment));

        assertTrue(service.startConfirmedInternship(10L, occurredAt));

        assertEquals(3, status.getStatus());
        ArgumentCaptor<InternshipProgressRecord> event = ArgumentCaptor.forClass(InternshipProgressRecord.class);
        verify(progressRecordService).saveRecord(event.capture());
        assertEquals("internship_started", event.getValue().getEventType());
        assertEquals(occurredAt, event.getValue().getEventTime());
        assertEquals(200L, event.getValue().getRelatedId());
    }

    @Test
    void finishesInternshipAndUsesConfiguredEndAsEventTime() {
        Date occurredAt = new Date(System.currentTimeMillis() - 60_000);
        StudentInternshipStatus status = status(3, null, occurredAt);
        when(statusMapper.findById(10L)).thenReturn(status);
        when(statusMapper.transitionStatus(10L, 3, 4)).thenReturn(1);
        when(assignmentService.findByStudentId(20L)).thenReturn(java.util.List.of(assignment(1)));

        assertTrue(service.finishActiveInternship(10L, occurredAt));

        assertEquals(4, status.getStatus());
        ArgumentCaptor<InternshipProgressRecord> event = ArgumentCaptor.forClass(InternshipProgressRecord.class);
        verify(progressRecordService).saveRecord(event.capture());
        assertEquals("internship_ended", event.getValue().getEventType());
        assertEquals(occurredAt, event.getValue().getEventTime());
    }

    @Test
    void refreshAdvancesDueActiveInternshipWithoutWaitingForScheduler() {
        Date occurredAt = new Date(System.currentTimeMillis() - 60_000);
        StudentInternshipStatus active = status(3, null, occurredAt);
        StudentInternshipStatus ended = status(4, null, occurredAt);
        when(statusMapper.findByStudentId(20L)).thenReturn(active, ended);
        when(statusMapper.findById(10L)).thenReturn(active);
        when(statusMapper.transitionStatus(10L, 3, 4)).thenReturn(1);
        when(assignmentService.findByStudentId(20L)).thenReturn(List.of(assignment(1)));

        StudentInternshipStatus result = service.refreshStudentLifecycle(20L);

        assertEquals(4, result.getStatus());
        verify(statusMapper).transitionStatus(10L, 3, 4);
        verify(progressRecordService).saveRecord(any(InternshipProgressRecord.class));
    }

    @Test
    void refreshKeepsActiveInternshipBeforeItsEndBoundary() {
        Date futureEnd = new Date(System.currentTimeMillis() + 60_000);
        StudentInternshipStatus active = status(3, null, futureEnd);
        when(statusMapper.findByStudentId(20L)).thenReturn(active);

        StudentInternshipStatus result = service.refreshStudentLifecycle(20L);

        assertEquals(3, result.getStatus());
        verify(statusMapper, never()).transitionStatus(any(), any(), any());
    }

    @Test
    void ignoresAlreadyFinishedInternshipWithoutDuplicateEvent() {
        when(statusMapper.findById(10L)).thenReturn(status(4, null, new Date()));

        assertFalse(service.finishActiveInternship(10L, new Date()));

        verify(statusMapper, never()).transitionStatus(any(), any(), any());
        verify(progressRecordService, never()).saveRecord(any(InternshipProgressRecord.class));
    }

    @Test
    void matchesLifecycleBoundaryToTheCorrectPlacementWithoutStageCopy() {
        Date stageOneStart = new Date(1_000_000L);
        Date stageOneEnd = new Date(2_000_000L);
        Date stageTwoStart = new Date(2_000_000L);
        Date stageTwoEnd = new Date(3_000_000L);
        Date nowPastStageTwo = new Date(System.currentTimeMillis() - 60_000L);
        long shift = nowPastStageTwo.getTime() - stageTwoEnd.getTime();
        stageOneStart = new Date(stageOneStart.getTime() + shift);
        stageOneEnd = new Date(stageOneEnd.getTime() + shift);
        stageTwoStart = new Date(stageTwoStart.getTime() + shift);
        stageTwoEnd = nowPastStageTwo;

        StudentInternshipStatus status = status(3, stageOneStart, stageTwoEnd);
        InternshipStageAssignment first = assignment(1);
        first.setId(101L);
        first.setInternshipStartTime(stageOneStart);
        first.setInternshipEndTime(stageOneEnd);
        InternshipStageAssignment second = assignment(2);
        second.setId(202L);
        second.setInternshipStartTime(stageTwoStart);
        second.setInternshipEndTime(stageTwoEnd);

        when(statusMapper.findById(10L)).thenReturn(status);
        when(statusMapper.transitionStatus(10L, 3, 4)).thenReturn(1);
        when(assignmentService.findByStudentId(20L)).thenReturn(List.of(first, second));

        assertTrue(service.finishActiveInternship(10L, stageTwoEnd));

        ArgumentCaptor<InternshipProgressRecord> event = ArgumentCaptor.forClass(InternshipProgressRecord.class);
        verify(progressRecordService).saveRecord(event.capture());
        assertEquals(202L, event.getValue().getRelatedId());
        assertEquals("测试企业 · Java实习生", event.getValue().getDescription());
        assertFalse(event.getValue().getDescription().contains("阶段"));
    }

    private StudentInternshipStatus status(int state, Date start, Date end) {
        StudentInternshipStatus status = new StudentInternshipStatus();
        status.setId(10L);
        status.setStudentId(20L);
        status.setStatus(state);
        status.setCompanyId(30L);
        status.setPositionId(40L);
        status.setCompanyName("测试企业");
        status.setPositionName("Java实习生");
        status.setInternshipStartTime(start);
        status.setInternshipEndTime(end);
        return status;
    }

    private InternshipStageAssignment assignment(int stageNo) {
        InternshipStageAssignment assignment = new InternshipStageAssignment();
        assignment.setId(200L);
        assignment.setStageNo(stageNo);
        assignment.setCurrentFlag(1);
        assignment.setCompanyId(30L);
        assignment.setPositionId(40L);
        return assignment;
    }
}
