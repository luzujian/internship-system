package com.internship.controller;

import com.internship.service.OnlineUserService;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OnlineUserControllerTest {
    @Configuration @EnableMethodSecurity static class Config {
        @Bean OnlineUserService onlineService() { return mock(OnlineUserService.class); }
        @Bean OnlineUserController controller(OnlineUserService service) { return new OnlineUserController(service); }
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private void authenticate(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("test", "",
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }
    @Test void everyEndpointRequiresBothAdministratorAndLoginLogPermission() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var controller = context.getBean(OnlineUserController.class);
            SecurityContextHolder.clearContext();
            assertThrows(AuthenticationCredentialsNotFoundException.class, controller::summary);
            assertThrows(AuthenticationCredentialsNotFoundException.class, () -> controller.list("", "", "", 1, 20));
            for (String[] permissions : List.of(new String[]{"ROLE_ADMIN"}, new String[]{"log:login:view"},
                    new String[]{"ROLE_TEACHER_COLLEGE", "log:login:view"})) {
                authenticate(permissions);
                assertThrows(AccessDeniedException.class, controller::summary);
                assertThrows(AccessDeniedException.class, () -> controller.list("", "", "", 1, 20));
            }
            authenticate("ROLE_ADMIN", "log:login:view");
            assertEquals(200, controller.summary().getBody().getCode());
            assertEquals(200, controller.list("", "", "", 1, 20).getBody().getCode());
        }
    }
    @Test void unavailableDataReturnsServiceUnavailableInsteadOfAZeroCount() {
        var service = mock(OnlineUserService.class);
        when(service.summary()).thenThrow(new IllegalStateException("offline"));
        var response = new OnlineUserController(service).summary();
        assertEquals(503, response.getStatusCode().value());
        assertNull(response.getBody().getData());
    }
}
