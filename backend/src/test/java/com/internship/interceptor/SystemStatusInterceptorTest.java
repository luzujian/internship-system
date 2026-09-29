package com.internship.interceptor;

import com.internship.config.SystemSettingsConfig;
import com.internship.entity.SystemSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemStatusInterceptorTest {
    private final SystemStatusInterceptor interceptor = new SystemStatusInterceptor();
    private SystemSettings previousSettings;
    private SystemSettings settings;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        previousSettings = SystemSettingsConfig.getCurrentSettings();
        settings = new SystemSettings();
        settings.setSystemStatus(0);
        ReflectionTestUtils.setField(SystemSettingsConfig.class, "currentSettings", settings);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        ReflectionTestUtils.setField(SystemSettingsConfig.class, "currentSettings", previousSettings);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin/dashboard", "/api/auth/user-info", "/api/announcements"})
    void authenticatedAdminCanUseBackendDuringMaintenance(String path) throws Exception {
        authenticate("ROLE_ADMIN");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(new MockHttpServletRequest("GET", path), response, new Object()));
        assertEquals(200, response.getStatus());
        assertEquals("", response.getContentAsString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_STUDENT", "ROLE_TEACHER_COLLEGE", "ROLE_COMPANY", "ROLE_ADMIN_FAKE"})
    void otherRolesStillReceiveMaintenanceResponse(String role) throws Exception {
        authenticate(role);
        assertMaintenanceBlocked(new MockHttpServletRequest("GET", "/api/admin/dashboard"));
    }

    @Test
    void clientSuppliedRoleCannotBypassMaintenance() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/announcements");
        request.addHeader("role", "ROLE_ADMIN");
        request.addParameter("role", "ROLE_ADMIN");
        assertMaintenanceBlocked(request);
    }

    @Test
    void unauthenticatedAdminAuthorityCannotBypassMaintenance() throws Exception {
        var authentication = new UsernamePasswordAuthenticationToken("admin", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        authentication.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        assertMaintenanceBlocked(new MockHttpServletRequest("GET", "/api/admin/dashboard"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/auth/login", "/api/auth/refresh-token"})
    void authenticationEndpointsRemainReachableDuringMaintenance(String path) throws Exception {
        assertTrue(interceptor.preHandle(new MockHttpServletRequest("POST", path),
                new MockHttpServletResponse(), new Object()));
    }

    @Test
    void refreshExemptionDoesNotCoverOtherPathsOrMethods() throws Exception {
        assertMaintenanceBlocked(new MockHttpServletRequest("POST", "/api/auth/refresh-token/other"));
        assertMaintenanceBlocked(new MockHttpServletRequest("GET", "/api/auth/refresh-token"));
    }

    @Test
    void adminCanTurnMaintenanceOff() throws Exception {
        authenticate("ROLE_ADMIN");
        assertTrue(interceptor.preHandle(new MockHttpServletRequest("PUT", "/api/admin/settings"),
                new MockHttpServletResponse(), new Object()));
    }

    @Test
    void normalOperationAllowsNonAdminRequestsAgain() throws Exception {
        authenticate("ROLE_STUDENT");
        settings.setSystemStatus(1);
        assertTrue(interceptor.preHandle(new MockHttpServletRequest("GET", "/api/announcements"),
                new MockHttpServletResponse(), new Object()));
    }

    private void authenticate(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "test-user", null, List.of(new SimpleGrantedAuthority(role))));
    }

    private void assertMaintenanceBlocked(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(503, response.getStatus());
        assertTrue(response.getContentAsString().contains("系统维护中，暂时无法访问"));
    }
}
