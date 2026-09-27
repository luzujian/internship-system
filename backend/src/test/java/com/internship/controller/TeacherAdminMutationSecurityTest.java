package com.internship.controller;

import com.internship.entity.TeacherRole;
import com.internship.service.ResourceService;
import com.internship.service.TeacherPermissionService;
import com.internship.service.TeacherRolePermissionService;
import com.internship.service.TeacherRoleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class TeacherAdminMutationSecurityTest {
    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class TestConfig {
        @Bean TeacherRoleController teacherRoleController() { return new TeacherRoleController(); }
        @Bean TeacherPermissionController teacherPermissionController() { return new TeacherPermissionController(); }
        @Bean TeacherRolePermissionController teacherRolePermissionController() { return new TeacherRolePermissionController(); }
        @Bean ResourceController resourceController() { return new ResourceController(); }
        @Bean TeacherRoleService teacherRoleService() { return mock(TeacherRoleService.class); }
        @Bean TeacherPermissionService teacherPermissionService() { return mock(TeacherPermissionService.class); }
        @Bean TeacherRolePermissionService teacherRolePermissionService() { return mock(TeacherRolePermissionService.class); }
        @Bean ResourceService resourceService() { return mock(ResourceService.class); }
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                role, "unused", List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @Test
    void everyTeacherRoleOrPermissionWriteMethodRequiresAdmin() {
        for (Class<?> type : List.of(TeacherRoleController.class, TeacherPermissionController.class,
                TeacherRolePermissionController.class)) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.isAnnotationPresent(PostMapping.class) || method.isAnnotationPresent(PutMapping.class)
                        || method.isAnnotationPresent(DeleteMapping.class)) {
                    PreAuthorize gate = method.getAnnotation(PreAuthorize.class);
                    assertNotNull(gate, type.getSimpleName() + "." + method.getName());
                    assertEquals("hasRole('ADMIN')", gate.value(), type.getSimpleName() + "." + method.getName());
                }
            }
        }
    }

    @Test
    void methodSecurityRejectsTeacherMutationAndPreservesAdminMutation() {
        try (var context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            TeacherRoleController controller = context.getBean(TeacherRoleController.class);
            TeacherRoleService service = context.getBean(TeacherRoleService.class);
            TeacherRole role = new TeacherRole();
            role.setRoleName("测试角色");

            authenticate("TEACHER");
            assertThrows(AccessDeniedException.class, () -> controller.createRole(role));
            verifyNoInteractions(service);

            authenticate("ADMIN");
            assertDoesNotThrow(() -> controller.createRole(role));
            verify(service).insert(role);
        }
    }

    @Test
    void adminOnlyLearningResourceManagementIsEnforcedByMethodSecurity() {
        for (String methodName : List.of("reviewResource", "getPendingResources", "addCategory", "deleteResource")) {
            Method method = java.util.Arrays.stream(ResourceController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .findFirst().orElseThrow();
            PreAuthorize gate = method.getAnnotation(PreAuthorize.class);
            assertNotNull(gate, methodName);
            assertEquals("hasRole('ADMIN')", gate.value(), methodName);
        }

        try (var context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            ResourceController controller = context.getBean(ResourceController.class);
            ResourceService service = context.getBean(ResourceService.class);
            authenticate("STUDENT");
            assertThrows(AccessDeniedException.class, controller::getPendingResources);
            verifyNoInteractions(service);

            authenticate("ADMIN");
            assertEquals(200, controller.getPendingResources().getStatusCode().value());
            verify(service).getPendingResources();
        }
    }
}
