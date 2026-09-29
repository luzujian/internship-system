package com.internship.service.impl;

import com.internship.entity.InternshipTimeSettings;
import com.internship.entity.dto.InternshipCalendar;
import com.internship.exception.BusinessException;
import com.internship.mapper.InternshipTimeSettingsMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternshipCalendarServiceImplTest {

    @Mock
    private InternshipTimeSettingsMapper settingsMapper;

    @Test
    void readsOnlyCanonicalCalendarFields() {
        InternshipTimeSettings settings = settings(7L, "2026-03-01", "2026-09-17", 7, "2026-10-10");
        settings.setApplicationStartDate("2025-01-01");
        when(settingsMapper.findLatest()).thenReturn(settings);

        InternshipCalendar calendar = service().getCalendar();

        assertEquals("2026-03-01", calendar.getStartDate());
        assertEquals("2026-09-17", calendar.getEndDate());
        assertEquals(7, calendar.getReportCycle());
        assertEquals("2026-10-10", calendar.getAppraisalDeadline());
        assertNotNull(calendar.getVersion());
    }

    @Test
    void rejectsStaleVersionBeforeUpdating() {
        InternshipTimeSettings current = settings(7L, "2026-03-01", "2026-09-17", 7, "2026-10-10");
        when(settingsMapper.findLatest()).thenReturn(current);
        InternshipCalendar request = request("stale");

        assertThrows(BusinessException.class,
                () -> service().saveCalendar(request, 11L, "department-teacher"));

        verify(settingsMapper, never()).updateCalendarIfUnchanged(any(), any());
    }

    @Test
    void createsCalendarWithUpdaterAuditWhenNoSettingsExist() {
        InternshipTimeSettings saved = settings(
                8L, "2026-03-01", "2026-09-17", 7, "2026-10-10");
        when(settingsMapper.findLatest()).thenReturn(null, saved);
        when(settingsMapper.insert(any())).thenReturn(1);

        InternshipCalendar result = service().saveCalendar(
                request(null), 11L, "department-teacher");

        ArgumentCaptor<InternshipTimeSettings> created =
                ArgumentCaptor.forClass(InternshipTimeSettings.class);
        verify(settingsMapper).insert(created.capture());
        assertEquals(11L, created.getValue().getCreateAdminId());
        assertEquals(11L, created.getValue().getUpdateAdminId());
        assertEquals("department-teacher", created.getValue().getCreator());
        assertEquals("2026-03-01", result.getStartDate());
    }

    @Test
    void updatesFourFieldsWithCompareAndSet() {
        InternshipTimeSettings current = settings(7L, "2026-03-01", "2026-09-17", 7, "2026-10-10");
        InternshipTimeSettings saved = settings(7L, "2026-04-01", "2026-10-01", 14, "2026-10-31");
        when(settingsMapper.findLatest()).thenReturn(current, current, saved);
        InternshipCalendarServiceImpl service = service();
        InternshipCalendar request = service.getCalendar();
        request.setStartDate("2026-04-01");
        request.setEndDate("2026-10-01");
        request.setReportCycle(14);
        request.setAppraisalDeadline("2026-10-31");
        when(settingsMapper.updateCalendarIfUnchanged(any(), any())).thenReturn(1);

        InternshipCalendar result = service.saveCalendar(request, 11L, "department-teacher");

        ArgumentCaptor<InternshipTimeSettings> updated = ArgumentCaptor.forClass(InternshipTimeSettings.class);
        verify(settingsMapper).updateCalendarIfUnchanged(updated.capture(), any());
        assertEquals("2026-04-01", updated.getValue().getStartDate());
        assertEquals("2026-10-01", updated.getValue().getEndDate());
        assertEquals(14, updated.getValue().getReportCycle());
        assertEquals("2026-10-31", updated.getValue().getEvaluationDeadline());
        assertEquals("2026-04-01", result.getStartDate());
    }

    @Test
    void rejectsAppraisalDeadlineOnInternshipEndDate() {
        InternshipCalendar request = request(null);
        request.setAppraisalDeadline(request.getEndDate());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service().saveCalendar(request, 11L, "department-teacher"));

        assertEquals("实习鉴定截止日期必须晚于实习结束日期", error.getMessage());
        verify(settingsMapper, never()).findLatest();
    }

    @Test
    void rejectsInvalidCalendarRange() {
        InternshipCalendar request = request(null);
        request.setEndDate("2026-02-28");

        assertThrows(BusinessException.class,
                () -> service().saveCalendar(request, 11L, "department-teacher"));

        verify(settingsMapper, never()).findLatest();
    }

    private InternshipCalendarServiceImpl service() {
        return new InternshipCalendarServiceImpl(settingsMapper);
    }

    private InternshipCalendar request(String version) {
        InternshipCalendar calendar = new InternshipCalendar();
        calendar.setStartDate("2026-03-01");
        calendar.setEndDate("2026-09-17");
        calendar.setReportCycle(7);
        calendar.setAppraisalDeadline("2026-10-10");
        calendar.setVersion(version);
        return calendar;
    }

    private InternshipTimeSettings settings(Long id,
                                            String start,
                                            String end,
                                            int cycle,
                                            String appraisalDeadline) {
        InternshipTimeSettings settings = new InternshipTimeSettings();
        settings.setId(id);
        settings.setStartDate(start);
        settings.setEndDate(end);
        settings.setReportCycle(cycle);
        settings.setEvaluationDeadline(appraisalDeadline);
        settings.setUpdateTime(new Date(1_750_000_000_000L));
        return settings;
    }
}
