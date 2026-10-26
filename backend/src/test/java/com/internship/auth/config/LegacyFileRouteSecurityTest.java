package com.internship.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.entity.AdminUser;
import com.internship.entity.TeacherUser;
import com.internship.entity.User;
import com.internship.service.UserCacheService;
import com.internship.service.JwtRedisService;
import com.internship.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LegacyFileRouteSecurityTest {
    @TestConfiguration(proxyBeanMethods = false)
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
    void retiredRoutesAndStudentProvisioningRespectHttpBoundaries() throws Exception {
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
            Claims claims = mock(Claims.class);
            when(jwt.validateToken("admin-token")).thenReturn(true);
            when(jwt.parseToken("admin-token")).thenReturn(claims);
            when(claims.get("username", String.class)).thenReturn("admin");
            when(claims.get("role", String.class)).thenReturn("ROLE_ADMIN");
            when(claims.get("typ")).thenReturn("access");
            when(claims.getIssuedAt()).thenReturn(new Date());
            User admin = new User();
            admin.setId(1L);
            admin.setUsername("admin");
            admin.setRole("ROLE_ADMIN");
            when(cache.getUser("admin")).thenReturn(admin);
            AdminUser adminRecord = new AdminUser();
            adminRecord.setStatus(1);
            when(cache.getAdminUser("admin")).thenReturn(adminRecord);
            when(details.loadUserByUsername("admin")).thenReturn(
                    org.springframework.security.core.userdetails.User.withUsername("admin")
                            .password("unused").roles("ADMIN").build());

            Claims teacherClaims = mock(Claims.class);
            when(jwt.validateToken("teacher-token")).thenReturn(true);
            when(jwt.parseToken("teacher-token")).thenReturn(teacherClaims);
            when(teacherClaims.get("username", String.class)).thenReturn("teacher");
            when(teacherClaims.get("role", String.class)).thenReturn("ROLE_TEACHER_COLLEGE");
            when(teacherClaims.get("typ")).thenReturn("access");
            when(teacherClaims.getIssuedAt()).thenReturn(new Date());
            User teacher = new User();
            teacher.setId(2L);
            teacher.setUsername("teacher");
            teacher.setRole("ROLE_TEACHER_COLLEGE");
            when(cache.getUser("teacher")).thenReturn(teacher);
            TeacherUser teacherRecord = new TeacherUser();
            teacherRecord.setStatus(1);
            when(cache.getTeacherUser("teacher")).thenReturn(teacherRecord);
            when(details.loadUserByUsername("teacher")).thenReturn(
                    org.springframework.security.core.userdetails.User.withUsername("teacher")
                            .password("unused")
                            .authorities("ROLE_TEACHER_COLLEGE", "user:student:add").build());

            for (String path : new String[] { "/api/file-management", "/api/file-management/list",
                    "/api/file-management/download/1", "/api/file-management/upload" }) {
                MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
                request.setServletPath(path);
                request.addHeader("Authorization", "Bearer admin-token");
                MockHttpServletResponse response = new MockHttpServletResponse();
                AtomicBoolean handlerReached = new AtomicBoolean();
                security.doFilter(request, response, (ignoredRequest, ignoredResponse) -> handlerReached.set(true));
                assertEquals(403, response.getStatus(), path);
                assertFalse(handlerReached.get(), path);
            }

            for (String token : new String[] {"admin-token", "teacher-token"}) {
                for (String method : new String[] {"GET", "POST", "PUT", "DELETE"}) {
                    for (String path : new String[] {"/api/chat", "/api/chat/user", "/api/chat/message",
                            "/api/chat/1/messages", "/api/chat/1/members"}) {
                        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
                        request.setServletPath(path);
                        request.addHeader("Authorization", "Bearer " + token);
                        MockHttpServletResponse response = new MockHttpServletResponse();
                        AtomicBoolean reached = new AtomicBoolean();
                        security.doFilter(request, response, (req, res) -> reached.set(true));
                        assertEquals(403, response.getStatus(), method + " " + path);
                        assertFalse(reached.get());
                    }
                }
                MockHttpServletRequest ai = new MockHttpServletRequest("POST", "/api/ai/chat");
                ai.setServletPath("/api/ai/chat");
                ai.addHeader("Authorization", "Bearer " + token);
                AtomicBoolean aiReached = new AtomicBoolean();
                security.doFilter(ai, new MockHttpServletResponse(), (req, res) -> aiReached.set(true));
                assertTrue(aiReached.get(), "AI endpoint must remain available");
            }

            MockHttpServletRequest staleUpload = new MockHttpServletRequest("POST", "/api/resources/upload");
            staleUpload.setServletPath("/api/resources/upload");
            staleUpload.addHeader("Authorization", "Bearer admin-token");
            MockHttpServletResponse staleUploadResponse = new MockHttpServletResponse();
            AtomicBoolean staleUploadReached = new AtomicBoolean();
            security.doFilter(staleUpload, staleUploadResponse,
                    (ignoredRequest, ignoredResponse) -> staleUploadReached.set(true));
            assertEquals(403, staleUploadResponse.getStatus());
            assertFalse(staleUploadReached.get());

            MockHttpServletRequest anonymousPreview = new MockHttpServletRequest(
                    "POST", "/api/upload/preview-legacy-doc");
            anonymousPreview.setServletPath("/api/upload/preview-legacy-doc");
            MockHttpServletResponse anonymousPreviewResponse = new MockHttpServletResponse();
            AtomicBoolean previewHandlerReached = new AtomicBoolean();
            security.doFilter(anonymousPreview, anonymousPreviewResponse,
                    (ignoredRequest, ignoredResponse) -> previewHandlerReached.set(true));
            assertEquals(401, anonymousPreviewResponse.getStatus());
            assertFalse(previewHandlerReached.get());

            for (String path : new String[] { "/api/resources/categories", "/api/resources/categories/1" }) {
                for (String method : new String[] { "GET", "POST" }) {
                    MockHttpServletRequest request = new MockHttpServletRequest(method, path);
                    request.setServletPath(path);
                    request.addHeader("Authorization", "Bearer admin-token");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    AtomicBoolean handlerReached = new AtomicBoolean();
                    security.doFilter(request, response,
                            (ignoredRequest, ignoredResponse) -> handlerReached.set(true));
                    assertEquals(403, response.getStatus(), method + " " + path);
                    assertFalse(handlerReached.get(), method + " " + path);
                }
            }

            for (String path : new String[] { "/api/company/send-verify-code",
                    "/api/company/send-management-code", "/api/company/verify-management-code" }) {
                MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
                request.setServletPath(path);
                request.addHeader("Authorization", "Bearer admin-token");
                MockHttpServletResponse response = new MockHttpServletResponse();
                AtomicBoolean handlerReached = new AtomicBoolean();
                security.doFilter(request, response,
                        (ignoredRequest, ignoredResponse) -> handlerReached.set(true));
                assertEquals(403, response.getStatus(), path);
                assertFalse(handlerReached.get(), path);
            }

            MockHttpServletRequest currentUpload = new MockHttpServletRequest("POST", "/api/resource-documents/teacher/upload");
            currentUpload.setServletPath("/api/resource-documents/teacher/upload");
            currentUpload.addHeader("Authorization", "Bearer teacher-token");
            MockHttpServletResponse currentUploadResponse = new MockHttpServletResponse();
            AtomicBoolean currentUploadReached = new AtomicBoolean();
            security.doFilter(currentUpload, currentUploadResponse,
                    (ignoredRequest, ignoredResponse) -> currentUploadReached.set(true));
            assertTrue(currentUploadReached.get());

            MockHttpServletRequest registration = new MockHttpServletRequest("POST", "/api/auth/register");
            registration.setServletPath("/api/auth/register");
            registration.addHeader("Authorization", "Bearer admin-token");
            MockHttpServletResponse registrationResponse = new MockHttpServletResponse();
            AtomicBoolean registrationReached = new AtomicBoolean();
            security.doFilter(registration, registrationResponse,
                    (ignoredRequest, ignoredResponse) -> registrationReached.set(true));
            assertEquals(403, registrationResponse.getStatus());
            assertFalse(registrationReached.get());

            MockHttpServletRequest anonymousRegistration = new MockHttpServletRequest("POST", "/api/auth/register");
            anonymousRegistration.setServletPath("/api/auth/register");
            MockHttpServletResponse anonymousResponse = new MockHttpServletResponse();
            AtomicBoolean anonymousReached = new AtomicBoolean();
            security.doFilter(anonymousRegistration, anonymousResponse,
                    (ignoredRequest, ignoredResponse) -> anonymousReached.set(true));
            assertEquals(401, anonymousResponse.getStatus());
            assertFalse(anonymousReached.get());

            for (String base : new String[] { "/api/admin/student-users", "/api/admin/students" }) {
                for (String suffix : new String[] { "", "/batch", "/import" }) {
                    String path = base + suffix;
                    MockHttpServletRequest teacherRequest = new MockHttpServletRequest("POST", path);
                    teacherRequest.setServletPath(path);
                    teacherRequest.addHeader("Authorization", "Bearer teacher-token");
                    MockHttpServletResponse teacherResponse = new MockHttpServletResponse();
                    AtomicBoolean teacherReached = new AtomicBoolean();
                    security.doFilter(teacherRequest, teacherResponse,
                            (ignoredRequest, ignoredResponse) -> teacherReached.set(true));
                    assertEquals(403, teacherResponse.getStatus(), path);
                    assertFalse(teacherReached.get(), path);

                    MockHttpServletRequest adminRequest = new MockHttpServletRequest("POST", path);
                    adminRequest.setServletPath(path);
                    adminRequest.addHeader("Authorization", "Bearer admin-token");
                    MockHttpServletResponse adminResponse = new MockHttpServletResponse();
                    AtomicBoolean adminReached = new AtomicBoolean();
                    security.doFilter(adminRequest, adminResponse,
                            (ignoredRequest, ignoredResponse) -> adminReached.set(true));
                    assertTrue(adminReached.get(), path);
                }
            }

            MockHttpServletRequest health = new MockHttpServletRequest("GET", "/api/health/ping");
            health.setServletPath("/api/health/ping");
            MockHttpServletResponse healthResponse = new MockHttpServletResponse();
            AtomicBoolean healthReached = new AtomicBoolean();
            security.doFilter(health, healthResponse, (ignoredRequest, ignoredResponse) -> healthReached.set(true));
            assertTrue(healthReached.get());
        }
    }
}
