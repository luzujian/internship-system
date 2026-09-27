package com.internship.service;

import com.internship.entity.StudentInternshipStatus;
import com.internship.entity.dto.JobSearchStatusDTO;
import com.internship.entity.dto.JobSearchStatusEvidenceDTO;
import com.internship.mapper.JobSearchStatusMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JobSearchStatusServiceTest {
    private JobSearchStatusMapper mapper;
    private JobSearchStatusService service;

    @BeforeEach
    void setUp() {
        mapper = mock(JobSearchStatusMapper.class);
        service = new JobSearchStatusService(mapper);
    }

    @Test
    void noActiveApplicationOrInterviewIsUnapplied() {
        when(mapper.findEvidenceByStudentIds(anyCollection()))
                .thenReturn(List.of(evidence(1L, 0, false, false, false)));

        JobSearchStatusDTO result = service.resolve(1L, 0);

        assertEquals("NOT_FOUND", result.jobSearchStatus());
        assertEquals("未投递", result.jobSearchStatusText());
    }

    @Test
    void pendingApplicationOrActiveInterviewWithoutPassedOfferIsNoOffer() {
        when(mapper.findEvidenceByStudentIds(anyCollection()))
                .thenReturn(List.of(evidence(2L, 0, true, false, false)));

        JobSearchStatusDTO result = service.resolve(2L, 0);

        assertEquals("NO_OFFER", result.jobSearchStatus());
        assertEquals("无 Offer", result.jobSearchStatusText());
    }

    @Test
    void passedInterviewOrPendingStageIsPendingConfirmation() {
        when(mapper.findEvidenceByStudentIds(anyCollection()))
                .thenReturn(List.of(evidence(3L, 0, true, true, false)));

        JobSearchStatusDTO result = service.resolve(3L, 0);

        assertEquals("PENDING_CONFIRMATION", result.jobSearchStatus());
        assertEquals("待确认", result.jobSearchStatusText());
    }

    @Test
    void establishedInternshipStatusHasPriorityOverOfferEvidence() {
        when(mapper.findEvidenceByStudentIds(anyCollection()))
                .thenReturn(List.of(evidence(4L, 5, true, true, true)));

        JobSearchStatusDTO result = service.resolve(4L, 5);

        assertEquals("INTERRUPTED", result.jobSearchStatus());
        assertEquals("已中断", result.jobSearchStatusText());
    }

    @Test
    void batchEnrichmentKeepsNumericInternshipStatusAndAddsDisplayFields() {
        StudentInternshipStatus status = new StudentInternshipStatus();
        status.setStudentId(5L);
        status.setStatus(0);
        when(mapper.findEvidenceByStudentIds(anyCollection()))
                .thenReturn(List.of(evidence(5L, 0, true, false, false)));

        service.enrich(List.of(status));

        assertEquals(0, status.getStatus());
        assertEquals(0, status.getInternshipStatus());
        assertEquals("NO_OFFER", status.getJobSearchStatus());
        assertEquals("无 Offer", status.getJobSearchStatusText());
    }

    private JobSearchStatusEvidenceDTO evidence(Long studentId,
                                                Integer internshipStatus,
                                                boolean opportunity,
                                                boolean passed,
                                                boolean pendingConfirmation) {
        JobSearchStatusEvidenceDTO evidence = new JobSearchStatusEvidenceDTO();
        evidence.setStudentId(studentId);
        evidence.setInternshipStatus(internshipStatus);
        evidence.setHasInterviewOpportunity(opportunity);
        evidence.setHasPassedOffer(passed);
        evidence.setHasPendingConfirmation(pendingConfirmation);
        return evidence;
    }
}
