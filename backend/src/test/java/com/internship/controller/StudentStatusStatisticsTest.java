package com.internship.controller;

import com.internship.entity.Division;
import com.internship.entity.Major;
import com.internship.entity.StudentUser;
import com.internship.entity.StudentInternshipStatus;
import com.internship.entity.dto.JobSearchStatusDTO;
import com.internship.mapper.DivisionMapper;
import com.internship.mapper.MajorMapper;
import com.internship.mapper.StudentUserMapper;
import com.internship.mapper.StudentInternshipStatusMapper;
import com.internship.service.JobSearchStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class StudentStatusStatisticsTest {
    @Test
    void departmentDistributionUsesDerivedStatesAndDivisionFallbackWithoutDisabledStudents() {
        StatisticsController controller = new StatisticsController();
        StudentUserMapper students = mock(StudentUserMapper.class);
        MajorMapper majors = mock(MajorMapper.class);
        DivisionMapper divisions = mock(DivisionMapper.class);
        JobSearchStatusService statusService = mock(JobSearchStatusService.class);
        StudentInternshipStatusMapper internshipStatuses = mock(StudentInternshipStatusMapper.class);
        ReflectionTestUtils.setField(controller, "studentUserMapper", students);
        ReflectionTestUtils.setField(controller, "majorMapper", majors);
        ReflectionTestUtils.setField(controller, "divisionMapper", divisions);
        ReflectionTestUtils.setField(controller, "jobSearchStatusService", statusService);
        ReflectionTestUtils.setField(controller, "internshipStatusMapper", internshipStatuses);

        Major major = new Major();
        major.setId(10L);
        major.setDivisionId(20L);
        Division division = new Division();
        division.setId(20L);
        division.setDepartmentId(30L);
        when(majors.findAll()).thenReturn(List.of(major));
        when(divisions.findAll()).thenReturn(List.of(division));
        StudentUser active = new StudentUser();
        active.setId(1L); active.setMajorId(10L); active.setClassId(40L); active.setStatus(1);
        StudentUser disabled = new StudentUser();
        disabled.setId(2L); disabled.setMajorId(10L); disabled.setClassId(40L); disabled.setStatus(0);
        when(students.findAll()).thenReturn(List.of(active, disabled));
        when(statusService.resolveForStudentIds(List.of(1L))).thenReturn(Map.of(
                1L, new JobSearchStatusDTO("PENDING_CONFIRMATION", "已录用待确认")));

        List<Map<String, Object>> distribution = ReflectionTestUtils.invokeMethod(controller,
                "getStatusDistribution", "30", "10", "40");
        assertEquals(8, distribution.size());
        assertEquals(1L, distribution.stream().filter(row -> "已录用待确认".equals(row.get("name")))
                .findFirst().orElseThrow().get("value"));
        assertEquals(0L, distribution.stream().filter(row -> "尚未获录用".equals(row.get("name")))
                .count());
        verify(statusService).resolveForStudentIds(List.of(1L));

        StudentInternshipStatus delayed = new StudentInternshipStatus();
        delayed.setStudentId(1L); delayed.setStatus(6); delayed.setIsDelayed(false);
        StudentInternshipStatus outsideScope = new StudentInternshipStatus();
        outsideScope.setStudentId(2L); outsideScope.setStatus(5); outsideScope.setHasComplaint(true);
        when(internshipStatuses.selectAll()).thenReturn(List.of(delayed, outsideScope));
        List<Map<String, Object>> abnormal = ReflectionTestUtils.invokeMethod(controller,
                "getAbnormalStatusAnalysis", "30", "10", "40");
        assertEquals(1, abnormal.stream().filter(row -> "已延期实习".equals(row.get("name")))
                .findFirst().orElseThrow().get("count"));
        assertEquals(0, abnormal.stream().filter(row -> "实习已中断".equals(row.get("name")))
                .findFirst().orElseThrow().get("count"));
        assertEquals(0, abnormal.stream().filter(row -> "投诉".equals(row.get("name")))
                .findFirst().orElseThrow().get("count"));
    }
}
