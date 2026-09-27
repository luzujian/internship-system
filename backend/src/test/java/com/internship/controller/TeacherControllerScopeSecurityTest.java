package com.internship.controller;

import com.internship.entity.ClassCounselorRelation;
import com.internship.entity.LearningResource;
import com.internship.entity.Major;
import com.internship.entity.PageResult;
import com.internship.entity.StudentInternshipStatus;
import com.internship.entity.StudentUser;
import com.internship.entity.TeacherUser;
import com.internship.entity.User;
import com.internship.mapper.ClassCounselorRelationMapper;
import com.internship.mapper.DepartmentMapper;
import com.internship.mapper.DivisionMapper;
import com.internship.mapper.StudentInternshipStatusMapper;
import com.internship.service.ResourceService;
import com.internship.service.MajorService;
import com.internship.service.StudentInternshipStatusService;
import com.internship.service.StudentUserService;
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
import org.springframework.security.access.prepost.PreAuthorize;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeacherControllerScopeSecurityTest {
    @InjectMocks private TeacherController controller;
    @Mock private TeacherUserService teacherUserService;
    @Mock private ClassCounselorRelationMapper classCounselorRelationMapper;
    @Mock private DepartmentMapper departmentMapper;
    @Mock private DivisionMapper divisionMapper;
    @Mock private StudentInternshipStatusService studentInternshipStatusService;
    @Mock private StudentInternshipStatusMapper studentInternshipStatusMapper;
    @Mock private StudentUserService studentUserService;
    @Mock private MajorService majorService;
    @Mock private ResourceService resourceService;

    @AfterEach
    void clearUser() {
        CurrentHolder.remove();
    }

    private TeacherUser teacher(String type, List<Long> classIds) {
        User user = new User();
        user.setId(23L);
        user.setUsername("T0023");
        user.setRole("GENERAL".equals(type) ? "ROLE_TEACHER" : "ROLE_TEACHER_" + type);
        CurrentHolder.setUser(user);

        TeacherUser teacher = new TeacherUser();
        teacher.setId(23L);
        teacher.setTeacherUserId("T0023");
        teacher.setTeacherType(type);
        when(teacherUserService.findById(23L)).thenReturn(teacher);
        if ("COUNSELOR".equals(type)) {
            when(classCounselorRelationMapper.findByCounselorId(23L)).thenReturn(classIds.stream().map(id -> {
                ClassCounselorRelation relation = new ClassCounselorRelation();
                relation.setClassId(id);
                return relation;
            }).toList());
        }
        return teacher;
    }

    private StudentInternshipStatus status(long id, long studentId) {
        StudentInternshipStatus item = new StudentInternshipStatus();
        item.setId(id);
        item.setStudentId(studentId);
        return item;
    }

    @Test
    void teacherRoutesRequireOneOfTheThreeRecognizedSubroles() {
        PreAuthorize gate = TeacherController.class.getAnnotation(PreAuthorize.class);
        assertEquals("hasAnyRole('TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR')",
                gate.value());
    }

    @Test
    void missingIdentityDeniesListStatisticsDetailsAndBothExportsBeforeQueries() {
        assertThrows(AccessDeniedException.class, () -> controller.getStudents(1, 10, null, null));
        assertThrows(AccessDeniedException.class, () -> controller.getInternshipStatusList(
                1, 10, null, null, null, null, null, null, null, null, null));
        assertThrows(AccessDeniedException.class, controller::getInternshipStatusStatistics);
        assertThrows(AccessDeniedException.class, controller::getInternshipCompanyOptions);
        assertThrows(AccessDeniedException.class, () -> controller.getStudentInternshipDetail(81L));
        assertThrows(AccessDeniedException.class, () -> controller.getInternshipStatusById(91L));
        assertThrows(AccessDeniedException.class, () -> controller.exportInternshipStatus(
                null, null, null, null, null, null, null, new MockHttpServletResponse()));
        assertThrows(AccessDeniedException.class, () -> controller.exportBatchInternshipStatus(
                null, null, null, null, new MockHttpServletResponse()));
        verifyNoInteractions(studentInternshipStatusService, studentInternshipStatusMapper, studentUserService);
    }

    @Test
    void teacherStudentListAlwaysUsesResolvedScopeEvenWithSearchAndClassFilter() {
        teacher("COUNSELOR", List.of(31L));
        when(studentUserService.findPageByTeacherScope(eq(1), eq(10), eq("S1"), eq(31L),
                isNull(), isNull(), eq(List.of(31L))))
                .thenReturn(PageResult.build(1L, List.of(new StudentUser())));
        assertEquals(1L, ((PageResult<?>) controller.getStudents(1, 10, "S1", 31L).getData()).getTotal());
        verify(studentUserService).findPageByTeacherScope(eq(1), eq(10), eq("S1"), eq(31L),
                isNull(), isNull(), eq(List.of(31L)));

        org.mockito.Mockito.reset(teacherUserService, studentUserService);
        TeacherUser college = teacher("COLLEGE", List.of());
        college.setDepartmentId("5");
        controller.getStudents(1, 10, null, null);
        verify(studentUserService).findPageByTeacherScope(eq(1), eq(10), isNull(), isNull(),
                eq(List.of(5L)), isNull(), isNull());

        org.mockito.Mockito.reset(teacherUserService, studentUserService);
        TeacherUser department = teacher("DEPARTMENT", List.of());
        department.setDivisionId("3");
        controller.getStudents(1, 10, null, null);
        verify(studentUserService).findPageByTeacherScope(eq(1), eq(10), isNull(), isNull(),
                isNull(), eq(List.of(3L)), isNull());
    }

    @Test
    void teacherStudentSqlCannotFallBackToUnscopedList() throws IOException {
        try (var stream = getClass().getResourceAsStream("/com/internship/mapper/StudentUserMapper.xml")) {
            assertTrue(stream != null);
            String xml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            int start = xml.indexOf("<select id=\"listByTeacherScope\"");
            assertTrue(start >= 0);
            String query = xml.substring(start, xml.indexOf("</select>", start));
            assertTrue(query.contains("m.department_id IN"));
            assertTrue(query.contains("m.division_id IN"));
            assertTrue(query.contains("s.class_id IN"));
            assertTrue(query.contains("<otherwise>AND 1 = 0</otherwise>"));
        }
    }

    @Test
    void unscopedTeacherAndScopeLookupFailureDenyRatherThanUseSchoolwideQueries() {
        teacher("GENERAL", List.of());
        assertThrows(AccessDeniedException.class, () -> controller.getInternshipStatusList(
                1, 10, null, null, null, null, null, null, null, null, null));
        assertThrows(AccessDeniedException.class, () -> controller.exportBatchInternshipStatus(
                null, null, null, null, new MockHttpServletResponse()));
        verifyNoInteractions(studentInternshipStatusService, studentInternshipStatusMapper);

        org.mockito.Mockito.reset(teacherUserService);
        when(teacherUserService.findById(23L)).thenThrow(new IllegalStateException("scope unavailable"));
        assertThrows(AccessDeniedException.class, controller::getInternshipStatusStatistics);
        verifyNoInteractions(studentInternshipStatusService, studentInternshipStatusMapper);
    }

    @Test
    void counselorListAndStatisticsUseOnlyAssignedClasses() {
        teacher("COUNSELOR", List.of(31L));
        when(studentInternshipStatusService.findPageByScope(any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PageResult.<StudentInternshipStatus>build(2L, List.of()));

        assertEquals(200, controller.getInternshipStatusList(
                1, 10, null, null, null, null, null, null, null, null, null).getCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> statistics = (Map<String, Object>) controller.getInternshipStatusStatistics().getData();
        assertEquals(2L, statistics.get("studentCount"));
        assertEquals(2L, statistics.get("noOfferCount"));
        verify(studentInternshipStatusService).findPageByScope(eq(1), eq(10), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), eq(List.of(31L)));
        verify(studentInternshipStatusService, never()).findPage(any(), any());
        verify(studentInternshipStatusService, never()).countByStatus(any());
    }

    @Test
    void collegeScopeUsesDepartmentIncludingMajorWithoutDivision() {
        TeacherUser college = teacher("COLLEGE", List.of());
        college.setDepartmentId("5");
        when(studentInternshipStatusService.findPageByScope(any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PageResult.<StudentInternshipStatus>build(1L, List.of()));
        assertEquals(200, controller.getInternshipStatusList(
                1, 10, null, null, null, null, null, null, null, null, null).getCode());
        verify(studentInternshipStatusService).findPageByScope(eq(1), eq(10), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(List.of(5L)), isNull(), isNull());

        StudentUser student = new StudentUser();
        student.setId(81L);
        student.setMajorId(71L);
        Major major = new Major();
        major.setId(71L);
        major.setDepartmentId(5L);
        major.setDivisionId(null);
        when(studentInternshipStatusService.findById(91L)).thenReturn(status(91L, 81L));
        when(studentUserService.findById(81L)).thenReturn(student);
        when(majorService.findById(71L)).thenReturn(major);
        assertEquals(200, controller.getInternshipStatusById(91L).getCode());
        controller.getInternshipCompanyOptions();
        verify(studentInternshipStatusMapper).findDistinctCompanyNamesByScope(
                eq(List.of(5L)), isNull(), isNull());
    }

    @Test
    void collegeMapperQueriesFilterMajorDepartmentDirectly() throws IOException {
        try (var stream = getClass().getResourceAsStream(
                "/com/internship/mapper/StudentInternshipStatusMapper.xml")) {
            assertTrue(stream != null);
            String xml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            for (String queryId : List.of("listByScope", "findDistinctCompanyNamesByScope")) {
                int start = xml.indexOf("<select id=\"" + queryId + "\"");
                assertTrue(start >= 0, queryId);
                int end = xml.indexOf("</select>", start);
                assertTrue(end > start, queryId);
                String query = xml.substring(start, end);
                assertTrue(query.contains("m.department_id IN"), queryId);
                assertTrue(query.contains("collection=\"departmentIds\""), queryId);
            }
        }
    }

    @Test
    void directDetailsRejectStudentsOutsideAssignedClass() {
        teacher("COUNSELOR", List.of(31L));
        StudentUser outsider = new StudentUser();
        outsider.setId(81L);
        outsider.setClassId(99L);
        when(studentUserService.findById(81L)).thenReturn(outsider);
        when(studentInternshipStatusService.findById(91L)).thenReturn(status(91L, 81L));

        assertThrows(AccessDeniedException.class, () -> controller.getStudentInternshipDetail(81L));
        assertThrows(AccessDeniedException.class, () -> controller.getInternshipStatusById(91L));
        verify(studentInternshipStatusService, never()).findByStudentId(81L);
    }

    @Test
    void selectedExportRejectsOutOfScopeIdsAndNormalExportKeepsScopeFilter() throws IOException {
        teacher("COUNSELOR", List.of(31L));
        when(studentInternshipStatusMapper.listByScope(any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of(status(91L, 81L)));

        MockHttpServletResponse denied = new MockHttpServletResponse();
        assertThrows(AccessDeniedException.class, () -> controller.exportInternshipStatus(
                null, null, null, null, null, null, "81,82", denied));
        assertEquals(0, denied.getContentAsByteArray().length);

        MockHttpServletResponse permitted = new MockHttpServletResponse();
        controller.exportInternshipStatus(81L, null, null, null, null, null, null, permitted);
        assertTrue(permitted.getContentAsByteArray().length > 0);
        MockHttpServletResponse selected = new MockHttpServletResponse();
        controller.exportInternshipStatus(null, null, null, null, null, null, "81", selected);
        assertTrue(selected.getContentAsByteArray().length > 0);
        verify(studentInternshipStatusMapper).listByScope(eq(81L), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(List.of(31L)));
        verify(studentInternshipStatusService, never()).findByIds(any());
        verify(studentInternshipStatusService, never()).list(any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any());
    }

    @Test
    void batchExportKeepsGradeClassAndAssignedScope() throws IOException {
        teacher("COUNSELOR", List.of(31L));
        when(studentInternshipStatusMapper.listByScope(any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.exportBatchInternshipStatus("class", "软件1班", "2024", 2, response);
        assertTrue(response.getContentAsByteArray().length > 0);
        verify(studentInternshipStatusMapper).listByScope(isNull(), isNull(), isNull(), eq(2),
                isNull(), isNull(), eq("2024"), isNull(), eq("软件1班"), isNull(), isNull(), isNull(), eq(List.of(31L)));
    }

    @Test
    void sameNumericIdInAnotherRoleIsNotTeacherOwnedResource() {
        User user = new User();
        user.setId(23L);
        user.setRole("ROLE_TEACHER");
        CurrentHolder.setUser(user);
        LearningResource studentResource = new LearningResource();
        studentResource.setId(7L);
        studentResource.setUploaderId(23L);
        studentResource.setUploaderRole("STUDENT");
        when(resourceService.getResourcesByUploaderId(23L)).thenReturn(List.of(studentResource));
        when(resourceService.getResourceById(7L)).thenReturn(studentResource);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) controller.getMyResources(1, 10).getData();
        assertEquals(0, result.get("total"));
        assertTrue(((List<?>) result.get("list")).isEmpty());
        assertThrows(AccessDeniedException.class, () -> controller.updateResource(7L, new LearningResource()));
        assertThrows(AccessDeniedException.class, () -> controller.deleteResource(7L));
        verify(resourceService, never()).updateResource(any());
        verify(resourceService, never()).deleteResource(any());
    }

    @Test
    void missingIdentityCannotReadOrMutateTeacherResources() {
        assertThrows(AccessDeniedException.class, () -> controller.getMyResources(1, 10));
        assertThrows(AccessDeniedException.class, () -> controller.updateResource(7L, new LearningResource()));
        assertThrows(AccessDeniedException.class, () -> controller.deleteResource(7L));
        verifyNoInteractions(resourceService);
    }
}
