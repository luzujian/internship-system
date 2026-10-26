package com.internship.controller;
import com.internship.entity.User;
import com.internship.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class UserDetailPermissionTest {
    @TestConfiguration @EnableMethodSecurity static class Config {
        @Bean UserService userService(){return mock(UserService.class);}
        @Bean UserController userController(){return new UserController();}
    }
    @Test void onlyAdminCanEnumerateUsers() {
        try(var context=new AnnotationConfigApplicationContext(Config.class)) {
            var service=context.getBean(UserService.class);var controller=context.getBean(UserController.class);
            var user=new User();user.setId(9L);user.setPassword("hidden");when(service.findById(9L)).thenReturn(user);
            for(String role:java.util.List.of("STUDENT","COMPANY","TEACHER_COLLEGE","ADMIN")) {
                SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("user","",java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_"+role))));
                if(role.equals("ADMIN")){assertEquals(200,controller.getUserById(9L).getCode());assertNull(user.getPassword());}
                else assertThrows(org.springframework.security.access.AccessDeniedException.class,()->controller.getUserById(9L));
            }
            verify(service,times(1)).findById(9L);
        } finally {SecurityContextHolder.clearContext();}
    }
}
