package com.internship.controller;

import com.internship.entity.Position;
import com.internship.entity.StudentJobApplication;
import com.internship.entity.StudentUser;
import com.internship.entity.User;
import com.internship.service.PositionService;
import com.internship.service.StudentJobApplicationService;
import com.internship.service.StudentUserService;
import com.internship.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentJobApplicationProfileSnapshotTest {
    @Mock private UserService userService;
    @Mock private StudentUserService studentUserService;
    @Mock private StudentJobApplicationService applicationService;
    @Mock private PositionService positionService;
    @InjectMocks private StudentJobApplicationController controller;
    private StudentUser profile;

    @BeforeEach
    void login() {
        var principal = new org.springframework.security.core.userdetails.User("student", "password", List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        User user = new User();
        user.setId(42L);
        when(userService.findByUsername("student")).thenReturn(user);
        profile = new StudentUser();
        profile.setId(42L);
        profile.setName("当前学生");
        profile.setStudentId("20260042");
        profile.setPhone("13800000042");
        profile.setEmail("current@example.test");
        profile.setMajor("当前专业");
        profile.setGrade(2023);
        when(studentUserService.findById(42L)).thenReturn(profile);
    }

    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "13900000099"})
    void submissionSnapshotsCurrentProfileInsteadOfMissingOrStaleBrowserValues(String submittedPhone) {
        activePosition();
        StudentJobApplication application = staleApplication(submittedPhone);

        assertEquals(200, controller.create(application).getCode());

        verify(applicationService).create(application);
        assertAll(
                () -> assertEquals(42L, application.getStudentId()),
                () -> assertEquals(profile.getName(), application.getStudentName()),
                () -> assertEquals(profile.getStudentId(), application.getStudentNo()),
                () -> assertEquals(profile.getPhone(), application.getPhone()),
                () -> assertEquals(profile.getEmail(), application.getEmail()),
                () -> assertEquals(profile.getMajor(), application.getMajor()),
                () -> assertEquals("2023", application.getGrade()),
                () -> assertEquals("学生填写的自我介绍", application.getSelfIntroduction()));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void requestPhoneCannotReplaceMissingPhoneInStudentProfile(String savedPhone) {
        profile.setPhone(savedPhone);

        var result = controller.create(staleApplication("13800000042"));

        assertEquals(400, result.getCode());
        assertTrue(result.getMessage().contains("缺少：电话"));
        verifyNoInteractions(applicationService, positionService);
    }

    @Test
    void missingOptionalProfileFieldsDoNotRetainForgedBrowserValues() {
        activePosition();
        profile.setMajor(null);
        profile.setGrade(null);
        StudentJobApplication application = staleApplication("");

        assertEquals(200, controller.create(application).getCode());

        assertNull(application.getMajor());
        assertNull(application.getGrade());
    }

    private void activePosition() {
        Position position = new Position();
        position.setId(10L);
        position.setStatus("active");
        when(positionService.findById(10L)).thenReturn(position);
    }

    private StudentJobApplication staleApplication(String phone) {
        StudentJobApplication application = new StudentJobApplication();
        application.setStudentId(999L);
        application.setPositionId(10L);
        application.setStudentName("过期姓名");
        application.setStudentNo("wrong-student");
        application.setPhone(phone);
        application.setEmail("stale@example.test");
        application.setMajor("过期专业");
        application.setGrade("1999");
        application.setSelfIntroduction("学生填写的自我介绍");
        return application;
    }
}
