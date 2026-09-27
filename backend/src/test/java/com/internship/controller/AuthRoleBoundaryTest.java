package com.internship.controller;

import com.internship.entity.TeacherUser;
import com.internship.service.TeacherUserService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class AuthRoleBoundaryTest {
    @Test
    void publicStudentRegistrationHasNoControllerHandler() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new AuthController()).build();
        assertEquals(404, mvc.perform(post("/api/auth/register")
                .contentType("application/json")
                .content("{\"username\":\"untrusted\",\"role\":\"ROLE_STUDENT\"}"))
                .andReturn().getResponse().getStatus());
    }

    @Test
    void teacherNameCannotAssignCollegeRoleAtLogin() {
        AuthController controller = new AuthController();
        TeacherUserService teachers = mock(TeacherUserService.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        TeacherUser teacher = new TeacherUser();
        teacher.setTeacherUserId("t-1");
        teacher.setName("学院主任");
        teacher.setPassword("encoded");
        teacher.setStatus(1);
        when(teachers.findByTeacherUserId("t-1")).thenReturn(teacher);
        when(encoder.matches("valid", "encoded")).thenReturn(true);
        ReflectionTestUtils.setField(controller, "teacherUserService", teachers);
        ReflectionTestUtils.setField(controller, "passwordEncoder", encoder);

        assertThrows(AuthenticationException.class, () -> ReflectionTestUtils.invokeMethod(
                controller, "handleTeacherLogin", "t-1", "valid", new MockHttpServletRequest()));
        verify(teachers, never()).update(any());
    }
}
