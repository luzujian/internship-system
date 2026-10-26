package com.internship.controller;

import com.internship.entity.TeacherUser;
import com.internship.entity.User;
import com.internship.entity.dto.CoreMetricsDTO;
import com.internship.mapper.CompanyUserMapper;
import com.internship.mapper.TeacherMacroStatisticsMapper;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
    @Mock private TeacherMacroStatisticsMapper recruitmentStatistics;
    @Mock private TeacherUserService teachers;
    @Mock private TeacherAutomationScopeMapper scopes;
    @Mock private com.internship.service.JobSearchStatusService jobSearchStatusService;

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
        verifyNoInteractions(reports, companies, students, statuses, companyService, applications, recruitmentStatistics);
    }

    @ParameterizedTest
    @ValueSource(strings = {"COUNSELOR", "DEPARTMENT", "COLLEGE"})
    void allTeacherRolesReadSchoolwideMetricsAndLists(String type) {
        teacher(type, List.of(101L), false);
        when(students.count()).thenReturn(200L);
        when(statuses.countByStatus(2)).thenReturn(20);
        when(statuses.countByStatus(3)).thenReturn(30);
        when(statuses.countByStatus(4)).thenReturn(10);
        when(jobSearchStatusService.countByDisplayFilter(0, null)).thenReturn(120L);
        when(applications.count()).thenReturn(60L);
        when(companyService.count()).thenReturn(5L);
        when(recruitmentStatistics.sumActivePositionRemainingQuota()).thenReturn(197L);
        when(reports.getStudentInternshipDetails(0, 5)).thenReturn(List.of());
        when(reports.getApprovalDetails(0, 5)).thenReturn(List.of());
        when(reports.getCompanyDetails(0, 5)).thenReturn(List.of());
        when(reports.getStudentInternshipDetailsCount()).thenReturn(12L);
        when(reports.getApprovalDetailsCount()).thenReturn(3L);
        when(reports.getCompanyDetailsCount()).thenReturn(54L);

        CoreMetricsDTO metrics = (CoreMetricsDTO) controller.getCoreMetrics("month", null, null).getData();
        assertEquals(30.0, metrics.getInternshipRate());
        assertEquals(65, metrics.getApprovalCount());
        assertEquals(120, metrics.getUnplacedStudentCount());
        assertEquals(197, metrics.getRemainingPositionCount());
        verify(recruitmentStatistics).sumActivePositionRemainingQuota();
        assertEquals(200, controller.getStudentInternshipDetails(1, 5).getCode());
        assertEquals(12L, controller.getStudentInternshipDetailsCount().getData());
        assertEquals(200, controller.getApprovalDetails(1, 5).getCode());
        assertEquals(3L, controller.getApprovalDetailsCount().getData());
        assertEquals(200, controller.getCompanyDetails(1, 5).getCode());
        assertEquals(54L, controller.getCompanyDetailsCount().getData());
        verify(reports, never()).getScopedCompanyDetails(0, 5, List.of(101L));
        verify(reports, never()).getScopedStudentInternshipDetails(0, 5, List.of(101L));
        verify(reports, never()).getScopedApprovalDetails(0, 5, List.of(101L), false);
        verifyNoInteractions(scopes);
    }

    @Test
    void teacherWithoutCompanyAuditPermissionCanReadSchoolwideApprovalSummary() {
        teacher("COLLEGE", List.of(101L), false);
        when(reports.getApprovalDetails(0, 5)).thenReturn(List.of());
        when(reports.getApprovalDetailsCount()).thenReturn(3L);
        assertEquals(200, controller.getApprovalDetails(1, 5).getCode());
        assertEquals(3L, controller.getApprovalDetailsCount().getData());
        verify(reports).getApprovalDetails(0, 5);
        verifyNoInteractions(scopes);
    }

    @Test
    void teacherWithoutCompanyAuditPermissionExportsSchoolwideApprovalSummary() throws Exception {
        teacher("COUNSELOR", List.of(), false);
        when(reports.getApprovalDetails(0, Integer.MAX_VALUE)).thenReturn(List.of());
        controller.exportApprovalReport(new MockHttpServletResponse());
        verify(reports).getApprovalDetails(0, Integer.MAX_VALUE);
        verifyNoInteractions(scopes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"COUNSELOR", "DEPARTMENT", "COLLEGE"})
    void teacherExportsUseTheSameSchoolwideQueriesAsThePage(String type) throws Exception {
        teacher(type, List.of(101L), false);
        when(students.count()).thenReturn(200L);
        when(statuses.countByStatus(2)).thenReturn(0);
        when(statuses.countByStatus(3)).thenReturn(60);
        when(statuses.countByStatus(4)).thenReturn(0);
        when(applications.count()).thenReturn(5L);
        when(jobSearchStatusService.countByDisplayFilter(0, null)).thenReturn(120L);
        when(recruitmentStatistics.sumActivePositionRemainingQuota()).thenReturn(197L);
        when(reports.getCompanyTrendByQuarter(org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of());
        when(reports.getCompanyDetails(0, Integer.MAX_VALUE)).thenReturn(List.of());
        when(reports.getStudentInternshipDetails(0, Integer.MAX_VALUE)).thenReturn(List.of());
        when(reports.getApprovalDetails(0, Integer.MAX_VALUE)).thenReturn(List.of());
        MockHttpServletResponse comprehensive = new MockHttpServletResponse();
        controller.exportReport("month", null, null, comprehensive);
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                new java.io.ByteArrayInputStream(comprehensive.getContentAsByteArray()))) {
            assertEquals("30.0%", workbook.getSheet("核心指标概览").getRow(3).getCell(1).getStringCellValue());
            assertEquals("当前可招名额", workbook.getSheet("核心指标概览").getRow(4).getCell(0).getStringCellValue());
            assertEquals("197", workbook.getSheet("核心指标概览").getRow(4).getCell(1).getStringCellValue());
            assertEquals("尚未获录用学生数量", workbook.getSheet("核心指标概览").getRow(5).getCell(0).getStringCellValue());
            assertEquals("120", workbook.getSheet("核心指标概览").getRow(5).getCell(1).getStringCellValue());
            assertEquals("申请审核数量", workbook.getSheet("核心指标概览").getRow(6).getCell(0).getStringCellValue());
            assertEquals("5", workbook.getSheet("核心指标概览").getRow(6).getCell(1).getStringCellValue());
        }
        controller.exportCompanyReport(new MockHttpServletResponse());
        controller.exportStudentReport(new MockHttpServletResponse());
        controller.exportApprovalReport(new MockHttpServletResponse());
        verify(reports, org.mockito.Mockito.times(2)).getCompanyDetails(0, Integer.MAX_VALUE);
        verify(reports, org.mockito.Mockito.times(2)).getStudentInternshipDetails(0, Integer.MAX_VALUE);
        verify(reports, org.mockito.Mockito.times(2)).getApprovalDetails(0, Integer.MAX_VALUE);
        verifyNoInteractions(scopes);
    }

    @Test
    void disabledTeacherStillCannotReadAnyReportSummary() {
        teacher("COLLEGE", List.of(101L), false).setStatus(0);
        assertThrows(AccessDeniedException.class, () -> controller.getStudentInternshipDetails(1, 5));
        verifyNoInteractions(reports);
    }

    @Test
    void validTeacherWithoutAssignedDepartmentCanReadSchoolwideSummary() {
        teacher("COLLEGE", null, false).setDepartmentId(null);
        when(reports.getStudentInternshipDetails(0, 5)).thenReturn(List.of());
        assertEquals(200, controller.getStudentInternshipDetails(1, 5).getCode());
        verifyNoInteractions(scopes);
    }

    @Test
    void accountIdentityMismatchStillFailsBeforeReadingReports() {
        teacher("DEPARTMENT", List.of(101L), false).setTeacherUserId("another-teacher");
        assertThrows(AccessDeniedException.class, () -> controller.getCompanyDetails(1, 5));
        verifyNoInteractions(reports);
    }

    @Test
    void roleMismatchFailsBeforeReadingReports() {
        User actor = new User(); actor.setId(23L); actor.setUsername("T0023");
        actor.setRole("ROLE_TEACHER_COLLEGE"); CurrentHolder.setUser(actor);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                actor, null, List.of(new SimpleGrantedAuthority("ROLE_TEACHER_DEPARTMENT"))));
        assertThrows(AccessDeniedException.class, () -> controller.getCompanyDetails(1, 5));
        verifyNoInteractions(reports, teachers);
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
        when(reports.getApprovalDetailsCount()).thenReturn(3L);

        assertEquals(200, controller.getStudentInternshipDetails(1, 5).getCode());
        assertEquals(3L, controller.getApprovalDetailsCount().getData());
        verify(reports).getStudentInternshipDetails(0, 5);
        verify(reports).getApprovalDetailsCount();
        verifyNoInteractions(teachers, scopes);
    }
}
