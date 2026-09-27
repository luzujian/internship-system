package com.internship.controller;

import com.internship.entity.Result;
import com.internship.entity.User;
import com.internship.entity.dto.RecentActivityPageDTO;
import com.internship.service.InternshipProgressRecordService;
import com.internship.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentHomeProgressListTest {

    @Mock
    private UserService userService;

    @Mock
    private InternshipProgressRecordService progressRecordService;

    private StudentHomeController controller;

    @BeforeEach
    void setUp() {
        controller = new StudentHomeController();
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "progressRecordService", progressRecordService);

        org.springframework.security.core.userdetails.User principal =
                new org.springframework.security.core.userdetails.User(
                        "s001", "password", List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        User student = new User();
        student.setId(42L);
        student.setUsername("s001");
        student.setRole("ROLE_STUDENT");
        when(userService.findByUsername("s001")).thenReturn(student);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void progressListUsesOnlyAuthenticatedStudentIdentity() {
        RecentActivityPageDTO page = new RecentActivityPageDTO(List.of(), null, false);
        when(progressRecordService.getRecentActivities(42L, 10, "cursor-value"))
                .thenReturn(page);

        Result result = controller.getProgressList("10", "cursor-value");

        assertEquals(200, result.getCode());
        assertSame(page, result.getData());
        verify(progressRecordService).getRecentActivities(42L, 10, "cursor-value");
    }

    @Test
    void invalidCursorErrorIsNotConvertedToHttp200Result() {
        when(progressRecordService.getRecentActivities(42L, 10, "bad"))
                .thenThrow(new IllegalArgumentException("无效的分页游标"));

        assertThrows(IllegalArgumentException.class,
                () -> controller.getProgressList("10", "bad"));
    }

    @Test
    void malformedLimitIsReportedAsBadRequestInput() {
        assertThrows(IllegalArgumentException.class,
                () -> controller.getProgressList("ten", null));
    }
}
