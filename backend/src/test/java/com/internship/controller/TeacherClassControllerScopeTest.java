package com.internship.controller;

import com.internship.entity.Class;
import com.internship.entity.ClassCounselorRelation;
import com.internship.entity.Result;
import com.internship.entity.TeacherUser;
import com.internship.entity.User;
import com.internship.mapper.TeacherUserMapper;
import com.internship.service.ClassCounselorRelationService;
import com.internship.service.ClassService;
import com.internship.utils.CurrentHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeacherClassControllerScopeTest {
    private final TeacherClassController controller = new TeacherClassController();
    private final ClassService classes = mock(ClassService.class);
    private final ClassCounselorRelationService relations = mock(ClassCounselorRelationService.class);
    private final TeacherUserMapper teachers = mock(TeacherUserMapper.class);

    TeacherClassControllerScopeTest() {
        ReflectionTestUtils.setField(controller, "classService", classes);
        ReflectionTestUtils.setField(controller, "classCounselorRelationService", relations);
        ReflectionTestUtils.setField(controller, "teacherUserMapper", teachers);
    }

    @AfterEach
    void clearIdentity() {
        CurrentHolder.remove();
    }

    @Test
    void counselorSeesOnlyAssignedClassesAndCannotSelectAnotherCounselor() {
        login(3L, "COUNSELOR", null, null);
        when(relations.findByCounselorId(3L)).thenReturn(List.of(relation(11L, 3L)));
        when(classes.findAllWithStudentCount()).thenReturn(List.of(classRow(11L, 5L, 9L), classRow(12L, 5L, 9L)));

        Result result = controller.getAllClasses();
        assertEquals(List.of(11L), ((List<Class>) result.getData()).stream().map(Class::getId).toList());
        assertThrows(AccessDeniedException.class, () -> controller.getCounselorStudents(4L, null, null,
                null, null, null, null));
        verify(relations, never()).findStudentsByCounselorId(4L, null, null, null, null, null, null);
    }

    @Test
    void staleCounselorRelationDoesNotBlockOwnStudentScope() {
        login(3L, "COUNSELOR", null, null);
        when(relations.findByCounselorId(3L)).thenReturn(List.of(relation(153L, 3L)));
        when(classes.findById(153L)).thenReturn(null);
        when(relations.findStudentsByCounselorId(3L, null, null, null, null, null, null))
                .thenReturn(List.of());

        Result result = controller.getCounselorStudents(3L, null, null, null, null, null, null);

        assertEquals(List.of(), result.getData());
        verify(relations).findStudentsByCounselorId(3L, null, null, null, null, null, null);
    }
    @Test
    void departmentCannotReadOtherDivisionClassOrAggregate() {
        login(8L, "DEPARTMENT", "5", "9");
        when(classes.findById(12L)).thenReturn(classRow(12L, 5L, 10L));

        assertThrows(AccessDeniedException.class, () -> controller.getClassStudents(12L, 8L));
        assertThrows(AccessDeniedException.class, () -> controller.getClassDetail(12L));
        verify(relations, never()).findStudentsByClassIdWithCounselor(12L, 8L);
    }

    @Test
    void collegeClassListStaysWithinItsCollege() {
        login(7L, "COLLEGE", "5", null);
        when(classes.findAllWithStudentCount()).thenReturn(List.of(classRow(11L, 5L, 9L), classRow(12L, 6L, 10L)));

        Result result = controller.getAllClasses();
        assertEquals(List.of(11L), ((List<Class>) result.getData()).stream().map(Class::getId).toList());
    }

    @Test
    void claimUsesAuthenticatedIdentityAndDoesNotEnableDirectStudentAccess() {
        login(3L, "COUNSELOR", "5", null);
        when(relations.claimClasses(3L, List.of(11L))).thenReturn(1);
        assertEquals(1, controller.claimClasses(new TeacherClassController.ClaimClassesRequest(List.of(11L))).getData());
        verify(relations).claimClasses(3L, List.of(11L));
        when(classes.findById(12L)).thenReturn(classRow(12L,5L,9L));
        assertThrows(AccessDeniedException.class, () -> controller.getClassStudents(12L,3L));
    }

    private void login(Long id, String type, String departmentId, String divisionId) {
        User user = new User();
        user.setId(id);
        CurrentHolder.setUser(user);
        TeacherUser teacher = new TeacherUser();
        teacher.setId(id);
        teacher.setStatus(1);
        teacher.setTeacherType(type);
        teacher.setDepartmentId(departmentId);
        teacher.setDivisionId(divisionId);
        when(teachers.findById(id)).thenReturn(teacher);
    }

    private ClassCounselorRelation relation(Long classId, Long counselorId) {
        ClassCounselorRelation relation = new ClassCounselorRelation();
        relation.setClassId(classId);
        relation.setCounselorId(counselorId);
        return relation;
    }

    private Class classRow(Long id, Long departmentId, Long divisionId) {
        Class item = new Class();
        item.setId(id);
        item.setDepartmentId(departmentId);
        item.setDivisionId(divisionId);
        return item;
    }
}
