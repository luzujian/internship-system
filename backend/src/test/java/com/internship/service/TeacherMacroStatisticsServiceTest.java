package com.internship.service;

import com.internship.entity.Division;
import com.internship.entity.TeacherUser;
import com.internship.entity.dto.TeacherMacroStatisticsDTO;
import com.internship.exception.BusinessException;
import com.internship.mapper.DivisionMapper;
import com.internship.mapper.TeacherMacroStatisticsMapper;
import com.internship.mapper.TeacherUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeacherMacroStatisticsServiceTest {
    private JobSearchStatusService jobSearchStatusService;
    private TeacherMacroStatisticsMapper statisticsMapper;
    private TeacherUserMapper teacherUserMapper;
    private DivisionMapper divisionMapper;
    private TeacherMacroStatisticsService service;

    @BeforeEach
    void setUp() {
        jobSearchStatusService = mock(JobSearchStatusService.class);
        statisticsMapper = mock(TeacherMacroStatisticsMapper.class);
        teacherUserMapper = mock(TeacherUserMapper.class);
        divisionMapper = mock(DivisionMapper.class);
        service = new TeacherMacroStatisticsService(
                jobSearchStatusService, statisticsMapper, teacherUserMapper, divisionMapper);
    }

    @Test
    void collegeTeacherCountsWithoutOfferStudentsInOwnCollege() {
        TeacherUser teacher = teacher("COLLEGE", "10", null);
        when(teacherUserMapper.findById(7L)).thenReturn(teacher);
        when(jobSearchStatusService.countWithoutOfferByDepartmentId(10L)).thenReturn(12L);
        when(statisticsMapper.sumActivePositionRemainingQuota()).thenReturn(37L);

        TeacherMacroStatisticsDTO result = service.getStatistics(7L);

        assertEquals(12L, result.getWithoutOfferStudentCount());
        assertEquals(37L, result.getTotalRemainingQuota());
    }

    @Test
    void departmentDirectorResolvesCollegeThroughDivision() {
        TeacherUser teacher = teacher("DEPARTMENT", "10", "20");
        Division division = new Division();
        division.setId(20L);
        division.setDepartmentId(10L);
        when(teacherUserMapper.findById(8L)).thenReturn(teacher);
        when(divisionMapper.findById(20L)).thenReturn(division);
        when(jobSearchStatusService.countWithoutOfferByDepartmentId(10L)).thenReturn(5L);

        TeacherMacroStatisticsDTO result = service.getStatistics(8L);

        assertEquals(5L, result.getWithoutOfferStudentCount());
        verify(jobSearchStatusService).countWithoutOfferByDepartmentId(10L);
    }

    @Test
    void departmentDirectorWithoutDivisionUsesConfiguredCollege() {
        when(teacherUserMapper.findById(8L))
                .thenReturn(teacher("DEPARTMENT", "10", null));
        when(jobSearchStatusService.countWithoutOfferByDepartmentId(10L)).thenReturn(6L);

        TeacherMacroStatisticsDTO result = service.getStatistics(8L);

        assertEquals(6L, result.getWithoutOfferStudentCount());
        verify(divisionMapper, never()).findById(org.mockito.ArgumentMatchers.anyLong());
        verify(jobSearchStatusService).countWithoutOfferByDepartmentId(10L);
    }

    @Test
    void counselorUsesCollegeInsteadOfResponsibleClassScope() {
        when(teacherUserMapper.findById(9L))
                .thenReturn(teacher("COUNSELOR", "10", null));
        when(jobSearchStatusService.countWithoutOfferByDepartmentId(10L)).thenReturn(9L);

        TeacherMacroStatisticsDTO result = service.getStatistics(9L);

        assertEquals(9L, result.getWithoutOfferStudentCount());
        verify(jobSearchStatusService).countWithoutOfferByDepartmentId(10L);
    }

    @Test
    void inconsistentDepartmentConfigurationFailsClosed() {
        TeacherUser teacher = teacher("DEPARTMENT", "11", "20");
        Division division = new Division();
        division.setId(20L);
        division.setDepartmentId(10L);
        when(teacherUserMapper.findById(8L)).thenReturn(teacher);
        when(divisionMapper.findById(20L)).thenReturn(division);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.getStatistics(8L));

        assertEquals("教师学院与系室所属学院不一致，请联系管理员修正", error.getMessage());
        verify(jobSearchStatusService, never()).countWithoutOfferByDepartmentId(10L);
        verify(statisticsMapper, never()).sumActivePositionRemainingQuota();
    }

    private TeacherUser teacher(String teacherType, String departmentId, String divisionId) {
        TeacherUser teacher = new TeacherUser();
        teacher.setTeacherType(teacherType);
        teacher.setDepartmentId(departmentId);
        teacher.setDivisionId(divisionId);
        return teacher;
    }
}
