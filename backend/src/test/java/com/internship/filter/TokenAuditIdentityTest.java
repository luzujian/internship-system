package com.internship.filter;

import com.internship.config.SystemSettingsConfig;
import com.internship.entity.SecurityEventLog;
import com.internship.entity.SystemSettings;
import com.internship.service.AuditLogService;
import com.internship.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Date;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class TokenAuditIdentityTest {
    @Test void allVerifiedRolesAndIdsSurviveSessionTimeout() throws Exception {
        var old = SystemSettingsConfig.getCurrentSettings(); var settings = new SystemSettings(); settings.setSessionTimeout(120);
        ReflectionTestUtils.setField(SystemSettingsConfig.class, "currentSettings", settings);
        try {
            for (String role : new String[]{"ROLE_ADMIN", "ROLE_STUDENT", "ROLE_COMPANY", "ROLE_TEACHER_COUNSELOR"}) {
                var filter = new TokenFilter(); var jwt = mock(JwtUtils.class); var claims = mock(Claims.class); var audit = mock(AuditLogService.class);
                ReflectionTestUtils.setField(filter,"jwtUtils",jwt); ReflectionTestUtils.setField(filter,"auditLogService",audit);
                when(jwt.validateToken("test-token")).thenReturn(true); when(jwt.parseToken("test-token")).thenReturn(claims);
                when(claims.get("username",String.class)).thenReturn("test-account"); when(claims.get("role",String.class)).thenReturn(role);
                when(claims.get("typ")).thenReturn("access"); when(claims.get("userId")).thenReturn(42);
                when(claims.getIssuedAt()).thenReturn(new Date(System.currentTimeMillis()-121L*60*1000));
                var req = new MockHttpServletRequest("GET","/api/test"); req.setServletPath("/api/test"); req.addHeader("Authorization","Bearer test-token");
                var response = new MockHttpServletResponse(); var chain = mock(FilterChain.class);
                filter.doFilter(req,response,chain);
                assertEquals(401,response.getStatus()); verify(chain,never()).doFilter(any(),any());
                var capture = ArgumentCaptor.forClass(SecurityEventLog.class); verify(audit).recordSecurityEvent(capture.capture());
                assertEquals("42",capture.getValue().getUserId()); assertEquals(role.substring(5),capture.getValue().getUserRole());
            }
        } finally { ReflectionTestUtils.setField(SystemSettingsConfig.class,"currentSettings",old); }
    }

    @Test void invalidTokenDoesNotUseUnverifiedClaims() throws Exception {
        var filter = new TokenFilter(); var jwt = mock(JwtUtils.class); var audit = mock(AuditLogService.class);
        ReflectionTestUtils.setField(filter,"jwtUtils",jwt); ReflectionTestUtils.setField(filter,"auditLogService",audit);
        var req = new MockHttpServletRequest("GET","/api/test"); req.setServletPath("/api/test"); req.addHeader("Authorization","Bearer invalid-token");
        var response = new MockHttpServletResponse(); filter.doFilter(req,response,mock(FilterChain.class));
        var capture = ArgumentCaptor.forClass(SecurityEventLog.class); verify(audit).recordSecurityEvent(capture.capture());
        assertNull(capture.getValue().getUserId()); assertNull(capture.getValue().getUserRole());
        verify(jwt,never()).parseToken(anyString());
    }
}
