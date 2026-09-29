package com.internship.service.impl;

import com.internship.entity.InternshipStageAssignment;
import com.internship.exception.BusinessException;
import com.internship.mapper.InternshipStageAssignmentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternshipStageAssignmentContinuationTest {
    @Mock private InternshipStageAssignmentMapper mapper;

    private InternshipStageAssignmentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InternshipStageAssignmentServiceImpl(mapper);
    }

    @Test
    void stageTwoContinuationAndChangeAreRetired() {
        assertThrows(BusinessException.class,
                () -> service.createAutoContinuedAssignment(7L, new Date()));
        assertThrows(BusinessException.class,
                () -> service.beginStageTwoChange(7L, "更换单位"));
    }

    @Test
    void newPlacementUsesStageOneAndNextSequenceWithoutBecomingCurrent() {
        InternshipStageAssignment request = new InternshipStageAssignment();
        request.setStudentId(7L);
        request.setStageNo(2);
        request.setCompanyId(21L);
        request.setPositionId(31L);

        when(mapper.findPendingByStudentAndPosition(7L, 21L, 31L)).thenReturn(null);
        when(mapper.countByStudent(7L)).thenReturn(3);
        doAnswer(invocation -> {
            InternshipStageAssignment saved = invocation.getArgument(0);
            saved.setId(51L);
            return 1;
        }).when(mapper).insert(any(InternshipStageAssignment.class));

        InternshipStageAssignment saved = service.createFromApplication(request);

        assertEquals(1, saved.getStageNo());
        assertEquals(4, saved.getSequenceNo());
        assertEquals(0, saved.getCurrentFlag());
        assertEquals("PENDING_CONFIRMATION", saved.getStatus());
        ArgumentCaptor<InternshipStageAssignment> captor =
                ArgumentCaptor.forClass(InternshipStageAssignment.class);
        verify(mapper).insert(captor.capture());
        assertEquals(saved, captor.getValue());
    }
}
