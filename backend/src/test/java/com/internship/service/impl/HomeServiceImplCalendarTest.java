package com.internship.service.impl;

import com.internship.entity.dto.InternshipCalendar;
import com.internship.mapper.AnnouncementMapper;
import com.internship.mapper.CompanyUserMapper;
import com.internship.mapper.StudentApplicationMapper;
import com.internship.mapper.StudentInternshipStatusMapper;
import com.internship.service.CompanyUserService;
import com.internship.service.InternshipCalendarService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class HomeServiceImplCalendarTest {
    @Test
    void homeOmitsDraftAndOtherRoleAnnouncements() {
        var visible=new com.internship.entity.Announcement();visible.setId(1L);visible.setStatus("PUBLISHED");visible.setTargetType("STUDENT");
        var hidden=new com.internship.entity.Announcement();hidden.setId(2L);hidden.setStatus("PUBLISHED");hidden.setTargetType("TEACHER");
        var draft=new com.internship.entity.Announcement();draft.setId(3L);draft.setStatus("DRAFT");draft.setTargetType("ALL");
        when(announcementMapper.findAll()).thenReturn(new ArrayList<>(java.util.List.of(visible,hidden,draft)));
        when(calendarService.getCalendar()).thenReturn(new InternshipCalendar());
        var user=new com.internship.entity.User();user.setId(1L);user.setRole("ROLE_STUDENT");com.internship.utils.CurrentHolder.setUser(user);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"announcementAudiencePolicy",new com.internship.service.AnnouncementAudiencePolicy(org.mockito.Mockito.mock(com.internship.mapper.StudentUserMapper.class)));
        try {
            var result=service.getHomeStats(1L,"STUDENT",null,null);
            org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(1L),result.getAnnouncements().stream().map(com.internship.entity.dto.AnnouncementWithReadStatusDTO::getId).toList());
        } finally {com.internship.utils.CurrentHolder.remove();}
    }

    @Mock private StudentInternshipStatusMapper statusMapper;
    @Mock private StudentApplicationMapper studentApplicationMapper;
    @Mock private CompanyUserMapper companyUserMapper;
    @Mock private AnnouncementMapper announcementMapper;
    @Mock private CompanyUserService companyUserService;
    @Mock private InternshipCalendarService calendarService;
    @Mock private com.internship.mapper.ClassCounselorRelationMapper classCounselorRelationMapper;
    @Mock private com.internship.mapper.TeacherUserMapper teacherUserMapper;
    @Mock private com.internship.mapper.DivisionMapper divisionMapper;
    @Mock private com.internship.mapper.DepartmentMapper departmentMapper;

    @InjectMocks
    private HomeServiceImpl service;

    @Test
    void dashboardDefaultsToGlobalTeachingCalendarInsteadOfLegacyApplicationWindow() {
        InternshipCalendar calendar = new InternshipCalendar();
        calendar.setStartDate("2026-03-01");
        calendar.setEndDate("2026-12-31");
        when(calendarService.getCalendar()).thenReturn(calendar);
        when(statusMapper.getDashboardStats("2026-03-01", "2026-12-31"))
                .thenReturn(Map.of());
        when(companyUserService.countByAuditStatus(0)).thenReturn(0L);
        when(companyUserMapper.countApproved()).thenReturn(0);
        when(announcementMapper.findAll()).thenReturn(new ArrayList<>());

        service.getHomeStats(null, null, null, null);

        verify(statusMapper).getDashboardStats("2026-03-01", "2026-12-31");
    }

    @ParameterizedTest
    @ValueSource(strings = {"COUNSELOR", "DEPARTMENT", "COLLEGE", "TEACHER"})
    void missingTeachingScopeNeverFallsBackToSchoolWideStatistics(String teacherType) {
        when(announcementMapper.findAll()).thenReturn(new ArrayList<>());

        var result = service.getHomeStats(99L, teacherType, "2026-09-01", "2027-09-01");

        assertEquals(0.0, result.getInternshipRate());
        assertEquals(0, result.getStatusData().stream().mapToInt(item -> item.getValue()).sum());
        verifyNoInteractions(statusMapper);
    }

    @Test
    void collegeWithoutDivisionsNeverFallsBackToSchoolWideStatistics() {
        var teacher = new com.internship.entity.TeacherUser();
        teacher.setDepartmentId("7");
        when(teacherUserMapper.findById(99L)).thenReturn(teacher);
        when(announcementMapper.findAll()).thenReturn(new ArrayList<>());

        var result = service.getHomeStats(99L, "COLLEGE", "2026-09-01", "2027-09-01");

        assertEquals(0.0, result.getInternshipRate());
        verifyNoInteractions(statusMapper);
    }

    @Test
    void schoolYearRateIncludesUnplacedStudentsInTheDenominator() {
        when(statusMapper.getDashboardStats("2026-09-01", "2027-09-01"))
                .thenReturn(Map.of("totalStudents", 47, "confirmed", 18, "noOffer", 29));
        when(announcementMapper.findAll()).thenReturn(new ArrayList<>());

        var result = service.getHomeStats(1L, "ADMIN", "2026-09-01", "2027-09-01");

        assertEquals(38.3, result.getInternshipRate());
        assertEquals(29, result.getStatusData().stream()
                .filter(item -> "没offer".equals(item.getName())).findFirst().orElseThrow().getValue());
    }
}
