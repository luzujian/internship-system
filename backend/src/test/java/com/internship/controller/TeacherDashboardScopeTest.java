package com.internship.controller;

import com.internship.entity.TeacherUser;
import com.internship.entity.User;
import com.internship.mapper.TeacherUserMapper;
import com.internship.service.TeacherDashboardService;
import com.internship.service.impl.TeacherDashboardServiceImpl;
import com.internship.utils.CurrentHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TeacherDashboardScopeTest {
    private final TeacherDashboardController controller = new TeacherDashboardController();
    private final TeacherDashboardService service = mock(TeacherDashboardService.class);
    private final TeacherUserMapper teachers = mock(TeacherUserMapper.class);

    TeacherDashboardScopeTest() {
        ReflectionTestUtils.setField(controller, "teacherDashboardService", service);
        ReflectionTestUtils.setField(controller, "teacherUserMapper", teachers);
    }

    @AfterEach
    void clearIdentity() {
        CurrentHolder.remove();
    }

    @Test
    void missingTeacherNeverFallsBackToAllSchoolDashboard() {
        login(3L);
        assertThrows(AccessDeniedException.class, () -> controller.getDashboardStats(null, null));
        verifyNoInteractions(service);
    }

    @Test
    void counselorCannotQueryAnotherCounselorsAggregate() {
        login(3L);
        TeacherUser teacher = new TeacherUser();
        teacher.setId(3L);
        teacher.setStatus(1);
        teacher.setTeacherType("COUNSELOR");
        when(teachers.findById(3L)).thenReturn(teacher);

        assertThrows(AccessDeniedException.class,
                () -> controller.getCounselorDashboardStats(4L, null, null));
        verifyNoInteractions(service);
    }

    @Test
    void serviceRejectsUnknownScopeInsteadOfBuildingGlobalStats() {
        TeacherDashboardServiceImpl implementation = new TeacherDashboardServiceImpl();
        assertThrows(AccessDeniedException.class,
                () -> implementation.getDashboardStats(3L, "TEACHER", null, null));
    }

    private void login(Long userId) {
        User user = new User();
        user.setId(userId);
        CurrentHolder.setUser(user);
    }
}
