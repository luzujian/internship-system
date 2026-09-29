package com.internship.filter;

import com.internship.entity.User;
import com.internship.entity.StudentUser;
import com.internship.service.AuditLogService;
import com.internship.service.UserCacheService;
import com.internship.utils.CurrentHolder;
import com.internship.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class TokenFilterSecurityTest {

    @AfterEach
    void tearDown() {
        CurrentHolder.remove();
        SecurityContextHolder.clearContext();
    }

    @Test
    void skippedAuthenticationRequestCannotInheritOrLeakThreadLocalUser() throws Exception {
        TokenFilter filter = new TokenFilter();
        User staleUser = user(1L, "stale");
        CurrentHolder.setUser(staleUser);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            chainInvoked.set(true);
            assertNull(CurrentHolder.getUser(), "request must not inherit a user from a reused thread");
            CurrentHolder.setUser(user(2L, "in-request"));
        });

        assertTrue(chainInvoked.get());
        assertNull(CurrentHolder.getUser(), "request user must be cleared after the filter chain returns");
    }

    @Test
    void readOnlyIdentityCannotUseAnyUnsafeHttpMethod() throws Exception {
        TokenFilter filter = filterWithClaims(true, "access");
        for (String method : new String[] {"POST", "PUT", "PATCH", "DELETE", "TRACE", "PROPFIND"}) {
            Response result = invoke(filter, method, "/api/student/profile");
            assertEquals(403, result.response.getStatus(), method);
            assertFalse(result.chainInvoked.get(), method);
        }
    }

    @Test
    void readOnlyIdentityCanReadAndLogout() throws Exception {
        TokenFilter filter = filterWithClaims(true, "access");
        for (String method : new String[] {"GET", "HEAD", "OPTIONS"}) {
            Response result = invoke(filter, method, "/api/student/profile");
            assertEquals(200, result.response.getStatus(), method);
            assertTrue(result.chainInvoked.get(), method);
        }
        Response logout = invoke(filter, "POST", "/api/auth/logout");
        assertTrue(logout.chainInvoked.get());
    }

    @Test
    void normalAccessTokenStillAllowsWrites() throws Exception {
        Response result = invoke(filterWithClaims(false, "access"), "POST", "/api/student/profile");
        assertEquals(200, result.response.getStatus());
        assertTrue(result.chainInvoked.get());
    }

    @Test
    void refreshTokenCannotBeUsedAsHttpAccessToken() throws Exception {
        Response result = invoke(filterWithClaims(false, "refresh"), "POST", "/api/student/profile");
        assertEquals(401, result.response.getStatus());
        assertFalse(result.chainInvoked.get());
    }

    @Test
    void oldGenericTeacherTokenMustReauthenticate() throws Exception {
        JwtUtils jwt = mock(JwtUtils.class);
        Claims claims = Jwts.claims().add("username", "teacher")
                .add("role", "ROLE_TEACHER").add("typ", "access")
                .issuedAt(new Date()).build();
        when(jwt.validateToken("signed")).thenReturn(true);
        when(jwt.parseToken("signed")).thenReturn(claims);
        TokenFilter filter = new TokenFilter();
        ReflectionTestUtils.setField(filter, "jwtUtils", jwt);

        Response result = invoke(filter, "GET", "/api/teacher/students");
        assertEquals(401, result.response.getStatus());
        assertFalse(result.chainInvoked.get());
    }

    @Test
    void refreshRouteIsOnlyExemptAtItsExactPath() throws Exception {
        TokenFilter filter = filterWithClaims(true, "access");
        assertTrue(invoke(filter, "POST", "/api/auth/refresh-token").chainInvoked.get());
        Response lookalike = invoke(filter, "POST", "/api/student/auth/refresh-token");
        assertEquals(403, lookalike.response.getStatus());
        assertFalse(lookalike.chainInvoked.get());
        Response websocketWrite = invoke(filter, "POST", "/ws/agent-voice");
        assertEquals(403, websocketWrite.response.getStatus());
        assertFalse(websocketWrite.chainInvoked.get());
    }

    private TokenFilter filterWithClaims(boolean readOnly, String type) {
        JwtUtils jwt = mock(JwtUtils.class);
        Claims claims = Jwts.claims().add("username", "student")
                .add("role", "ROLE_STUDENT").add("typ", type)
                .add("isReadOnly", readOnly).issuedAt(new Date()).build();
        when(jwt.validateToken("signed")).thenReturn(true);
        when(jwt.parseToken("signed")).thenReturn(claims);

        UserCacheService cache = mock(UserCacheService.class);
        StudentUser student = new StudentUser();
        student.setStatus(1);
        when(cache.getStudentUser("student")).thenReturn(student);
        when(cache.getUser("student")).thenReturn(user(7L, "student"));

        UserDetailsService userDetails = mock(UserDetailsService.class);
        when(userDetails.loadUserByUsername("student")).thenReturn(
                org.springframework.security.core.userdetails.User.withUsername("student")
                        .password("unused").roles("STUDENT").build());
        ApplicationContext context = mock(ApplicationContext.class);
        when(context.getBean(UserDetailsService.class)).thenReturn(userDetails);
        when(context.getBean(AuditLogService.class)).thenReturn(mock(AuditLogService.class));

        TokenFilter filter = new TokenFilter();
        ReflectionTestUtils.setField(filter, "jwtUtils", jwt);
        ReflectionTestUtils.setField(filter, "userCacheService", cache);
        filter.setApplicationContext(context);
        return filter;
    }

    private Response invoke(TokenFilter filter, String method, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader("Authorization", "Bearer signed");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean(false);
        filter.doFilter(request, response, (servletRequest, servletResponse) -> called.set(true));
        SecurityContextHolder.clearContext();
        return new Response(response, called);
    }

    private record Response(MockHttpServletResponse response, AtomicBoolean chainInvoked) { }

    private User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole("ROLE_ADMIN");
        user.setName(username);
        return user;
    }
}
