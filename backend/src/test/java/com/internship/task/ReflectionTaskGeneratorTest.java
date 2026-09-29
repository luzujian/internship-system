package com.internship.task;

import com.internship.entity.InternshipReflection;
import com.internship.entity.InternshipStageAssignment;
import com.internship.entity.User;
import com.internship.entity.dto.InternshipCalendar;
import com.internship.mapper.InternshipReflectionMapper;
import com.internship.service.ClassCounselorRelationService;
import com.internship.service.CounselorAISettingsService;
import com.internship.service.InternshipCalendarService;
import com.internship.service.InternshipStageAssignmentService;
import com.internship.service.StudentInternshipStatusService;
import com.internship.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReflectionTaskGeneratorTest {
    @Mock private InternshipCalendarService calendarService;
    @Mock private InternshipStageAssignmentService assignmentService;
    @Mock private StudentInternshipStatusService statusService;
    @Mock private ClassCounselorRelationService classCounselorRelationService;
    @Mock private CounselorAISettingsService counselorAISettingsService;
    @Mock private InternshipReflectionMapper reflectionMapper;
    @Mock private UserService userService;

    @InjectMocks private ReflectionTaskGenerator generator;

    @Test
    void switchWithinOneGlobalPeriodCreatesOnlyOneTaskForTheLatestPlacement() {
        LocalDate today = LocalDate.now(ReflectionTaskGenerator.BUSINESS_ZONE);
        InternshipCalendar calendar = new InternshipCalendar();
        calendar.setStartDate(today.minusDays(10).toString());
        calendar.setEndDate(today.plusDays(20).toString());
        calendar.setReportCycle(7);
        when(calendarService.getCalendar()).thenReturn(calendar);

        InternshipStageAssignment oldPlacement = placement(1L, 42L, today.minusDays(10), today.minusDays(1));
        InternshipStageAssignment newPlacement = placement(2L, 42L, today.minusDays(1), today.plusDays(20));
        when(assignmentService.findAll()).thenReturn(List.of(oldPlacement, newPlacement));
        when(classCounselorRelationService.findCounselorIdByStudentId(42L)).thenReturn(9L);
        when(counselorAISettingsService.isAiScoringEnabled(9L)).thenReturn(true);
        when(reflectionMapper.existsByStudentIdAndPeriodNumber(42L, 2)).thenReturn(false);
        User student = new User();
        student.setId(42L);
        student.setName("测试学生");
        student.setUsername("s42");
        when(userService.findById(42L)).thenReturn(student);
        when(reflectionMapper.insert(any())).thenReturn(1);

        generator.generateReflectionTasks();

        ArgumentCaptor<InternshipReflection> captor = ArgumentCaptor.forClass(InternshipReflection.class);
        verify(reflectionMapper).insert(captor.capture());
        assertEquals(2, captor.getValue().getPeriodNumber());
        assertEquals(2L, captor.getValue().getStageAssignmentId());
    }

    @Test
    void existingCurrentPeriodTaskIsNeverDuplicated() {
        LocalDate today = LocalDate.now(ReflectionTaskGenerator.BUSINESS_ZONE);
        InternshipCalendar calendar = new InternshipCalendar();
        calendar.setStartDate(today.minusDays(1).toString());
        calendar.setEndDate(today.plusDays(20).toString());
        calendar.setReportCycle(7);
        when(calendarService.getCalendar()).thenReturn(calendar);
        when(assignmentService.findByStudentId(42L)).thenReturn(List.of(
                placement(1L, 42L, today.minusDays(1), today.plusDays(20))));
        when(reflectionMapper.existsByStudentIdAndPeriodNumber(42L, 1)).thenReturn(true);

        generator.generateNextTask(42L, 9L);

        verify(reflectionMapper, never()).insert(any());
    }

    private InternshipStageAssignment placement(Long id, Long studentId, LocalDate start, LocalDate end) {
        InternshipStageAssignment assignment = new InternshipStageAssignment();
        assignment.setId(id);
        assignment.setStudentId(studentId);
        assignment.setStatus("CONFIRMED");
        assignment.setInternshipStartTime(Timestamp.valueOf(start.atStartOfDay()));
        assignment.setInternshipEndTime(Timestamp.valueOf(end.atStartOfDay()));
        return assignment;
    }
}
