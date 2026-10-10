package com.internship.service.impl;

import com.internship.entity.InternshipStageAssignment;
import com.internship.mapper.InternshipStageAssignmentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternshipStageAssignmentSourceIdentityTest {
    @Mock private InternshipStageAssignmentMapper mapper;
    @InjectMocks private InternshipStageAssignmentServiceImpl service;

    @Test
    void selfPracticeCannotReuseAnUnrelatedJobApplicationWithSameNumericId() {
        InternshipStageAssignment selfPractice = assignment(10L, "SELF_PRACTICE");
        selfPractice.setSourceAssignmentId(50L);
        when(mapper.insert(selfPractice)).thenReturn(1);

        assertSame(selfPractice, service.createFromApplication(selfPractice));

        verify(mapper, never()).findByApplicationId(50L);
        verify(mapper).insert(selfPractice);
    }

    @Test
    void jobApplicationCannotReuseSelfPracticeWithSameNumericId() {
        InternshipStageAssignment job = assignment(10L, "NEW_APPLICATION");
        InternshipStageAssignment selfPractice = assignment(10L, "SELF_PRACTICE");
        when(mapper.findByApplicationId(50L)).thenReturn(selfPractice);
        when(mapper.insert(job)).thenReturn(1);

        assertSame(job, service.createFromApplication(job));

        verify(mapper).insert(job);
    }

    @Test
    void jobApplicationCannotReuseAnotherStudentsPlacement() {
        InternshipStageAssignment job = assignment(10L, "NEW_APPLICATION");
        when(mapper.findByApplicationId(50L)).thenReturn(assignment(20L, "NEW_APPLICATION"));
        when(mapper.insert(job)).thenReturn(1);

        assertSame(job, service.createFromApplication(job));
    }

    @Test
    void duplicateJobApplicationStillReturnsItsExistingPlacement() {
        InternshipStageAssignment job = assignment(10L, "NEW_APPLICATION");
        InternshipStageAssignment existing = assignment(10L, "NEW_APPLICATION");
        when(mapper.findByApplicationId(50L)).thenReturn(existing);

        assertSame(existing, service.createFromApplication(job));

        verify(mapper, never()).insert(any());
    }

    private InternshipStageAssignment assignment(Long studentId, String sourceType) {
        InternshipStageAssignment assignment = new InternshipStageAssignment();
        assignment.setStudentId(studentId);
        assignment.setApplicationId(50L);
        assignment.setSourceType(sourceType);
        return assignment;
    }
}
