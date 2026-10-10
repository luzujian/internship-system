package com.internship.service.impl;

import com.internship.entity.PageResult;
import com.internship.entity.TeacherUser;
import com.internship.mapper.AnnouncementMapper;
import com.internship.mapper.ClassCounselorRelationMapper;
import com.internship.mapper.CompanyUserMapper;
import com.internship.mapper.DepartmentMapper;
import com.internship.mapper.DivisionMapper;
import com.internship.mapper.StudentApplicationMapper;
import com.internship.mapper.StudentInternshipStatusMapper;
import com.internship.mapper.TeacherAutomationScopeMapper;
import com.internship.mapper.TeacherUserMapper;
import com.internship.service.CompanyUserService;
import com.internship.service.StudentApplicationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeServiceImplPendingApprovalTest {
    @InjectMocks private HomeServiceImpl service;
    @Mock private TeacherUserMapper teachers;
    @Mock private TeacherAutomationScopeMapper scope;
    @Mock private StudentApplicationService applications;
    @Mock private StudentApplicationMapper applicationMapper;
    @Mock private CompanyUserService companies;
    @Mock private CompanyUserMapper companyMapper;
    @Mock private StudentInternshipStatusMapper statuses;
    @Mock private ClassCounselorRelationMapper classes;
    @Mock private DivisionMapper divisions;
    @Mock private DepartmentMapper departments;
    @Mock private AnnouncementMapper announcements;

    @BeforeEach
    void emptyAnnouncements() {
        when(announcements.findAll()).thenReturn(new ArrayList<>());
    }

    @AfterEach
    void clearIdentity() {
        SecurityContextHolder.clearContext();
    }

    private TeacherUser teacher(String type, boolean companyAudit) {
        TeacherUser teacher = new TeacherUser();
        teacher.setId(199L);
        teacher.setTeacherType(type);
        teacher.setStatus(1);
        teacher.setDivisionId("15");
        teacher.setDepartmentId("24");
        when(teachers.findById(199L)).thenReturn(teacher);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "teacher", null, companyAudit
                ? List.of(new SimpleGrantedAuthority("user:company:audit")) : List.of()));
        return teacher;
    }

    @ParameterizedTest
    @CsvSource({
            "DEPARTMENT, true, delay, 2, 3",
            "DEPARTMENT, false, delay, 2, 2",
            "COLLEGE, true, delay, 3, 4",
            "COLLEGE, false, delay, 3, 3",
            "COUNSELOR, true, selfPractice, 7, 7",
            "COUNSELOR, false, selfPractice, 7, 7"
    })
    void countsOnlyVisibleApprovalTypesWithinTeacherScope(String type, boolean companyAudit,
            String applicationType, long studentCount, int expected) {
        TeacherUser teacher = teacher(type, companyAudit);
        List<Long> studentIds = List.of(101L, 102L);
        when(scope.studentIds(teacher)).thenReturn(studentIds);
        when(applications.findPageWithinStudentIds(1, 1, applicationType, "pending", null, null, studentIds))
                .thenReturn(PageResult.build(studentCount, List.of()));
        if (companyAudit && !"COUNSELOR".equals(type)) {
            when(companies.countByAuditStatus(0)).thenReturn(1L);
        }

        var result = service.getHomeStats(199L, type, "2026-09-01", "2027-09-01");

        assertEquals(expected, result.getPendingApprovalCount());
        verify(applications).findPageWithinStudentIds(1, 1, applicationType, "pending", null, null, studentIds);
        verifyNoInteractions(applicationMapper);
        if (!companyAudit || "COUNSELOR".equals(type)) verifyNoInteractions(companies);
    }

    @ParameterizedTest
    @ValueSource(strings = {"COUNSELOR", "DEPARTMENT", "COLLEGE"})
    void emptyStudentScopeDoesNotCountOtherTeachersApplications(String type) {
        TeacherUser teacher = teacher(type, false);
        when(scope.studentIds(teacher)).thenReturn(List.of());

        var result = service.getHomeStats(199L, type, "2026-09-01", "2027-09-01");

        assertEquals(0, result.getPendingApprovalCount());
        verifyNoInteractions(applications, applicationMapper, companies);
    }

    @Test
    void companyAuditorWithNoStudentApplicationsStillSeesCompanyPendingCount() {
        TeacherUser teacher = teacher("DEPARTMENT", true);
        when(scope.studentIds(teacher)).thenReturn(List.of());
        when(companies.countByAuditStatus(0)).thenReturn(1L);

        var result = service.getHomeStats(199L, "DEPARTMENT", "2026-09-01", "2027-09-01");

        assertEquals(1, result.getPendingApprovalCount());
        verifyNoInteractions(applications, applicationMapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"COUNSELOR", "DEPARTMENT", "COLLEGE", "TEACHER"})
    void unavailableTeacherDoesNotFallBackToGlobalPendingCount(String type) {
        var result = service.getHomeStats(199L, type, "2026-09-01", "2027-09-01");

        assertEquals(0, result.getPendingApprovalCount());
        verifyNoInteractions(scope, applications, applicationMapper, companies);
    }

    @Test
    void missingDivisionDoesNotFallBackToGlobalPendingCount() {
        TeacherUser teacher = teacher("DEPARTMENT", true);
        teacher.setDivisionId(null);

        var result = service.getHomeStats(199L, "DEPARTMENT", "2026-09-01", "2027-09-01");

        assertEquals(0, result.getPendingApprovalCount());
        verifyNoInteractions(scope, applications, applicationMapper, companies);
    }

    @Test
    void disabledTeacherDoesNotCountApplications() {
        TeacherUser teacher = teacher("COUNSELOR", true);
        teacher.setStatus(0);

        var result = service.getHomeStats(199L, "COUNSELOR", "2026-09-01", "2027-09-01");

        assertEquals(0, result.getPendingApprovalCount());
        verifyNoInteractions(scope, applications, applicationMapper, companies);
    }
}
