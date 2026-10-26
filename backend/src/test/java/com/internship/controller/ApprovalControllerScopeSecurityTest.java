package com.internship.controller;

import com.internship.entity.CompanyUser;
import com.internship.entity.PageResult;
import com.internship.entity.StudentApplication;
import com.internship.entity.TeacherUser;
import com.internship.entity.User;
import com.internship.mapper.TeacherAutomationScopeMapper;
import com.internship.service.CompanyTagService;
import com.internship.service.CompanyUserService;
import com.internship.service.EmailService;
import com.internship.service.StudentApplicationService;
import com.internship.service.TeacherUserService;
import com.internship.utils.CurrentHolder;
import com.internship.utils.AliyunOSSOperator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalControllerScopeSecurityTest {
    @InjectMocks private ApprovalController controller;
    @Mock private StudentApplicationService applications;
    @Mock private CompanyUserService companies;
    @Mock private EmailService emails;
    @Mock private CompanyTagService companyTags;
    @Mock private TeacherUserService teachers;
    @Mock private TeacherAutomationScopeMapper scope;
    @Mock private AliyunOSSOperator oss;

    @AfterEach
    void clearIdentity() {
        CurrentHolder.remove();
        SecurityContextHolder.clearContext();
    }

    private TeacherUser teacher(String type, boolean companyAudit) {
        User actor = new User();
        actor.setId(23L);
        actor.setUsername("T0023");
        actor.setRole("ROLE_TEACHER_" + type);
        CurrentHolder.setUser(actor);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                actor, null, companyAudit ? List.of(new SimpleGrantedAuthority("user:company:audit")) : List.of()));

        TeacherUser teacher = new TeacherUser();
        teacher.setId(23L);
        teacher.setTeacherUserId("T0023");
        teacher.setTeacherType(type);
        teacher.setStatus(1);
        teacher.setDepartmentId("5");
        teacher.setDivisionId("9");
        when(teachers.findById(23L)).thenReturn(teacher);
        return teacher;
    }

    private StudentApplication application(long studentId, String type) {
        StudentApplication item = new StudentApplication();
        item.setId(71L);
        item.setStudentId(studentId);
        item.setApplicationType(type);
        item.setMaterials(Map.of("certificate", "https://oss.example/private.pdf"));
        return item;
    }

    @Test
    void applicationDetailsWithMaterialUrlsRequireStudentInScope() {
        TeacherUser counselor = teacher("COUNSELOR", false);
        when(applications.findById(71L)).thenReturn(application(501L, "selfPractice"));

        assertThrows(AccessDeniedException.class, () -> controller.getStudentApplicationById(71L));
        verify(scope).contains(counselor, 501L);

        when(scope.contains(counselor, 501L)).thenReturn(1);
        var visible = controller.getStudentApplicationById(71L);
        assertEquals(200, visible.getCode());
        assertEquals("uploaded.pdf", ((StudentApplication) visible.getData()).getMaterials().get("certificate"));
    }

    @Test
    void listAndCombinedListUseDatabaseScopeAndRestrictType() {
        TeacherUser college = teacher("COLLEGE", false);
        when(scope.studentIds(college)).thenReturn(List.of(501L));
        when(applications.findPageWithinStudentIds(1, 10, "delay", "pending", null, null, List.of(501L)))
                .thenReturn(PageResult.build(1L, List.of(application(501L, "delay"))));

        assertEquals(200, controller.getStudentApplications(1, 10, "delay", "pending", null, null, null, null).getCode());
        verify(applications).findPageWithinStudentIds(1, 10, "delay", "pending", null, null, List.of(501L));

        @SuppressWarnings("unchecked")
        Map<String, Object> combined = (Map<String, Object>) controller.getApplications(1, 10,
                "selfPractice", "pending", null).getData();
        assertEquals(0L, ((PageResult<?>) combined.get("data")).getTotal());
        verify(applications, never()).findPageWithinStudentIds(1, 10,
                "selfPractice", "pending", null, null, List.of(501L));
    }

    @Test
    void counselorListUsesOnlyConfirmedClassScopeWithoutFilters() {
        TeacherUser counselor = teacher("COUNSELOR", false);
        when(scope.studentIds(counselor)).thenReturn(List.of(501L, 502L));
        when(applications.findPageWithinStudentIds(1, 10, "selfPractice", null, null, null, List.of(501L, 502L)))
                .thenReturn(PageResult.build(2L, List.of(application(501L, "selfPractice"), application(502L, "selfPractice"))));

        PageResult<?> page = (PageResult<?>) controller.getStudentApplications(
                1, 10, "selfPractice", null, null, null, null, null).getData();

        assertEquals(2L, page.getTotal());
        verify(applications).findPageWithinStudentIds(1, 10, "selfPractice", null, null, null, List.of(501L, 502L));
    }

    @Test
    void majorAndClassFiltersUseTheNarrowedTeacherScopeWithExistingFilters() {
        TeacherUser counselor = teacher("COUNSELOR", false);
        when(scope.filteredStudentIds(counselor, 6L, 12L)).thenReturn(List.of(501L));
        when(applications.findPageWithinStudentIds(2, 5, "selfPractice", "approved", "学生", "学生", List.of(501L)))
                .thenReturn(PageResult.build(1L, List.of(application(501L, "selfPractice"))));

        assertEquals(200, controller.getStudentApplications(
                2, 5, "selfPractice", "approved", "学生", "学生", 6L, 12L).getCode());

        verify(applications).findPageWithinStudentIds(2, 5, "selfPractice", "approved", "学生", "学生", List.of(501L));
        verify(scope, never()).studentIds(counselor);
    }

    @Test
    void unclaimedClassAndCounselorWithoutClassesCannotFallBackToAllStudents() {
        TeacherUser counselor = teacher("COUNSELOR", false);
        when(scope.studentIds(counselor)).thenReturn(List.of());
        when(scope.filteredStudentIds(counselor, null, 999L)).thenReturn(List.of());
        when(applications.findPageWithinStudentIds(1, 10, "selfPractice", null, null, null, List.of()))
                .thenReturn(PageResult.build(0L, List.of()));

        PageResult<?> unclaimedClass = (PageResult<?>) controller.getStudentApplications(
                1, 10, "selfPractice", null, null, null, null, 999L).getData();
        PageResult<?> noClasses = (PageResult<?>) controller.getStudentApplications(
                1, 10, "selfPractice", null, null, null, null, null).getData();

        assertEquals(0L, unclaimedClass.getTotal());
        assertEquals(0L, noClasses.getTotal());
    }

    @Test
    void outOfScopeApprovalAndRejectionCannotWriteAndReviewerIsServerBound() {
        TeacherUser department = teacher("DEPARTMENT", false);
        when(applications.findById(71L)).thenReturn(application(501L, "delay"));

        assertThrows(AccessDeniedException.class, () -> controller.approveStudentApplication(71L, 999L));
        assertThrows(AccessDeniedException.class, () -> controller.rejectStudentApplication(71L, 999L, "reason"));
        verify(applications, never()).approve(anyLong(), anyLong());
        verify(applications, never()).reject(anyLong(), anyLong(), any());

        when(scope.contains(department, 501L)).thenReturn(1);
        when(applications.approve(71L, 23L)).thenReturn(1);
        assertEquals(200, controller.approveStudentApplication(71L, 999L).getCode());
        verify(applications).approve(71L, 23L);
    }

    @Test
    void companyQualificationNeedsExistingAuditPermissionForReadsAndWrites() throws Exception {
        teacher("COLLEGE", false);
        assertEquals("hasAuthority('user:company:audit')", ApprovalController.class
                .getMethod("getCompanyQualificationById", Long.class).getAnnotation(PreAuthorize.class).value());
        assertThrows(AccessDeniedException.class, () -> controller.getCompanyQualificationById(15L));
        assertThrows(AccessDeniedException.class, () -> controller.getCompanyQualifications(1, 10, null, null));
        assertThrows(AccessDeniedException.class, () -> controller.getApplications(1, 10,
                "companyQualification", null, null));
        assertThrows(AccessDeniedException.class, () -> controller.approveCompanyQualification(15L, 999L));
        verifyNoInteractions(companies);

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "T0023", null, List.of(new SimpleGrantedAuthority("user:company:audit"))));
        CompanyUser company = new CompanyUser();
        company.setId(15L);
        company.setBusinessLicense("https://oss.example/business-license.pdf");
        when(companies.findById(15L)).thenReturn(company);
        var visible = controller.getCompanyQualificationById(15L);
        assertEquals(200, visible.getCode());
        assertEquals("uploaded.pdf", ((CompanyUser) visible.getData()).getBusinessLicense());
        when(companies.rejectQualification(15L, 23L, "reason")).thenReturn(1);
        assertEquals(200, controller.rejectCompanyQualification(15L, 999L, "reason").getCode());
        verify(companies).rejectQualification(15L, 23L, "reason");
    }

    @Test
    void companyApprovalSendsResultEmailAfterUpdate() {
        teacher("COLLEGE", true);
        CompanyUser company = new CompanyUser();
        company.setId(15L);
        company.setCompanyName("测试企业");
        company.setContactEmail("company@example.test");
        when(companies.findById(15L)).thenReturn(company);
        when(companies.approveQualification(15L, 23L)).thenReturn(1);
        when(emails.sendEmail(eq("company@example.test"), anyString(), anyString())).thenReturn(true);

        var result = controller.approveCompanyQualification(15L, 999L);

        assertEquals(200, result.getCode());
        assertEquals(true, ((Map<?, ?>) result.getData()).get("emailNotified"));
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        InOrder order = inOrder(companies, companyTags, emails);
        order.verify(companies).approveQualification(15L, 23L);
        order.verify(companyTags).updateCompanyTag(15L);
        order.verify(emails).sendEmail(eq("company@example.test"), eq("企业注册申请审核结果通知"), body.capture());
        assertTrue(body.getValue().contains("企业注册申请已通过审核"));
    }

    @Test
    void companyRejectionSendsTeacherReasonToCompanyMailboxAfterUpdate() {
        teacher("COLLEGE", true);
        CompanyUser company = new CompanyUser();
        company.setId(15L);
        company.setCompanyName("测试企业");
        company.setContactEmail("company@example.test");
        company.setEmail("fallback@example.test");
        when(companies.findById(15L)).thenReturn(company);
        when(companies.rejectQualification(15L, 23L, "请补交完整营业执照")).thenReturn(1);
        when(emails.sendEmail(eq("company@example.test"), anyString(), anyString())).thenReturn(true);

        var result = controller.rejectCompanyQualification(15L, 999L, " 请补交完整营业执照 ");

        assertEquals(200, result.getCode());
        assertEquals(true, ((Map<?, ?>) result.getData()).get("emailNotified"));
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        InOrder order = inOrder(companies, emails);
        order.verify(companies).rejectQualification(15L, 23L, "请补交完整营业执照");
        order.verify(emails).sendEmail(eq("company@example.test"), eq("企业注册申请审核结果通知"), body.capture());
        assertTrue(body.getValue().contains("请补交完整营业执照"));
    }

    @Test
    void companyRejectionStillCompletesButReportsEmailFailure() {
        teacher("COLLEGE", true);
        CompanyUser company = new CompanyUser();
        company.setId(15L);
        company.setCompanyName("测试企业");
        company.setContactEmail("company@example.test");
        when(companies.findById(15L)).thenReturn(company);
        when(companies.rejectQualification(15L, 23L, "材料不完整")).thenReturn(1);
        when(emails.sendEmail(eq("company@example.test"), anyString(), anyString())).thenReturn(false);

        var result = controller.rejectCompanyQualification(15L, 999L, "材料不完整");

        assertEquals(200, result.getCode());
        assertFalse((Boolean) ((Map<?, ?>) result.getData()).get("emailNotified"));
        assertTrue(result.getMessage().contains("邮件通知未完成"));
        verify(companies).rejectQualification(15L, 23L, "材料不完整");
    }

    @Test
    void identityMismatchFailsClosedBeforeAnyApplicationRead() {
        teacher("COUNSELOR", false);
        CurrentHolder.getUser().setUsername("other-teacher");
        assertThrows(AccessDeniedException.class, () -> controller.getStudentApplicationById(71L));
        verifyNoInteractions(applications, scope);
    }

    @Test
    void materialReadChecksTeacherScopeBeforeOssAndBindsStudentId() throws Exception {
        TeacherUser counselor = teacher("COUNSELOR", false);
        StudentApplication item = application(501L, "selfPractice");
        when(applications.findById(71L)).thenReturn(item);
        assertThrows(AccessDeniedException.class,
                () -> controller.downloadStudentApplicationMaterial(71L, "certificate"));
        verifyNoInteractions(oss);

        when(scope.contains(counselor, 501L)).thenReturn(1);
        when(oss.downloadRecordAttachment("https://oss.example/private.pdf", 501L))
                .thenReturn(new byte[] {1, 2});
        assertEquals(200, controller.downloadStudentApplicationMaterial(71L, "certificate").getStatusCode().value());
        verify(oss).downloadRecordAttachment("https://oss.example/private.pdf", 501L);
    }

    @Test
    void companyMaterialReadRequiresAuditAuthorityAndSelectsOnlyRecordField() throws Exception {
        teacher("COLLEGE", false);
        assertEquals("hasAuthority('user:company:audit')", ApprovalController.class
                .getMethod("downloadCompanyQualificationMaterial", Long.class, String.class, int.class)
                .getAnnotation(PreAuthorize.class).value());
        assertThrows(AccessDeniedException.class,
                () -> controller.downloadCompanyQualificationMaterial(15L, "businessLicense", 0));
        verifyNoInteractions(companies, oss);

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "T0023", null, List.of(new SimpleGrantedAuthority("user:company:audit"))));
        CompanyUser company = new CompanyUser();
        company.setBusinessLicense("https://oss.example/license.png");
        when(companies.findById(15L)).thenReturn(company);
        assertEquals(404, controller.downloadCompanyQualificationMaterial(15L, "other", 0).getStatusCode().value());
        verifyNoInteractions(oss);
        when(oss.downloadRecordAttachment(company.getBusinessLicense(), null)).thenReturn(new byte[] {3});
        assertEquals(200, controller.downloadCompanyQualificationMaterial(15L, "businessLicense", 0).getStatusCode().value());
    }
}
