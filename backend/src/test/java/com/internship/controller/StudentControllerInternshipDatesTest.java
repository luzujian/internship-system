package com.internship.controller;

import com.internship.entity.Result;
import com.internship.entity.StudentInternshipStatus;
import com.internship.entity.User;
import com.internship.service.StudentInternshipStatusService;
import com.internship.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StudentControllerInternshipDatesTest {

    private StudentController controller;
    private UserService userService;
    private StudentInternshipStatusService statusService;

    @BeforeEach
    void setUp() {
        controller = new StudentController();
        userService = mock(UserService.class);
        statusService = mock(StudentInternshipStatusService.class);
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "studentInternshipStatusService", statusService);

        var principal = org.springframework.security.core.userdetails.User
                .withUsername("s001")
                .password("test")
                .roles("STUDENT")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        User user = new User();
        user.setId(7L);
        user.setUsername("s001");
        when(userService.findByUsername("s001")).thenReturn(user);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void statusPreservesActualDatesAndAllowsOpenEnd() {
        StudentInternshipStatus status = new StudentInternshipStatus();
        status.setStudentId(7L);
        Date explicitStart = date("2026-03-15");
        status.setInternshipStartTime(explicitStart);
        when(statusService.findByStudentId(7L)).thenReturn(status);

        Result result = controller.getMyInternshipStatus();

        assertEquals(200, result.getCode());
        StudentInternshipStatus returned = (StudentInternshipStatus) result.getData();
        assertEquals(explicitStart, returned.getInternshipStartTime());
        assertNull(returned.getInternshipEndTime());
    }

    @Test
    void recordUsesActualStatusDatesInsteadOfPositionOrCalendarDates() {
        StudentInternshipStatus status = new StudentInternshipStatus();
        status.setId(21L);
        status.setStudentId(7L);
        status.setPositionId(31L);
        status.setInternshipStartTime(date("2026-04-01"));
        status.setInternshipEndTime(date("2026-10-31"));
        when(statusService.findByStudentId(7L)).thenReturn(status);

        Result result = controller.getInternshipRecord();

        assertEquals(200, result.getCode());
        Map<?, ?> record = (Map<?, ?>) result.getData();
        assertEquals("2026-04-01", record.get("internshipStartDate"));
        assertEquals("2026-10-31", record.get("internshipEndDate"));
    }

    @Test
    void openInternshipDoesNotFabricateAnEndDate() {
        StudentInternshipStatus status = new StudentInternshipStatus();
        status.setStudentId(7L);
        status.setInternshipStartTime(date("2026-05-01"));
        when(statusService.findByStudentId(7L)).thenReturn(status);

        Result statusResult = controller.getMyInternshipStatus();
        assertEquals(200, statusResult.getCode());
        StudentInternshipStatus returned = (StudentInternshipStatus) statusResult.getData();
        assertEquals(date("2026-05-01"), returned.getInternshipStartTime());
        assertNull(returned.getInternshipEndTime());

        Result recordResult = controller.getInternshipRecord();
        assertEquals(200, recordResult.getCode());
        Map<?, ?> record = (Map<?, ?>) recordResult.getData();
        assertEquals("2026-05-01", record.get("internshipStartDate"));
        assertEquals("", record.get("internshipEndDate"));
        assertEquals(0, record.get("progress"));
    }

    private Date date(String value) {
        return Date.from(LocalDate.parse(value)
                .atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
