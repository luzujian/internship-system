package com.internship.controller;

import com.internship.entity.TeacherUser;
import com.internship.mapper.TeacherUserMapper;
import com.internship.service.TeacherInternshipAppraisalService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeacherInternshipAppraisalControllerTest {
    private TeacherInternshipAppraisalService service;
    private TeacherUserMapper teacherUserMapper;
    private TeacherInternshipAppraisalController controller;

    @BeforeEach
    void setUp() {
        service = mock(TeacherInternshipAppraisalService.class);
        teacherUserMapper = mock(TeacherUserMapper.class);
        controller = new TeacherInternshipAppraisalController(service, teacherUserMapper);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "T001", "n/a", List.of(new SimpleGrantedAuthority("ROLE_TEACHER_COUNSELOR"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void derivesTeacherFromAuthenticationAndReturnsSame404ForMissingOrUnauthorizedStudent() {
        TeacherUser teacher = new TeacherUser();
        teacher.setId(7L);
        teacher.setTeacherType("COUNSELOR");
        when(teacherUserMapper.findByTeacherUserId("T001")).thenReturn(teacher);
        when(service.findSubmittedDetail(teacher, 88L)).thenReturn(null);

        ResponseEntity<?> response = controller.detail(88L);

        assertEquals(404, response.getStatusCode().value());
        verify(teacherUserMapper).findByTeacherUserId("T001");
        verify(service).findSubmittedDetail(teacher, 88L);
    }

    @Test
    void fileEndpointReturns404WithoutRevealingWhetherIdExists() throws Exception {
        TeacherUser teacher = new TeacherUser();
        teacher.setId(7L);
        teacher.setTeacherType("COUNSELOR");
        when(teacherUserMapper.findByTeacherUserId("T001")).thenReturn(teacher);
        when(service.loadFile(teacher, 99L, false)).thenReturn(null);

        ResponseEntity<byte[]> response = controller.download(99L);

        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void genericTeacherRouteAllowsOnlyTheThreeScopedTeacherRoles() {
        RequestMapping mapping = TeacherInternshipAppraisalController.class.getAnnotation(RequestMapping.class);
        PreAuthorize authorization = TeacherInternshipAppraisalController.class.getAnnotation(PreAuthorize.class);

        assertEquals("/api/teacher/internship-appraisals", mapping.value()[0]);
        assertTrue(authorization.value().contains("TEACHER_COLLEGE"));
        assertTrue(authorization.value().contains("TEACHER_DEPARTMENT"));
        assertTrue(authorization.value().contains("TEACHER_COUNSELOR"));
    }
}
