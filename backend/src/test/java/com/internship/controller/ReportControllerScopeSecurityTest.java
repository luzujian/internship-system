package com.internship.controller;

import com.internship.entity.TeacherUser;
import com.internship.entity.User;
import com.internship.entity.dto.CoreMetricsDTO;
import com.internship.mapper.CompanyUserMapper;
import com.internship.mapper.PositionMapper;
import com.internship.mapper.ReportMapper;
import com.internship.mapper.StudentApplicationMapper;
import com.internship.mapper.StudentInternshipStatusMapper;
import com.internship.mapper.StudentUserMapper;
import com.internship.mapper.TeacherAutomationScopeMapper;
import com.internship.service.CompanyUserService;
import com.internship.service.TeacherUserService;
import com.internship.utils.CurrentHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportControllerScopeSecurityTest {
    @InjectMocks private ReportController controller;
    @Mock private ReportMapper reports;
    @Mock private CompanyUserMapper companies;
    @Mock private StudentUserMapper students;
    @Mock private StudentInternshipStatusMapper statuses;
    @Mock private CompanyUserService companyService;
    @Mock private StudentApplicationMapper applications;
    @Mock private PositionMapper positions;
    @Mock private TeacherUserService teachers;
    @Mock private TeacherAutomationScopeMapper scopes;

    @AfterEach
    void clearIdentity() {
        CurrentHolder.remove();
        SecurityContextHolder.clearContext();
    }

    private TeacherUser teacher(String type, List<Long> studentIds, boolean auditCompanies) {
        User actor = new User();
        actor.setId(23L);
        actor.setUsername("T0023");
        actor.setRole("ROLE_TEACHER_" + type);
        CurrentHolder.setUser(actor);
        var authorities = new java.util.ArrayList<SimpleGrantedAuthority>();
        authorities.add(new SimpleGrantedAuthority(actor.getRole()));
        if (auditCompanies) authorities.add(new SimpleGrantedAuthority("user:company:audit"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                actor, null, authorities));

        TeacherUser teacher = new TeacherUser();
        teacher.setId(23L);
        teacher.setTeacherUserId("T0023");
        teacher.setTeacherType(type);
        teacher.setStatus(1);
        teacher.setDepartmentId("5");
        teacher.setDivisionId("9");
        when(teachers.findById(23L)).thenReturn(teacher);
        when(scopes.studentIds(teacher)).thenReturn(studentIds);
        return teacher;
    }

    @Test
    void everyReportRouteDeniesMissingIdentityBeforeQueries() {
        assertThrows(AccessDeniedException.class, () -> controller.getCoreMetrics("month", null, null));
        assertThrows(AccessDeniedException.class, () -> controller.getCompanyTrend(2026));
        assertThrows(AccessDeniedException.class, () -> controller.getCompanyDetails(1, 5));
        assertThrows(AccessDeniedException.class, controller::getCompanyDetailsCount);
        assertThrows(AccessDeniedException.class, () -> controller.getStudentInternshipDetails(1, 5));
        assertThrows(AccessDeniedException.class, controller::getStudentInternshipDetailsCount);
        assertThrows(AccessDeniedException.class, () -> controller.getApprovalDetails(1, 5));
        assertThrows(AccessDeniedException.class, controller::getApprovalDetailsCount);
        assertThrows(AccessDeniedException.class, () -> controller.exportReport("month", null, null,
                new MockHttpServletResponse()));
        assertThrows(AccessDeniedException.class, () -> controller.exportCompanyReport(new MockHttpServletResponse()));
        assertThrows(AccessDeniedException.class, () -> controller.exportStudentReport(new MockHttpServletResponse()));
        assertThrows(AccessDeniedException.class, () -> controller.exportApprovalReport(new MockHttpServletResponse()));
        verifyNoInteractions(reports, companies, students, statuses, companyService, applications, positions);
    }

    @Test
    void teacherMetricsAndListsUseOnlyScopedStudents() {
        teacher("COUNSELOR", List.of(101L, 102L), false);
        when(reports.countScopedStudents(List.of(101L, 102L))).thenReturn(2L);
        when(reports.countScopedInternshipStudents(List.of(101L, 102L))).thenReturn(1L);
        when(reports.countScopedApplications(List.of(101L, 102L))).thenReturn(1L);
        when(positions.findAll()).thenReturn(List.of());
        when(reports.getScopedStudentInternshipDetails(0, 5, List.of(101L, 102L))).thenReturn(List.of());
        when(reports.getScopedApprovalDetails(0, 5, List.of(101L, 102L), false)).thenReturn(List.of());
        when(reports.getScopedCompanyDetails(0, 5, List.of(101L, 102L))).thenReturn(List.of());
        when(reports.getScopedStudentInternshipDetailsCount(List.of(101L, 102L))).thenReturn(1L);
        when(reports.getScopedApprovalDetailsCount(List.of(101L, 102L), false)).thenReturn(3L);
        when(reports.getCompanyDetailsCount()).thenReturn(2L);

        CoreMetricsDTO metrics = (CoreMetricsDTO) controller.getCoreMetrics("month", null, null).getData();
        assertEquals(50.0, metrics.getInternshipRate());
        assertEquals(1, metrics.getApprovalCount());
        assertEquals(1, metrics.getUnplacedStudentCount());
        assertEquals(200, controller.getStudentInternshipDetails(1, 5).getCode());
        assertEquals(1L, controller.getStudentInternshipDetailsCount().getData());
        assertEquals(200, controller.getApprovalDetails(1, 5).getCode());
        assertEquals(3L, controller.getApprovalDetailsCount().getData());
        assertEquals(200, controller.getCompanyDetails(1, 5).getCode());
        assertEquals(2L, controller.getCompanyDetailsCount().getData());

        verify(reports, never()).getStudentInternshipDetails(0, 5);
        verify(reports, never()).getApprovalDetails(0, 5);
        verify(reports, never()).getCompanyDetails(0, 5);
        verify(companyService, never()).count();
        verifyNoInteractions(students, statuses, applications);
    }

    @Test
    void teacherWithCompanyAuditPermissionIncludesCompanyApprovalRowAndCount() {
        teacher("COLLEGE", List.of(101L), true);
        when(reports.getScopedApprovalDetails(0, 5, List.of(101L), true)).thenReturn(List.of());
        when(reports.getScopedApprovalDetailsCount(List.of(101L), true)).thenReturn(4L);

        assertEquals(200, controller.getApprovalDetails(1, 5).getCode());
        assertEquals(4L, controller.getApprovalDetailsCount().getData());
        verify(reports).getScopedApprovalDetails(0, 5, List.of(101L), true);
        verify(reports).getScopedApprovalDetailsCount(List.of(101L), true);
        verify(reports, never()).getApprovalDetails(0, 5);
        verify(reports, never()).getApprovalDetailsCount();
    }

    @Test
    void teacherWithoutCompanyAuditPermissionExportsOnlyStudentApprovalRows() throws Exception {
        teacher("COUNSELOR", List.of(101L), false);
        when(reports.getScopedApprovalDetails(0, 10000, List.of(101L), false)).thenReturn(List.of());

        controller.exportApprovalReport(new MockHttpServletResponse());

        verify(reports).getScopedApprovalDetails(0, 10000, List.of(101L), false);
        verify(reports, never()).getScopedApprovalDetails(0, 10000, List.of(101L), true);
        verify(reports, never()).getApprovalDetails(0, 10000);
    }

    @Test
    void teacherExportsNeverReadGlobalStudentOrAdmissionQueries() throws Exception {
        teacher("DEPARTMENT", List.of(101L), true);
        when(reports.countScopedStudents(List.of(101L))).thenReturn(1L);
        when(reports.countScopedInternshipStudents(List.of(101L))).thenReturn(1L);
        when(reports.countScopedApplications(List.of(101L))).thenReturn(1L);
        when(reports.getCompanyTrendByQuarter(org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of());
        when(reports.getScopedCompanyDetails(0, 1000, List.of(101L))).thenReturn(List.of());
        when(reports.getScopedStudentInternshipDetails(0, 1000, List.of(101L))).thenReturn(List.of());
        when(reports.getScopedApprovalDetails(0, 1000, List.of(101L), true)).thenReturn(List.of());
        when(reports.getScopedCompanyDetails(0, 10000, List.of(101L))).thenReturn(List.of());
        when(reports.getScopedStudentInternshipDetails(0, 10000, List.of(101L))).thenReturn(List.of());
        when(reports.getScopedApprovalDetails(0, 10000, List.of(101L), true)).thenReturn(List.of());

        controller.exportReport("month", null, null, new MockHttpServletResponse());
        controller.exportCompanyReport(new MockHttpServletResponse());
        controller.exportStudentReport(new MockHttpServletResponse());
        controller.exportApprovalReport(new MockHttpServletResponse());

        verify(reports).getScopedCompanyDetails(0, 1000, List.of(101L));
        verify(reports).getScopedStudentInternshipDetails(0, 1000, List.of(101L));
        verify(reports).getScopedApprovalDetails(0, 1000, List.of(101L), true);
        verify(reports, never()).getStudentInternshipDetails(0, 1000);
        verify(reports, never()).getApprovalDetails(0, 1000);
        verify(reports, never()).getCompanyDetails(0, 1000);
        verifyNoInteractions(students, statuses, applications);
    }

    @Test
    void missingTeacherScopeFailsClosedAndDoesNotUseAdministratorQueries() {
        teacher("COLLEGE", null, false);
        assertThrows(AccessDeniedException.class, () -> controller.getStudentInternshipDetails(1, 5));
        verifyNoInteractions(reports);
    }

    @Test
    void administratorRetainsExistingGlobalReportPath() {
        User admin = new User();
        admin.setId(1L);
        admin.setRole("ROLE_ADMIN");
        CurrentHolder.setUser(admin);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                admin, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        when(reports.getStudentInternshipDetails(0, 5)).thenReturn(List.of());
        when(reports.getApprovalDetailsCount()).thenReturn(4L);

        assertEquals(200, controller.getStudentInternshipDetails(1, 5).getCode());
        assertEquals(4L, controller.getApprovalDetailsCount().getData());
        verify(reports).getStudentInternshipDetails(0, 5);
        verify(reports).getApprovalDetailsCount();
        verifyNoInteractions(teachers, scopes);
    }
}
