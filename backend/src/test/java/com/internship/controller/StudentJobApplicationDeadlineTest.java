package com.internship.controller;

import com.internship.entity.Position;
import com.internship.entity.Result;
import com.internship.entity.StudentJobApplication;
import com.internship.entity.StudentUser;
import com.internship.entity.User;
import com.internship.service.PositionService;
import com.internship.service.StudentJobApplicationService;
import com.internship.service.StudentUserService;
import com.internship.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentJobApplicationDeadlineTest {
    @Mock private UserService userService;
    @Mock private StudentUserService studentUserService;
    @Mock private StudentJobApplicationService applicationService;
    @Mock private PositionService positionService;
    @InjectMocks private StudentJobApplicationController controller;

    @BeforeEach
    void login() {
        var principal = new org.springframework.security.core.userdetails.User("student", "password", List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        User user = new User();
        user.setId(1L);
        when(userService.findByUsername("student")).thenReturn(user);
        StudentUser profile = new StudentUser();
        profile.setName("测试学生");
        profile.setStudentUserId("s001");
        profile.setPhone("13800000000");
        profile.setEmail("qa@example.test");
        when(studentUserService.findById(1L)).thenReturn(profile);
    }

    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1})
    void endDateIsInclusiveForTheWholeShanghaiBusinessDay(int daysFromToday) {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        Position position = new Position();
        position.setId(10L);
        position.setStatus("active");
        position.setInternshipEndDate(Date.from(LocalDate.now(zone).plusDays(daysFromToday)
                .atStartOfDay(zone).toInstant()));
        when(positionService.findById(10L)).thenReturn(position);
        StudentJobApplication application = new StudentJobApplication();
        application.setPositionId(10L);

        Result result = controller.create(application);

        assertEquals(daysFromToday < 0 ? 400 : 200, result.getCode());
        if (daysFromToday < 0) verify(applicationService, never()).create(application);
        else verify(applicationService).create(application);
    }
}
