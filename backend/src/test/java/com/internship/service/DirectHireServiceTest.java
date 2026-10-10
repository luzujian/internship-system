package com.internship.service;

import com.internship.entity.CompanyUser;
import com.internship.entity.InterviewInvitation;
import com.internship.entity.InternshipProgressRecord;
import com.internship.entity.Position;
import com.internship.entity.StudentInternshipStatus;
import com.internship.entity.StudentJobApplication;
import com.internship.entity.dto.StudentPositionKey;
import com.internship.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DirectHireServiceTest {

    @Mock
    private StudentJobApplicationService applicationService;
    @Mock
    private PositionService positionService;
    @Mock
    private InterviewInvitationService invitationService;
    @Mock
    private InternshipProgressRecordService progressRecordService;
    @Mock
    private StudentInternshipStatusService internshipStatusService;
    @Mock
    private CompanyUserService companyUserService;

    private DirectHireService directHireService;

    @BeforeEach
    void setUp() {
        directHireService = new DirectHireService(
                applicationService,
                positionService,
                invitationService,
                progressRecordService,
                companyUserService);
    }

    @Test
    void directHireCreatesPassedExemptInterviewAndProgress() {
        StudentJobApplication application = pendingApplication();
        Position position = position();
        CompanyUser company = company();

        when(applicationService.findById(100L)).thenReturn(application);
        when(positionService.findById(300L)).thenReturn(position);
        when(companyUserService.findById(200L)).thenReturn(company);
        when(invitationService.findByStudentAndPosition(400L, 300L)).thenReturn(null);
        when(applicationService.claimPendingForDirectHire(100L, 200L)).thenReturn(1);
        doAnswer(invocation -> {
            InterviewInvitation invitation = invocation.getArgument(0);
            invitation.setId(500L);
            return null;
        }).when(invitationService).create(any(InterviewInvitation.class));

        DirectHireService.DirectHireResult result = directHireService.directHire(200L, 100L);

        assertEquals(100L, result.applicationId());
        assertEquals(500L, result.invitationId());
        assertTrue(result.directHire());
        assertEquals("interview_passed", application.getStatus());

        ArgumentCaptor<InterviewInvitation> invitationCaptor = ArgumentCaptor.forClass(InterviewInvitation.class);
        verify(invitationService).create(invitationCaptor.capture());
        InterviewInvitation invitation = invitationCaptor.getValue();
        assertEquals("interview_passed", invitation.getStatus());
        assertEquals("direct_hire", invitation.getInterviewMethod());
        assertEquals("企业直接录取，无需参加面试", invitation.getRemark());

        ArgumentCaptor<InternshipProgressRecord> progressCaptor = ArgumentCaptor.forClass(InternshipProgressRecord.class);
        verify(progressRecordService).saveRecord(progressCaptor.capture());
        InternshipProgressRecord progress = progressCaptor.getValue();
        assertEquals("direct_offer", progress.getEventType());
        assertEquals("收到企业直接录取 Offer", progress.getEventTitle());
        assertEquals("success", progress.getStatus());
        assertEquals(500L, progress.getRelatedId());

        verify(internshipStatusService, never()).insert(any(StudentInternshipStatus.class));
        verify(internshipStatusService, never()).update(any(StudentInternshipStatus.class));

        verify(positionService, never()).update(any(Position.class));
    }

    @Test
    void directHireRejectsApplicationOwnedByAnotherCompany() {
        StudentJobApplication application = pendingApplication();
        when(applicationService.findById(100L)).thenReturn(application);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> directHireService.directHire(999L, 100L));

        assertEquals("无权操作此申请", error.getMessage());
        verify(applicationService, never()).update(any(StudentJobApplication.class));
        verify(invitationService, never()).create(any(InterviewInvitation.class));
    }

    @Test
    void directHireOnlyAcceptsPendingApplications() {
        StudentJobApplication application = pendingApplication();
        application.setStatus("approved");
        when(applicationService.findById(100L)).thenReturn(application);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> directHireService.directHire(200L, 100L));

        assertEquals("只有待审核申请可以直接录取", error.getMessage());
        verify(applicationService, never()).update(any(StudentJobApplication.class));
        verify(invitationService, never()).create(any(InterviewInvitation.class));
    }

    @Test
    void directHireStopsWhenAnotherRequestAlreadyClaimedApplication() {
        StudentJobApplication application = pendingApplication();
        when(applicationService.findById(100L)).thenReturn(application);
        when(positionService.findById(300L)).thenReturn(position());
        when(companyUserService.findById(200L)).thenReturn(company());
        when(invitationService.findByStudentAndPosition(400L, 300L)).thenReturn(null);
        when(applicationService.claimPendingForDirectHire(100L, 200L)).thenReturn(0);

        assertThrows(BusinessException.class, () -> directHireService.directHire(200L, 100L));

        verify(invitationService, never()).create(any(InterviewInvitation.class));
        verify(progressRecordService, never()).saveRecord(any(InternshipProgressRecord.class));
    }

    @Test
    void directHireKeepsConfirmableOfferAsActive() {
        InterviewInvitation existing = new InterviewInvitation();
        existing.setStatus("interview_passed");
        when(applicationService.findById(100L)).thenReturn(pendingApplication());
        when(positionService.findById(300L)).thenReturn(position());
        when(invitationService.findByStudentAndPosition(400L, 300L)).thenReturn(existing);

        assertThrows(BusinessException.class, () -> directHireService.directHire(200L, 100L));

        verify(applicationService, never()).claimPendingForDirectHire(100L, 200L);
        verify(invitationService, never()).create(any());
    }

    @Test
    void directHireCanCreateNewOfferAfterFailedInterview() {
        InterviewInvitation failed = new InterviewInvitation();
        failed.setStatus("interview_failed");
        when(applicationService.findById(100L)).thenReturn(pendingApplication());
        when(positionService.findById(300L)).thenReturn(position());
        when(companyUserService.findById(200L)).thenReturn(company());
        when(invitationService.findByStudentAndPosition(400L, 300L)).thenReturn(failed);
        when(applicationService.claimPendingForDirectHire(100L, 200L)).thenReturn(1);
        doAnswer(invocation -> {
            InterviewInvitation invitation = invocation.getArgument(0);
            invitation.setId(501L);
            return null;
        }).when(invitationService).create(any(InterviewInvitation.class));

        DirectHireService.DirectHireResult result = directHireService.directHire(200L, 100L);

        assertEquals(501L, result.invitationId());
        verify(invitationService).create(any(InterviewInvitation.class));
    }

    @Test
    void directHireDoesNotOverwriteExistingInternshipStatus() {
        StudentJobApplication application = pendingApplication();
        StudentInternshipStatus existingStatus = new StudentInternshipStatus();
        existingStatus.setId(600L);
        existingStatus.setStudentId(400L);
        existingStatus.setStatus(1);
        existingStatus.setCompanyConfirmStatus(0);
        existingStatus.setCompanyId(201L);
        existingStatus.setPositionId(301L);

        when(applicationService.findById(100L)).thenReturn(application);
        when(positionService.findById(300L)).thenReturn(position());
        when(companyUserService.findById(200L)).thenReturn(company());
        when(invitationService.findByStudentAndPosition(400L, 300L)).thenReturn(null);
        when(applicationService.claimPendingForDirectHire(100L, 200L)).thenReturn(1);
        doAnswer(invocation -> {
            InterviewInvitation invitation = invocation.getArgument(0);
            invitation.setId(500L);
            return null;
        }).when(invitationService).create(any(InterviewInvitation.class));

        directHireService.directHire(200L, 100L);

        assertEquals(201L, existingStatus.getCompanyId());
        assertEquals(301L, existingStatus.getPositionId());
        assertEquals(1, existingStatus.getStatus());
        assertEquals(0, existingStatus.getCompanyConfirmStatus());
        verify(internshipStatusService, never()).update(existingStatus);
        verify(internshipStatusService, never()).insert(any(StudentInternshipStatus.class));
    }

    @Test
    void isDirectHireRecognizesInvitationMarker() {
        InterviewInvitation invitation = new InterviewInvitation();
        invitation.setStudentId(400L);
        invitation.setPositionId(300L);
        invitation.setInterviewMethod("direct_hire");
        when(invitationService.findByStudentAndPosition(400L, 300L)).thenReturn(invitation);

        assertTrue(directHireService.isDirectHire(400L, 300L));
    }

    @Test
    void batchLookupDeduplicatesPairsAndRetainsFalseForMissingOrNonDirectInvitations() {
        StudentPositionKey direct = new StudentPositionKey(400L, 300L);
        StudentPositionKey normal = new StudentPositionKey(400L, 301L);
        StudentPositionKey missing = new StudentPositionKey(400L, 302L);
        StudentPositionKey samePositionAnotherStudent = new StudentPositionKey(401L, 300L);
        List<StudentPositionKey> expectedLookup = List.of(direct, normal, missing, samePositionAnotherStudent);
        when(invitationService.findLatestForStudentPositions(expectedLookup)).thenReturn(List.of(
                invitationFor(direct, "direct_hire"),
                invitationFor(normal, "online"),
                invitationFor(missing, null),
                invitationFor(samePositionAnotherStudent, "DIRECT_HIRE")));

        Set<StudentPositionKey> result = directHireService.findDirectHirePairs(Arrays.asList(
                direct, direct, normal, missing, samePositionAnotherStudent,
                new StudentPositionKey(null, 300L), new StudentPositionKey(400L, null), null));

        assertEquals(Set.of(direct), result);
        verify(invitationService).findLatestForStudentPositions(expectedLookup);
        verify(invitationService, never()).findByStudentAndPosition(any(), any());
    }

    @Test
    void batchLookupDoesNotQueryForEmptyOrOnlyNullKeys() {
        assertTrue(directHireService.findDirectHirePairs(List.of()).isEmpty());
        assertTrue(directHireService.findDirectHirePairs(Arrays.asList(
                null, new StudentPositionKey(null, 1L), new StudentPositionKey(1L, null))).isEmpty());

        verifyNoInteractions(invitationService);
    }

    @Test
    void batchLookupBoundsQueriesAndCombinesAllResults() {
        List<StudentPositionKey> requested = LongStream.rangeClosed(1, 401)
                .mapToObj(id -> new StudentPositionKey(id, 300L)).toList();
        List<Integer> batchSizes = new ArrayList<>();
        when(invitationService.findLatestForStudentPositions(anyList())).thenAnswer(invocation -> {
            List<StudentPositionKey> batch = invocation.getArgument(0);
            batchSizes.add(batch.size());
            return batch.stream().map(pair -> invitationFor(pair, "direct_hire")).toList();
        });

        assertEquals(Set.copyOf(requested), directHireService.findDirectHirePairs(requested));
        assertEquals(List.of(200, 200, 1), batchSizes);
        verify(invitationService, times(3)).findLatestForStudentPositions(anyList());
        verify(invitationService, never()).findByStudentAndPosition(any(), any());
    }

    @Test
    void batchLookupPropagatesReadFailureInsteadOfReturningIncorrectFlags() {
        when(invitationService.findLatestForStudentPositions(anyList()))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(IllegalStateException.class, () -> directHireService.findDirectHirePairs(
                List.of(new StudentPositionKey(400L, 300L))));
    }

    private InterviewInvitation invitationFor(StudentPositionKey pair, String method) {
        InterviewInvitation invitation = new InterviewInvitation();
        invitation.setStudentId(pair.studentId());
        invitation.setPositionId(pair.positionId());
        invitation.setInterviewMethod(method);
        return invitation;
    }

    private StudentJobApplication pendingApplication() {
        StudentJobApplication application = new StudentJobApplication();
        application.setId(100L);
        application.setStudentId(400L);
        application.setStudentName("测试学生");
        application.setStudentNo("20260001");
        application.setPositionId(300L);
        application.setPositionName("Java开发实习生");
        application.setCompanyId(200L);
        application.setCompanyName("测试企业");
        application.setStatus("pending");
        return application;
    }

    private Position position() {
        Position position = new Position();
        position.setId(300L);
        position.setCompanyId(200L);
        position.setPositionName("Java开发实习生");
        position.setStatus("active");
        position.setPlannedRecruit(5);
        position.setRecruitedCount(1);
        position.setRemainingQuota(4);
        return position;
    }

    private CompanyUser company() {
        CompanyUser company = new CompanyUser();
        company.setId(200L);
        company.setCompanyName("测试企业");
        company.setContactPerson("企业联系人");
        company.setContactPhone("13800000000");
        company.setAddress("测试地址");
        return company;
    }
}
