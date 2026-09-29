package com.internship.service.impl;

import com.internship.entity.StudentUser;
import com.internship.entity.TeacherUser;
import com.internship.exception.BusinessException;
import com.internship.mapper.*;
import com.internship.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountIdentifierUpdateTest {
    private final AccountIdentifierMapper references = mock(AccountIdentifierMapper.class);
    private final TeacherUserMapper teachers = mock(TeacherUserMapper.class);
    private final StudentUserMapper students = mock(StudentUserMapper.class);

    private TeacherUserServiceImpl teacherService() {
        var service = new TeacherUserServiceImpl();
        ReflectionTestUtils.setField(service, "teacherUserMapper", teachers);
        ReflectionTestUtils.setField(service, "accountIdentifierMapper", references);
        TeacherUser old = new TeacherUser();
        old.setId(49L);
        old.setTeacherUserId("t003");
        old.setTeacherType("COUNSELOR");
        old.setPassword("existing-hash");
        when(teachers.findById(49L)).thenReturn(old);
        when(teachers.update(any())).thenReturn(1);
        return service;
    }

    private TeacherUser teacherChange(String number) {
        TeacherUser change = new TeacherUser();
        change.setId(49L);
        change.setTeacherUserId(number);
        return change;
    }

    @Test
    void teacherRenameMovesClassReferencesAndPreservesPassword() {
        var service = teacherService();
        TeacherUser change = teacherChange("x003");
        assertEquals(1, service.update(change));
        verify(references).renameTeacher("t003", "x003");
        assertEquals("existing-hash", change.getPassword());
    }

    @Test
    void ordinaryEditAndRejectedRenameNeverMoveReferences() {
        var service = teacherService();
        service.update(teacherChange("t003"));
        service.update(teacherChange(null));
        when(teachers.findByTeacherUserId("taken")).thenReturn(new TeacherUser());
        assertThrows(BusinessException.class, () -> service.update(teacherChange("taken")));
        verifyNoInteractions(references);
        verify(teachers, times(2)).update(any());
    }

    @Test
    void referenceFailureIsNotSwallowedByTransactionalUpdate() {
        var service = teacherService();
        when(references.renameTeacher("t003", "x003")).thenThrow(new IllegalStateException("database failure"));
        assertThrows(IllegalStateException.class, () -> service.update(teacherChange("x003")));
    }

    @Test
    void unsuccessfulAccountUpdateDoesNotMoveClassReferences() {
        var service = teacherService();
        when(teachers.update(any())).thenReturn(0);
        assertEquals(0, service.update(teacherChange("x003")));
        verifyNoInteractions(references);
    }

    @Test
    void studentRenameSynchronizesBusinessNumbersByImmutableId() {
        var service = new StudentUserServiceImpl(students, mock(GroupMapper.class),
                mock(StudentInternshipStatusMapper.class), mock(PasswordEncoder.class),
                mock(MajorService.class), mock(ClassService.class), mock(DepartmentService.class));
        ReflectionTestUtils.setField(service, "accountIdentifierMapper", references);
        StudentUser old = new StudentUser();
        old.setId(8L);
        old.setStudentId("s001");
        when(students.findById(8L)).thenReturn(old);
        when(students.update(any())).thenReturn(1);
        StudentUser change = new StudentUser();
        change.setId(8L);
        change.setStudentId("s002");
        assertEquals(1, service.update(change));
        verify(references).renameApplicationStudent(8L, "s002");
        verify(references).renameStudentApplicationStudent(8L, "s002");
        verify(references).renameReflectionStudent(8L, "s002");
        verify(references).renameConfirmationStudent(8L, "s002");
        assertNull(change.getPassword());
        clearInvocations(references);
        change.setStudentId("s001");
        service.update(change);
        verifyNoInteractions(references);
    }
}
