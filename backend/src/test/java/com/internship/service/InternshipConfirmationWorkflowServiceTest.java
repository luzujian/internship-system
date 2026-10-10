package com.internship.service;

import com.internship.entity.InternshipConfirmationRecord;
import com.internship.entity.InternshipStageAssignment;
import com.internship.entity.Position;
import com.internship.entity.StudentInternshipStatus;
import com.internship.exception.BusinessException;
import com.internship.mapper.InternshipConfirmationRecordMapper;
import com.internship.mapper.InternshipStageAssignmentMapper;
import com.internship.mapper.PositionMapper;
import com.internship.mapper.StudentInternshipStatusMapper;
import com.internship.util.StudentInternshipStatusCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternshipConfirmationWorkflowServiceTest {
    @Mock private InternshipConfirmationRecordMapper confirmationMapper;
    @Mock private InternshipStageAssignmentMapper assignmentMapper;
    @Mock private StudentInternshipStatusMapper statusMapper;
    @Mock private PositionMapper positionMapper;
    @Mock private InternshipProgressRecordService progressRecordService;
    @Mock private StudentInternshipStatusCache statusCache;
    @Mock private InternshipCalendarService internshipCalendarService;
    @Mock private InternshipConfirmationResultNotifier confirmationResultNotifier;
    @Mock private InternshipAgreementService agreementService;

    private InternshipConfirmationWorkflowService service;

    @BeforeEach
    void setUp() {
        service = new InternshipConfirmationWorkflowService(
                confirmationMapper, assignmentMapper, statusMapper, positionMapper,
                progressRecordService, statusCache, internshipCalendarService,
                confirmationResultNotifier);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "agreementService", agreementService);
    }

    @Test
    void submitOfferCreatesOneNonCurrentStageOnePlacementWithStableOfferSource() {
        InternshipConfirmationRecord record = pendingRecord(7L, 200L, 20L, 70L);
        record.setId(null);
        record.setAgreementFileIds(List.of(501L));
        when(confirmationMapper.lockStudent(7L)).thenReturn(7L);
        when(assignmentMapper.findByStudentId(7L)).thenReturn(List.of());
        when(assignmentMapper.countByStudent(7L)).thenReturn(3);
        doAnswer(invocation -> {
            InternshipConfirmationRecord saved = invocation.getArgument(0);
            saved.setId(70L);
            return 1;
        }).when(confirmationMapper).insert(any(InternshipConfirmationRecord.class));
        doAnswer(invocation -> {
            InternshipStageAssignment saved = invocation.getArgument(0);
            saved.setId(90L);
            return 1;
        }).when(assignmentMapper).insert(any(InternshipStageAssignment.class));

        InternshipConfirmationRecord saved = service.submitOffer(record, 880L, null);

        assertEquals(70L, saved.getId());
        verify(agreementService).bindSubmission(7L, "CONFIRMATION", 70L, List.of(501L));
        assertEquals(0, saved.getRecallStatus());
        ArgumentCaptor<InternshipStageAssignment> placement =
                ArgumentCaptor.forClass(InternshipStageAssignment.class);
        verify(assignmentMapper).insert(placement.capture());
        assertEquals(1, placement.getValue().getStageNo());
        assertEquals(4, placement.getValue().getSequenceNo());
        assertEquals(0, placement.getValue().getCurrentFlag());
        assertEquals("PENDING_CONFIRMATION", placement.getValue().getStatus());
        assertEquals(InternshipConfirmationWorkflowService.OFFER_SOURCE,
                placement.getValue().getSourceType());
        assertEquals(880L, placement.getValue().getSourceAssignmentId());
        assertNull(placement.getValue().getApplicationId());
        assertEquals(70L, placement.getValue().getConfirmationRecordId());
    }

    @Test
    void companyConfirmationAtomicallySwitchesCurrentPlacementAndQuotas() {
        Date confirmedAt = Date.from(LocalDateTime.of(2026, 8, 1, 10, 30)
                .atZone(ZoneId.of("Asia/Shanghai")).toInstant());
        InternshipConfirmationRecord newRecord = pendingRecord(7L, 200L, 20L, 70L);
        InternshipConfirmationRecord oldRecord = pendingRecord(7L, 100L, 10L, 60L);
        oldRecord.setStatus(1);
        oldRecord.setRecallStatus(0);
        oldRecord.setInternshipStartTime(LocalDateTime.of(2026, 6, 1, 9, 0));

        InternshipStageAssignment pending = assignment(90L, 7L, 200L, 20L,
                "PENDING_CONFIRMATION", 1, 70L);
        InternshipStageAssignment current = assignment(80L, 7L, 100L, 10L,
                "IN_PROGRESS", 1, 60L);
        current.setInternshipStartTime(Date.from(LocalDateTime.of(2026, 6, 1, 9, 0)
                .atZone(ZoneId.of("Asia/Shanghai")).toInstant()));

        StudentInternshipStatus status = new StudentInternshipStatus();
        status.setId(5L);
        status.setStudentId(7L);
        status.setStatus(3);
        status.setCompanyId(100L);
        status.setPositionId(10L);
        status.setRecallStatus(2);
        status.setRecallReason("旧撤回原因");
        status.setIsDelayed(true);
        status.setIsInterrupted(true);

        Position oldPosition = position(10L, 100L, 0, 1);
        Position newPosition = position(20L, 200L, 2, 0);
        when(confirmationMapper.findById(70L)).thenReturn(newRecord);
        when(confirmationMapper.lockStudent(7L)).thenReturn(7L);
        when(confirmationMapper.findByIdForUpdate(70L)).thenReturn(newRecord);
        when(confirmationMapper.findByIdForUpdate(60L)).thenReturn(oldRecord);
        when(assignmentMapper.findByConfirmationRecordIdForUpdate(70L)).thenReturn(pending);
        when(assignmentMapper.findCurrentByStudentForUpdate(7L)).thenReturn(List.of(pending, current));
        when(statusMapper.findByStudentId(7L)).thenReturn(status);
        when(positionMapper.findByIdForUpdate(10L)).thenReturn(oldPosition);
        when(positionMapper.findByIdForUpdate(20L)).thenReturn(newPosition);
        when(assignmentMapper.update(any(InternshipStageAssignment.class))).thenReturn(1);
        when(assignmentMapper.markQuotaReleased(80L, "POSITION_SWITCH")).thenReturn(1);
        when(confirmationMapper.update(any(InternshipConfirmationRecord.class))).thenReturn(1);
        when(confirmationMapper.resolvePending(70L, 1, null)).thenReturn(1);
        when(positionMapper.update(any(Position.class))).thenReturn(1);
        when(statusMapper.updateCurrentPlacement(status)).thenReturn(1);

        InternshipConfirmationWorkflowService.ConfirmationOutcome outcome =
                service.confirm(200L, 70L, confirmedAt);

        assertEquals(10L, outcome.oldPositionId());
        assertEquals(20L, outcome.newPositionId());
        assertEquals("REPLACED", current.getStatus());
        assertEquals(0, current.getCurrentFlag());
        assertEquals(confirmedAt, current.getInternshipEndTime());
        assertEquals("IN_PROGRESS", pending.getStatus());
        assertEquals(1, pending.getStageNo());
        assertEquals(1, pending.getCurrentFlag());
        assertEquals(confirmedAt, pending.getInternshipStartTime());
        assertNull(pending.getInternshipEndTime());
        assertEquals(1, oldPosition.getRemainingQuota());
        assertEquals(0, oldPosition.getRecruitedCount());
        assertEquals(1, newPosition.getRemainingQuota());
        assertEquals(1, newPosition.getRecruitedCount());
        assertEquals(200L, status.getCompanyId());
        assertEquals(20L, status.getPositionId());
        assertEquals(3, status.getStatus());
        assertEquals(0, status.getRecallStatus());
        assertNull(status.getRecallReason());
        assertEquals(false, status.getIsDelayed());
        assertEquals(false, status.getIsInterrupted());
        assertEquals(confirmedAt, status.getInternshipStartTime());
        assertNull(status.getInternshipEndTime());
        assertNull(newRecord.getInternshipEndTime());
        assertNull(newRecord.getInternshipDuration());
        verify(statusCache).evictCache(7L);

        ArgumentCaptor<InternshipConfirmationResultNotification> emailNotification =
                ArgumentCaptor.forClass(InternshipConfirmationResultNotification.class);
        verify(confirmationResultNotifier).afterCommit(emailNotification.capture());
        assertEquals(70L, emailNotification.getValue().recordId());
        assertEquals(7L, emailNotification.getValue().studentId());
        assertEquals(InternshipConfirmationResultNotification.CONFIRMED,
                emailNotification.getValue().status());

        InOrder positionLocks = inOrder(positionMapper);
        positionLocks.verify(positionMapper).findByIdForUpdate(10L);
        positionLocks.verify(positionMapper).findByIdForUpdate(20L);
    }

    @Test
    void fullNewPositionStopsBeforeClosingExistingPlacement() {
        InternshipConfirmationRecord record = pendingRecord(7L, 200L, 20L, 70L);
        InternshipStageAssignment pending = assignment(90L, 7L, 200L, 20L,
                "PENDING_CONFIRMATION", 0, 70L);
        InternshipStageAssignment current = assignment(80L, 7L, 100L, 10L,
                "IN_PROGRESS", 1, 60L);
        StudentInternshipStatus status = new StudentInternshipStatus();
        status.setId(5L);
        status.setStudentId(7L);
        status.setStatus(3);
        status.setCompanyId(100L);
        status.setPositionId(10L);
        when(confirmationMapper.findById(70L)).thenReturn(record);
        when(confirmationMapper.lockStudent(7L)).thenReturn(7L);
        when(confirmationMapper.findByIdForUpdate(70L)).thenReturn(record);
        when(assignmentMapper.findByConfirmationRecordIdForUpdate(70L)).thenReturn(pending);
        when(assignmentMapper.findCurrentByStudentForUpdate(7L)).thenReturn(List.of(current));
        when(statusMapper.findByStudentId(7L)).thenReturn(status);
        when(positionMapper.findByIdForUpdate(10L)).thenReturn(position(10L, 100L, 0, 1));
        when(positionMapper.findByIdForUpdate(20L)).thenReturn(position(20L, 200L, 0, 0));

        assertThrows(BusinessException.class, () -> service.confirm(200L, 70L, new Date()));

        assertEquals("IN_PROGRESS", current.getStatus());
        assertEquals(1, current.getCurrentFlag());
        verify(assignmentMapper, never()).update(any());
        verify(positionMapper, never()).update(any());
        verify(statusMapper, never()).updateCurrentPlacement(any());
    }

    @Test
    void rejectionAndWithdrawalDoNotReplaceCurrentStatusOrConsumeQuota() {
        InternshipConfirmationRecord rejected = pendingRecord(7L, 200L, 20L, 70L);
        InternshipStageAssignment rejectedPlacement = assignment(90L, 7L, 200L, 20L,
                "PENDING_CONFIRMATION", 0, 70L);
        when(confirmationMapper.findById(70L)).thenReturn(rejected);
        when(confirmationMapper.lockStudent(7L)).thenReturn(7L);
        when(confirmationMapper.findByIdForUpdate(70L)).thenReturn(rejected);
        when(confirmationMapper.resolvePending(70L, 2, "不通过")).thenReturn(1);
        when(assignmentMapper.findByConfirmationRecordIdForUpdate(70L)).thenReturn(rejectedPlacement);
        when(assignmentMapper.update(rejectedPlacement)).thenReturn(1);

        service.reject(200L, 70L, "不通过");

        assertEquals("REJECTED", rejectedPlacement.getStatus());
        verify(statusMapper, never()).updateCurrentPlacement(any());
        verify(positionMapper, never()).update(any());

        ArgumentCaptor<InternshipConfirmationResultNotification> emailNotification =
                ArgumentCaptor.forClass(InternshipConfirmationResultNotification.class);
        verify(confirmationResultNotifier).afterCommit(emailNotification.capture());
        assertEquals(70L, emailNotification.getValue().recordId());
        assertEquals(InternshipConfirmationResultNotification.REJECTED,
                emailNotification.getValue().status());
        assertEquals("不通过", emailNotification.getValue().rejectReason());

        InternshipConfirmationRecord withdrawn = pendingRecord(7L, 201L, 21L, 71L);
        InternshipStageAssignment withdrawnPlacement = assignment(91L, 7L, 201L, 21L,
                "PENDING_CONFIRMATION", 0, 71L);
        when(confirmationMapper.findById(71L)).thenReturn(withdrawn);
        when(confirmationMapper.findByIdForUpdate(71L)).thenReturn(withdrawn);
        when(confirmationMapper.recallPending(71L, "自行撤回")).thenReturn(1);
        when(assignmentMapper.findByConfirmationRecordIdForUpdate(71L)).thenReturn(withdrawnPlacement);
        when(assignmentMapper.update(withdrawnPlacement)).thenReturn(1);

        service.withdraw(7L, 71L, "自行撤回");

        assertEquals("WITHDRAWN", withdrawnPlacement.getStatus());
        verify(statusMapper, never()).insert(any());
        verify(positionMapper, never()).findByIdForUpdate(any());
        // 学生撤回不触发结果邮件，仅企业确认/拒绝各一次
        verify(confirmationResultNotifier, org.mockito.Mockito.times(1)).afterCommit(any());
    }

    private InternshipConfirmationRecord pendingRecord(Long studentId, Long companyId,
                                                        Long positionId, Long id) {
        InternshipConfirmationRecord record = new InternshipConfirmationRecord();
        record.setId(id);
        record.setStudentId(studentId);
        record.setStudentName("学生甲");
        record.setCompanyId(companyId);
        record.setCompanyName("企业" + companyId);
        record.setPositionId(positionId);
        record.setPositionName("岗位" + positionId);
        record.setStatus(0);
        record.setRecallStatus(0);
        return record;
    }

    private InternshipStageAssignment assignment(Long id, Long studentId, Long companyId,
                                                   Long positionId, String status, Integer currentFlag,
                                                   Long confirmationRecordId) {
        InternshipStageAssignment assignment = new InternshipStageAssignment();
        assignment.setId(id);
        assignment.setStudentId(studentId);
        assignment.setCompanyId(companyId);
        assignment.setPositionId(positionId);
        assignment.setStatus(status);
        assignment.setCurrentFlag(currentFlag);
        assignment.setConfirmationRecordId(confirmationRecordId);
        return assignment;
    }

    private Position position(Long id, Long companyId, int remainingQuota, int recruitedCount) {
        Position position = new Position();
        position.setId(id);
        position.setCompanyId(companyId);
        position.setRemainingQuota(remainingQuota);
        position.setRecruitedCount(recruitedCount);
        return position;
    }
}
