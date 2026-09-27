package com.internship.service.impl;

import com.internship.entity.dto.InternshipCalendar;
import com.internship.mapper.AnnouncementMapper;
import com.internship.mapper.CompanyUserMapper;
import com.internship.mapper.StudentApplicationMapper;
import com.internship.mapper.StudentInternshipStatusMapper;
import com.internship.service.CompanyUserService;
import com.internship.service.InternshipCalendarService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeServiceImplCalendarTest {

    @Mock private StudentInternshipStatusMapper statusMapper;
    @Mock private StudentApplicationMapper studentApplicationMapper;
    @Mock private CompanyUserMapper companyUserMapper;
    @Mock private AnnouncementMapper announcementMapper;
    @Mock private CompanyUserService companyUserService;
    @Mock private InternshipCalendarService calendarService;

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
}
