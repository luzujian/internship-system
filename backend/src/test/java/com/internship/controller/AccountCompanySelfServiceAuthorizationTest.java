package com.internship.controller;

import com.internship.entity.CompanyUser;
import com.internship.entity.InternshipApplicationEntity;
import com.internship.entity.Result;
import com.internship.entity.TeacherUser;
import com.internship.entity.User;
import com.internship.service.CompanyUserService;
import com.internship.service.InternshipApplicationService;
import com.internship.service.TeacherUserService;
import com.internship.utils.CurrentHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AccountCompanySelfServiceAuthorizationTest {

    @AfterEach
    void clearIdentity() {
        CurrentHolder.remove();
        SecurityContextHolder.clearContext();
    }

    @Test
    void studentWithMatchingTeacherIdCannotReadOrChangeTeacherAccount() {
        TeacherUserService teacherService = mock(TeacherUserService.class);
        AccountSettingsController controller = teacherController(teacherService);
        identity("ROLE_STUDENT", 7L);

        assertThrows(AccessDeniedException.class, () -> controller.getAccountInfo(null));
        assertThrows(AccessDeniedException.class,
                () -> controller.updateContact(Map.of("email", "other@example.test"), null));
        assertThrows(AccessDeniedException.class,
                () -> controller.changePassword(Map.of("oldPassword", "x"), null));
        verifyNoInteractions(teacherService);
    }

    @Test
    void teacherSubroleReadsAndUpdatesOnlyCurrentTeacherId() {
        TeacherUserService teacherService = mock(TeacherUserService.class);
        AccountSettingsController controller = teacherController(teacherService);
        identity("ROLE_TEACHER_COUNSELOR", 7L);
        TeacherUser teacher = new TeacherUser();
        teacher.setId(7L);
        teacher.setEmail("old@example.test");
        when(teacherService.findById(7L)).thenReturn(teacher);

        assertEquals(200, controller.getAccountInfo(null).getCode());
        assertEquals(200, controller.updateContact(Map.of("email", "new@example.test"), null).getCode());
        assertEquals("new@example.test", teacher.getEmail());
        verify(teacherService).update(teacher);
    }

    @Test
    void nonCompanyWithMatchingCompanyIdCannotReadOrChangeCompanyProfile() {
        CompanyUserService companyService = mock(CompanyUserService.class);
        InternshipApplicationService applicationService = mock(InternshipApplicationService.class);
        CompanyController controller = companyController(companyService, applicationService);
        identity("ROLE_STUDENT", 7L);

        assertThrows(AccessDeniedException.class, controller::getProfile);
        assertThrows(AccessDeniedException.class,
                () -> controller.updateProfile(Map.of("email", "other@example.test")));
        assertThrows(AccessDeniedException.class, () -> controller.getCompanyInfo(7L));
        assertThrows(AccessDeniedException.class, () -> controller.getRecentApplications(7L, 5));
        assertThrows(AccessDeniedException.class, () -> controller.markApplicationAsViewed(10L));
        verifyNoInteractions(companyService, applicationService);
    }

    @Test
    void companyCanReadOwnInfoAndRecentApplicationsButNotOtherCompany() {
        CompanyUserService companyService = mock(CompanyUserService.class);
        InternshipApplicationService applicationService = mock(InternshipApplicationService.class);
        CompanyController controller = companyController(companyService, applicationService);
        identity("ROLE_COMPANY", 7L);
        CompanyUser company = new CompanyUser();
        company.setId(7L);
        company.setCompanyName("测试企业");
        when(companyService.findById(7L)).thenReturn(company);
        when(applicationService.findByCompanyId(7L)).thenReturn(List.of());

        Result ownInfo = controller.getCompanyInfo(7L);
        assertEquals(200, ownInfo.getCode());
        assertEquals(7L, ((Map<?, ?>) ownInfo.getData()).get("id"));
        assertEquals(200, controller.getRecentApplications(null, 5).getCode());
        assertEquals(200, controller.getRecentApplications(7L, 5).getCode());
        assertThrows(AccessDeniedException.class, () -> controller.getCompanyInfo(8L));
        assertThrows(AccessDeniedException.class, () -> controller.getRecentApplications(8L, 5));
        verify(companyService).findById(7L);
        verify(applicationService, org.mockito.Mockito.times(2)).findByCompanyId(7L);
    }

    @Test
    void companyCannotMarkOtherCompanyApplicationAsViewed() {
        CompanyUserService companyService = mock(CompanyUserService.class);
        InternshipApplicationService applicationService = mock(InternshipApplicationService.class);
        CompanyController controller = companyController(companyService, applicationService);
        identity("ROLE_COMPANY", 7L);
        InternshipApplicationEntity otherApplication = new InternshipApplicationEntity();
        otherApplication.setId(10L);
        otherApplication.setCompanyId(8L);
        when(applicationService.findById(10L)).thenReturn(otherApplication);

        assertThrows(AccessDeniedException.class, () -> controller.markApplicationAsViewed(10L));
        verify(applicationService, never()).markAsViewed(10L);
    }

    @Test
    void companyCanMarkOwnApplicationAsViewed() {
        CompanyUserService companyService = mock(CompanyUserService.class);
        InternshipApplicationService applicationService = mock(InternshipApplicationService.class);
        CompanyController controller = companyController(companyService, applicationService);
        identity("ROLE_COMPANY", 7L);
        InternshipApplicationEntity ownApplication = new InternshipApplicationEntity();
        ownApplication.setId(10L);
        ownApplication.setCompanyId(7L);
        when(applicationService.findById(10L)).thenReturn(ownApplication);
        when(applicationService.markAsViewed(10L)).thenReturn(true);

        assertEquals(200, controller.markApplicationAsViewed(10L).getCode());
        verify(applicationService).markAsViewed(10L);
    }

    @Test
    void movedPositionDoesNotExposeFormerCompanyApplications() {
        CompanyUserService companyService = mock(CompanyUserService.class);
        InternshipApplicationService applicationService = mock(InternshipApplicationService.class);
        CompanyController controller = companyController(companyService, applicationService);
        identity("ROLE_COMPANY", 7L);
        InternshipApplicationEntity ownApplication = new InternshipApplicationEntity();
        ownApplication.setId(10L);
        ownApplication.setCompanyId(7L);
        InternshipApplicationEntity formerCompanyApplication = new InternshipApplicationEntity();
        formerCompanyApplication.setId(11L);
        formerCompanyApplication.setCompanyId(8L);
        // The mapper currently joins on the position's present company, so a
        // moved position can return an application owned by its former company.
        when(applicationService.findByCompanyId(7L)).thenReturn(List.of(ownApplication, formerCompanyApplication));

        Result recent = controller.getRecentApplications(7L, 5);
        List<?> recentRows = (List<?>) recent.getData();
        assertEquals(1, recentRows.size());
        assertEquals(10L, ((Map<?, ?>) recentRows.get(0)).get("id"));

        Result all = controller.getCurrentCompanyApplications();
        List<?> allRows = (List<?>) all.getData();
        assertEquals(1, allRows.size());
        assertEquals(10L, ((InternshipApplicationEntity) allRows.get(0)).getId());
    }

    private AccountSettingsController teacherController(TeacherUserService teacherService) {
        AccountSettingsController target = new AccountSettingsController();
        ReflectionTestUtils.setField(target, "teacherUserService", teacherService);
        return secured(target, AccountSettingsController.class);
    }

    private CompanyController companyController(CompanyUserService companyService,
                                                InternshipApplicationService applicationService) {
        CompanyController target = new CompanyController();
        ReflectionTestUtils.setField(target, "companyUserService", companyService);
        ReflectionTestUtils.setField(target, "internshipApplicationService", applicationService);
        return secured(target, CompanyController.class);
    }

    private <T> T secured(T target, Class<T> type) {
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvisor(AuthorizationManagerBeforeMethodInterceptor.preAuthorize());
        return type.cast(proxyFactory.getProxy());
    }

    private void identity(String role, Long id) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        CurrentHolder.setUser(user);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "test", null, List.of(new SimpleGrantedAuthority(role))));
    }
}
