package com.internship.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.controller.TemplateFileController;
import com.internship.entity.AdminUser;
import com.internship.entity.StudentUser;
import com.internship.entity.TemplateFile;
import com.internship.entity.User;
import com.internship.service.JwtRedisService;
import com.internship.service.TemplateFileService;
import com.internship.service.UserCacheService;
import com.internship.utils.AliyunOSSOperator;
import com.internship.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TemplateDownloadSecurityTest {
    @Configuration(proxyBeanMethods = false)
    static class TestBeans {
        @Bean(name = "mvcHandlerMappingIntrospector")
        HandlerMappingIntrospector mvcHandlerMappingIntrospector() { return new HandlerMappingIntrospector(); }
        @Bean JwtAuthenticationEntryPoint entryPoint() { return new JwtAuthenticationEntryPoint(); }
        @Bean CustomAccessDeniedHandler deniedHandler() { return new CustomAccessDeniedHandler(); }
        @Bean JwtUtils jwtUtils() { return mock(JwtUtils.class); }
        @Bean JwtRedisService jwtRedisService() { return mock(JwtRedisService.class); }
        @Bean UserCacheService userCacheService() { return mock(UserCacheService.class); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean UserDetailsService userDetailsService() { return mock(UserDetailsService.class); }
    }

    @Test
    void templateDownloadRequiresAdminAtHttpBoundary() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                    "test-jwt-secret", Map.of("jwt.secret", "local-test-only-signing-key-0123456789abcdef0123456789abcdef")));
            context.register(SecurityConfig.class, TestBeans.class);
            context.refresh();

            FilterChainProxy security = context.getBean(FilterChainProxy.class);
            JwtUtils jwt = context.getBean(JwtUtils.class);
            UserCacheService cache = context.getBean(UserCacheService.class);
            UserDetailsService details = context.getBean(UserDetailsService.class);

            mockActor(jwt, cache, details, "student-token", "student", "STUDENT");
            mockActor(jwt, cache, details, "admin-token", "admin", "ADMIN");

            var anonymous = execute(security, null);
            assertEquals(401, anonymous.response().getStatus());
            assertFalse(anonymous.handlerReached());

            var student = execute(security, "student-token");
            assertEquals(403, student.response().getStatus());
            assertFalse(student.handlerReached());

            var admin = execute(security, "admin-token");
            assertTrue(admin.handlerReached());
        }
    }

    @Test
    void adminDownloadUsesTheRecordedTemplateFile() throws Exception {
        var controller = new TemplateFileController();
        var service = mock(TemplateFileService.class);
        var oss = mock(AliyunOSSOperator.class);
        ReflectionTestUtils.setField(controller, "templateFileService", service);
        ReflectionTestUtils.setField(controller, "aliyunOSSOperator", oss);
        var template = new TemplateFile();
        template.setId(7L);
        template.setName("approved.pdf");
        template.setFileUrl("resources/templates/approved.pdf");
        when(service.findById(7L)).thenReturn(template);
        byte[] content = "template".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        when(oss.downloadFile(template.getFileUrl())).thenReturn(content);

        var response = controller.downloadTemplate(7L);

        assertEquals(200, response.getStatusCode().value());
        assertArrayEquals(content, response.getBody());
        verify(oss).downloadFile(template.getFileUrl());
        verify(service).incrementDownloadCount(7L);
    }

    private void mockActor(JwtUtils jwt, UserCacheService cache, UserDetailsService details,
                           String token, String username, String role) {
        Claims claims = mock(Claims.class);
        when(jwt.validateToken(token)).thenReturn(true);
        when(jwt.parseToken(token)).thenReturn(claims);
        when(claims.get("username", String.class)).thenReturn(username);
        when(claims.get("role", String.class)).thenReturn("ROLE_" + role);
        when(claims.get("typ")).thenReturn("access");
        when(claims.getIssuedAt()).thenReturn(new Date());
        User user = new User();
        user.setId("ADMIN".equals(role) ? 1L : 2L);
        user.setUsername(username);
        user.setRole("ROLE_" + role);
        when(cache.getUser(username)).thenReturn(user);
        if ("ADMIN".equals(role)) {
            AdminUser admin = new AdminUser();
            admin.setStatus(1);
            when(cache.getAdminUser(username)).thenReturn(admin);
        } else {
            StudentUser student = new StudentUser();
            student.setStatus(1);
            when(cache.getStudentUser(username)).thenReturn(student);
        }
        when(details.loadUserByUsername(username)).thenReturn(
                org.springframework.security.core.userdetails.User.withUsername(username)
                        .password("unused").roles(role).build());
    }

    private RouteResult execute(FilterChainProxy security, String token) throws Exception {
        String path = "/api/admin/templates/7/download";
        var request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        if (token != null) request.addHeader("Authorization", "Bearer " + token);
        var response = new MockHttpServletResponse();
        var handlerReached = new AtomicBoolean();
        security.doFilter(request, response, (ignoredRequest, ignoredResponse) -> handlerReached.set(true));
        return new RouteResult(response, handlerReached.get());
    }

    private record RouteResult(MockHttpServletResponse response, boolean handlerReached) { }
}
