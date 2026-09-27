package com.internship.controller;

import com.internship.entity.StudentInternshipStatus;
import com.internship.entity.TeacherUser;
import com.internship.entity.User;
import com.internship.mapper.TeacherAutomationScopeMapper;
import com.internship.service.StudentInternshipStatusService;
import com.internship.service.TeacherUserService;
import com.internship.utils.CurrentHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StudentInternshipStatusDownloadScopeTest {
    @AfterEach
    void clearIdentity() {
        CurrentHolder.remove();
    }

    @Test
    void teacherCannotDownloadAnotherDivisionsStudentApplication() {
        Fixture fixture = fixture("ROLE_TEACHER_DEPARTMENT", 0);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(AccessDeniedException.class,
                () -> fixture.controller.downloadInternshipApplication(41L, response));
        assertEquals(0, response.getContentAsByteArray().length);
    }

    @Test
    void scopedTeacherCanDownloadApplication() throws Exception {
        Fixture fixture = fixture("ROLE_TEACHER_DEPARTMENT", 1);
        MockHttpServletResponse response = new MockHttpServletResponse();

        fixture.controller.downloadInternshipApplication(41L, response);

        byte[] bytes = response.getContentAsByteArray();
        assertTrue(bytes.length > 4);
        assertEquals((byte) 'P', bytes[0]);
        assertEquals((byte) 'K', bytes[1]);
    }

    @Test
    void legacyGenericTeacherRoleCannotBypassTypeBinding() {
        Fixture fixture = fixture("ROLE_TEACHER", 1);
        assertThrows(AccessDeniedException.class,
                () -> fixture.controller.downloadInternshipApplication(41L, new MockHttpServletResponse()));
    }

    private Fixture fixture(String actorRole, int containsResult) {
        StudentInternshipStatusController controller = new StudentInternshipStatusController();
        StudentInternshipStatusService statuses = mock(StudentInternshipStatusService.class);
        TeacherUserService teachers = mock(TeacherUserService.class);
        TeacherAutomationScopeMapper scope = mock(TeacherAutomationScopeMapper.class);
        ReflectionTestUtils.setField(controller, "studentInternshipStatusService", statuses);
        ReflectionTestUtils.setField(controller, "teacherUserService", teachers);
        ReflectionTestUtils.setField(controller, "teacherScopeMapper", scope);

        StudentInternshipStatus status = new StudentInternshipStatus();
        status.setId(41L);
        status.setStudentId(55L);
        when(statuses.findById(41L)).thenReturn(status);
        TeacherUser teacher = new TeacherUser();
        teacher.setId(11L);
        teacher.setTeacherUserId("T11");
        teacher.setTeacherType("DEPARTMENT");
        teacher.setDepartmentId("1");
        teacher.setDivisionId("3");
        teacher.setStatus(1);
        when(teachers.findById(11L)).thenReturn(teacher);
        when(scope.contains(teacher, 55L)).thenReturn(containsResult);

        User actor = new User();
        actor.setId(11L);
        actor.setUsername("T11");
        actor.setRole(actorRole);
        CurrentHolder.setUser(actor);
        return new Fixture(controller);
    }

    private record Fixture(StudentInternshipStatusController controller) {}
}
