package com.internship.controller;

import com.internship.entity.AdminUser;
import com.internship.entity.User;
import com.internship.service.AdminUserService;
import com.internship.service.UserCacheService;
import com.internship.utils.CurrentHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminProfileTest {
    private final AdminUserService service = mock(AdminUserService.class);
    private final UserCacheService cache = mock(UserCacheService.class);
    private final AdminUserController target = new AdminUserController();

    AdminProfileTest() {
        ReflectionTestUtils.setField(target, "adminUserService", service);
        ReflectionTestUtils.setField(target, "userCacheService", cache);
    }

    @AfterEach void clear() { CurrentHolder.remove(); SecurityContextHolder.clearContext(); }

    private void login(String role) {
        User actor = new User(); actor.setId(7L); actor.setRole(role); CurrentHolder.setUser(actor);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin-profile", null, List.of(new SimpleGrantedAuthority(role))));
    }

    private AdminUser existing() {
        AdminUser admin = new AdminUser(7L); admin.setUsername("admin-profile"); admin.setName("原姓名");
        admin.setAdminLevel(3); admin.setPassword("old-hash"); admin.setEmail("before@example.com");
        when(service.findById(7L)).thenReturn(admin);
        return admin;
    }

    @Test void ownProfileNeedsAdminRoleWithoutAdminManagementPermission() {
        login("ROLE_ADMIN"); existing();
        ProxyFactory proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvisor(AuthorizationManagerBeforeMethodInterceptor.preAuthorize());
        AdminUserController controller = (AdminUserController) proxy.getProxy();
        AdminUser result = (AdminUser) controller.getCurrentProfile().getData();
        assertEquals("before@example.com", result.getEmail()); assertNull(result.getPassword());
        login("ROLE_STUDENT");
        assertThrows(AccessDeniedException.class, controller::getCurrentProfile);
        assertThrows(AccessDeniedException.class, () -> controller.updateCurrentProfile(
                new AdminUserController.ProfileUpdate("admin-profile", "", "new@example.com")));
    }

    @Test void updateIsBoundToCurrentAdminAndPreservesProtectedFields() {
        login("ROLE_ADMIN"); AdminUser admin = existing();
        assertEquals(200, target.updateCurrentProfile(new AdminUserController.ProfileUpdate(
                "admin-profile", "", "  new@example.com  ")).getCode());
        verify(service).update(admin);
        assertEquals(7L, admin.getId()); assertEquals("new@example.com", admin.getEmail());
        assertEquals("原姓名", admin.getName()); assertEquals(3, admin.getAdminLevel());
        assertEquals(1, admin.getStatus()); assertEquals("ROLE_ADMIN", admin.getRole()); assertNull(admin.getPassword());
        verify(service, never()).findById(8L);
    }

    @Test void emailCanBeClearedAndDisabledAdminCannotEdit() {
        login("ROLE_ADMIN"); AdminUser admin = existing();
        target.updateCurrentProfile(new AdminUserController.ProfileUpdate("admin-profile", "", ""));
        assertEquals("", admin.getEmail());
        admin.setStatus(0); clearInvocations(service);
        assertEquals(400, target.updateCurrentProfile(new AdminUserController.ProfileUpdate("admin-profile", "", "new@example.com")).getCode());
        verify(service, never()).update(any());
    }

    @Test void invalidEmailIsRejectedBeforePersistence() throws Exception {
        login("ROLE_ADMIN");
        var mvc = MockMvcBuilders.standaloneSetup(target).build();
        mvc.perform(put("/api/admin/admins/me").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin-profile\",\"phone\":\"\",\"email\":\"bad-address\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
