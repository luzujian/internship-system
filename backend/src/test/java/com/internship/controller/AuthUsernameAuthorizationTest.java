package com.internship.controller;

import com.internship.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AuthUsernameAuthorizationTest {
    @Test
    void loggedInUserCannotChooseAnotherAccountByOldUsername() {
        AuthController controller = new AuthController();
        UserService userService = mock(UserService.class);
        ReflectionTestUtils.setField(controller, "userService", userService);
        var principal = new UsernamePasswordAuthenticationToken("student-a", null, java.util.List.of());

        assertThrows(AccessDeniedException.class, () -> controller.updateUsername(
                Map.of("oldUsername", "admin", "newUsername", "taken-over"), principal));
        verifyNoInteractions(userService);
    }

    @Test
    void ownAccountIsSelectedFromPrincipal() {
        AuthController controller = new AuthController();
        UserService userService = mock(UserService.class);
        ReflectionTestUtils.setField(controller, "userService", userService);
        when(userService.updateUsername("student-a", "new-student-a")).thenReturn(1);
        var principal = new UsernamePasswordAuthenticationToken("student-a", null, java.util.List.of());

        controller.updateUsername(Map.of("oldUsername", "student-a", "newUsername", "new-student-a"), principal);

        verify(userService).updateUsername("student-a", "new-student-a");
    }
}
