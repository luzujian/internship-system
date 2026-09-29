package com.internship.controller;

import com.internship.entity.Result;
import com.internship.entity.User;
import com.internship.entity.dto.StudentTodoItemDTO;
import com.internship.service.StudentHomeTodoService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentHomeTodoListTest {
    @Mock private UserService userService;
    @Mock private StudentHomeTodoService todoService;

    private StudentHomeController controller;

    @BeforeEach
    void setUp() {
        controller = new StudentHomeController();
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "studentHomeTodoService", todoService);
        org.springframework.security.core.userdetails.User principal =
                new org.springframework.security.core.userdetails.User("s001", "password", List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        User student = new User();
        student.setId(42L);
        student.setUsername("s001");
        when(userService.findByUsername("s001")).thenReturn(student);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void todoListUsesOnlyAuthenticatedStudentId() {
        List<StudentTodoItemDTO> todos = List.of();
        when(todoService.getActionableTodos(42L)).thenReturn(todos);

        Result result = controller.getTodoList();

        assertEquals(200, result.getCode());
        assertSame(todos, result.getData());
        verify(todoService).getActionableTodos(42L);
    }
}
