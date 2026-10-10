package com.internship.service.impl;

import com.internship.entity.Position;
import com.internship.entity.StudentJobApplication;
import com.internship.exception.BusinessException;
import com.internship.mapper.CompanyUserMapper;
import com.internship.mapper.InternshipApplicationMapper;
import com.internship.mapper.PositionMapper;
import com.internship.mapper.StudentJobApplicationMapper;
import com.internship.service.InternshipFlowRules;
import com.internship.service.InternshipProgressRecordService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentJobApplicationServiceImplTest {
    @Test
    void mirroredSubmissionSharesThePersistedCreationTimestamp() {
        var app = application(31L, 12L);
        var position = position(12L, 2); position.setCompanyId(7L);
        when(positionMapper.findByIdForUpdate(12L)).thenReturn(position);
        when(mapper.insert(app)).thenReturn(1);
        when(internshipApplicationMapper.insert(org.mockito.ArgumentMatchers.any())).thenReturn(1);
        assertEquals(1, service.create(app));
        var mirror = org.mockito.ArgumentCaptor.forClass(com.internship.entity.InternshipApplicationEntity.class);
        verify(internshipApplicationMapper).insert(mirror.capture());
        org.junit.jupiter.api.Assertions.assertNotNull(app.getCreateTime());
        assertEquals(app.getCreateTime(), mirror.getValue().getApplyTime());
        assertEquals(app.getCreateTime(), mirror.getValue().getCreateTime());
        assertEquals(app.getUpdateTime(), mirror.getValue().getUpdateTime());
    }

    @Test
    void companyRoutingComesFromLockedPosition() {
        var app=application(31L,12L); app.setCompanyId(999L); app.setCompanyName("forged");
        var job=position(12L,2); job.setCompanyId(7L); job.setPositionName("real job");
        var company=new com.internship.entity.CompanyUser(); company.setCompanyName("real company");
        when(positionMapper.findByIdForUpdate(12L)).thenReturn(job);
        when(companyUserMapper.findById(7L)).thenReturn(company);
        service.create(app);
        assertEquals(7L,app.getCompanyId()); assertEquals("real company",app.getCompanyName());
        assertEquals("real job",app.getPositionName()); verify(mapper).insert(app);
    }

    @Mock private StudentJobApplicationMapper mapper;
    @Mock private PositionMapper positionMapper;
    @Mock private InternshipApplicationMapper internshipApplicationMapper;
    @Mock private CompanyUserMapper companyUserMapper;
    @Mock private InternshipProgressRecordService progressRecordService;

    @InjectMocks
    private StudentJobApplicationServiceImpl service;

    @Test
    void acceptsApplicationWithoutStageOrDateGateAndNormalizesLegacyFields() {
        StudentJobApplication application = application(31L, null);
        application.setStageNo(2);
        application.setStageAssignmentId(99L);
        application.setSourceType(InternshipFlowRules.SOURCE_CHANGE);
        when(mapper.insert(same(application))).thenReturn(1);

        assertEquals(1, service.create(application));

        assertEquals(1, application.getStageNo());
        assertNull(application.getStageAssignmentId());
        assertEquals(InternshipFlowRules.SOURCE_NEW_APPLICATION, application.getSourceType());
        assertEquals("pending", application.getStatus());
        verify(mapper).insert(same(application));
    }

    @Test
    void rejectsDuplicateWhileLatestApplicationIsActive() {
        StudentJobApplication application = application(32L, 12L);
        Position position = position(12L, 0);
        StudentJobApplication latest = application(32L, 12L);
        latest.setStatus("approved");
        when(positionMapper.findByIdForUpdate(12L)).thenReturn(position);
        when(mapper.findLatestByStudentAndPositionForUpdate(32L, 12L)).thenReturn(latest);

        assertThrows(BusinessException.class, () -> service.create(application));

        verify(mapper, never()).insert(application);
        assertEquals(0, position.getRemainingQuota());
        assertEquals(0, position.getRecruitedCount());
    }

    @ParameterizedTest
    @ValueSource(strings = {"pending_interview", "interview_passed"})
    void rejectsDuplicateWhileLatestApplicationHasInterviewOrConfirmableOffer(String status) {
        StudentJobApplication application = application(35L, 15L);
        StudentJobApplication latest = application(35L, 15L);
        latest.setStatus(status);
        when(positionMapper.findByIdForUpdate(15L)).thenReturn(position(15L, 2));
        when(mapper.findLatestByStudentAndPositionForUpdate(35L, 15L)).thenReturn(latest);

        assertThrows(BusinessException.class, () -> service.create(application));

        verify(mapper, never()).insert(application);
    }

    @ParameterizedTest
    @ValueSource(strings = {"rejected", "interview_failed", "withdrawn"})
    void allowsRetryAfterLatestApplicationReachedTerminalStatus(String status) {
        StudentJobApplication application = application(33L, 13L);
        Position position = position(13L, 0);
        StudentJobApplication latest = application(33L, 13L);
        latest.setStatus(status);
        when(positionMapper.findByIdForUpdate(13L)).thenReturn(position);
        when(mapper.findLatestByStudentAndPositionForUpdate(33L, 13L)).thenReturn(latest);
        when(mapper.insert(same(application))).thenReturn(1);

        assertEquals(1, service.create(application));

        verify(mapper).insert(same(application));
        assertEquals(0, position.getRemainingQuota());
        assertEquals(0, position.getRecruitedCount());
    }

    @Test
    void applicationDoesNotConsumeOrRequireFinalQuota() {
        StudentJobApplication application = application(34L, 14L);
        Position position = position(14L, 0);
        when(positionMapper.findByIdForUpdate(14L)).thenReturn(position);
        when(mapper.insert(same(application))).thenReturn(1);

        assertEquals(1, service.create(application));

        assertEquals(0, position.getRemainingQuota());
        assertEquals(0, position.getRecruitedCount());
    }

    @Test
    void updatesLatestMainRecordForLegacyAuditEntry() {
        when(mapper.updateLatestStatusIfActive(41L, 15L, "rejected", "not matched"))
                .thenReturn(1);

        assertEquals(1, service.updateLatestStatusIfActive(41L, 15L, "rejected", "not matched"));
    }

    @Test
    void withdrawalClosesLatestMirrorRecordWithoutChangingQuota() {
        StudentJobApplication existing = application(42L, 16L);
        existing.setId(101L);
        existing.setStatus("pending");
        when(mapper.findById(101L)).thenReturn(existing);
        when(mapper.deleteById(101L)).thenReturn(1);

        assertEquals(1, service.deleteById(101L, 42L));

        verify(internshipApplicationMapper)
                .updateLatestStatusByStudentAndPosition(42L, 16L, "withdrawn");
        verify(positionMapper, never()).findByIdForUpdate(16L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"approved", "interview_passed", "rejected", "interview_failed", "withdrawn"})
    void withdrawalCannotDeleteProcessedApplicationOrAffectLaterRetry(String status) {
        StudentJobApplication existing = application(42L, 16L);
        existing.setId(101L);
        existing.setStatus(status);
        when(mapper.findById(101L)).thenReturn(existing);

        assertThrows(BusinessException.class, () -> service.deleteById(101L, 42L));

        verify(mapper, never()).deleteById(101L);
        verify(internshipApplicationMapper, never())
                .updateLatestStatusByStudentAndPosition(42L, 16L, "withdrawn");
    }

    @Test
    void withdrawalFailsWhenCompanyProcessesApplicationAfterItWasRead() {
        StudentJobApplication existing = application(42L, 16L);
        existing.setStatus("pending");
        when(mapper.findById(101L)).thenReturn(existing);
        when(mapper.deleteById(101L)).thenReturn(0);

        assertThrows(BusinessException.class, () -> service.deleteById(101L, 42L));

        verify(internshipApplicationMapper, never())
                .updateLatestStatusByStudentAndPosition(42L, 16L, "withdrawn");
    }

    private StudentJobApplication application(Long studentId, Long positionId) {
        StudentJobApplication application = new StudentJobApplication();
        application.setStudentId(studentId);
        application.setPositionId(positionId);
        application.setPositionName("测试岗位");
        return application;
    }

    private Position position(Long id, int remainingQuota) {
        Position position = new Position();
        position.setId(id);
        position.setRemainingQuota(remainingQuota);
        position.setRecruitedCount(0);
        return position;
    }
}
