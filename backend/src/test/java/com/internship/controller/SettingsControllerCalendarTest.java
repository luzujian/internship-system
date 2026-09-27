package com.internship.controller;

import com.internship.entity.Result;
import com.internship.entity.dto.InternshipCalendar;
import com.internship.service.InternshipCalendarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettingsControllerCalendarTest {

    @Mock
    private InternshipCalendarService calendarService;

    private SettingsController controller;

    @BeforeEach
    void setUp() {
        controller = new SettingsController();
        ReflectionTestUtils.setField(controller, "internshipCalendarService", calendarService);
    }

    @Test
    void exposesCanonicalCalendarContract() {
        InternshipCalendar calendar = calendar();
        when(calendarService.getCalendar()).thenReturn(calendar);

        Result result = controller.getInternshipCalendar();

        assertEquals(200, result.getCode());
        assertEquals(calendar, result.getData());
    }

    @Test
    @SuppressWarnings("unchecked")
    void legacyNodesMapsToCalendarWithoutOldDeadlines() {
        when(calendarService.getCalendar()).thenReturn(calendar());

        Result result = controller.getInternshipNodes();
        Map<String, Object> data = (Map<String, Object>) result.getData();

        assertEquals("2026-03-01", data.get("startDate"));
        assertEquals("2026-09-17", data.get("endDate"));
        assertEquals("2026-10-10", data.get("evaluationDeadline"));
        assertNull(data.get("applicationStartTime"));
        assertNull(data.get("companyConfirmationDeadline"));
    }

    @Test
    void calendarAndLegacyWritesAreDepartmentOnly() throws Exception {
        Method calendarWrite = SettingsController.class
                .getMethod("updateInternshipCalendar", InternshipCalendar.class);
        Method legacyWrite = SettingsController.class
                .getMethod("updateInternshipNodes", Map.class);

        assertEquals("hasRole('TEACHER_DEPARTMENT')",
                calendarWrite.getAnnotation(PreAuthorize.class).value());
        assertEquals("hasRole('TEACHER_DEPARTMENT')",
                legacyWrite.getAnnotation(PreAuthorize.class).value());
    }

    private InternshipCalendar calendar() {
        InternshipCalendar calendar = new InternshipCalendar();
        calendar.setStartDate("2026-03-01");
        calendar.setEndDate("2026-09-17");
        calendar.setReportCycle(7);
        calendar.setAppraisalDeadline("2026-10-10");
        calendar.setVersion("version-1");
        return calendar;
    }
}
