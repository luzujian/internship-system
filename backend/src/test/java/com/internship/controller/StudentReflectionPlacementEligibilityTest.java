package com.internship.controller;

import com.internship.entity.InternshipConfirmationRecord;
import com.internship.entity.InternshipStageAssignment;
import com.internship.entity.Result;
import com.internship.entity.User;
import com.internship.service.InternshipConfirmationRecordService;
import com.internship.service.InternshipReflectionService;
import com.internship.service.InternshipStageAssignmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentReflectionPlacementEligibilityTest {
    @Mock private InternshipConfirmationRecordService confirmationRecordService;
    @Mock private InternshipReflectionService internshipReflectionService;
    @Mock private InternshipStageAssignmentService stageAssignmentService;
    @InjectMocks private StudentInternshipReflectionController controller;

    @Test
    void normalInProgressInternshipCanSubmitReflection() {
        assertNull(validate("IN_PROGRESS"));
    }

    @Test
    void pendingPlacementCannotSubmitReflection() {
        assertNotNull(validate("PENDING_CONFIRMATION"));
    }

    private Result validate(String placementStatus) {
        User student = new User();
        student.setId(42L);
        InternshipStageAssignment placement = new InternshipStageAssignment();
        placement.setId(1L);
        placement.setStudentId(42L);
        placement.setStatus(placementStatus);
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        placement.setInternshipStartTime(Date.from(LocalDate.now(zone).minusDays(1).atStartOfDay(zone).toInstant()));
        placement.setInternshipEndTime(Date.from(LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant()));
        when(confirmationRecordService.findByStudentIdAndStatus(42L, 1))
                .thenReturn(List.of(new InternshipConfirmationRecord()));
        when(internshipReflectionService.calculatePeriodNumber(any(Date.class))).thenReturn(1);
        when(stageAssignmentService.findByStudentId(42L)).thenReturn(List.of(placement));
        return ReflectionTestUtils.invokeMethod(controller, "validateCanSubmitReflection", student);
    }
}
