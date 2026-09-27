package com.internship.service;

import com.internship.entity.InternshipReflection;
import com.internship.entity.StudentReflectionEvaluation;
import com.internship.entity.TeacherUser;
import com.internship.entity.User;
import com.internship.mapper.TeacherAutomationScopeMapper;
import com.internship.mapper.TeacherUserMapper;
import com.internship.utils.CurrentHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvaluationAccessPolicyTest {
    @InjectMocks private EvaluationAccessPolicy policy;
    @Mock private TeacherUserMapper teachers;
    @Mock private TeacherAutomationScopeMapper scope;
    @Mock private InternshipReflectionService reflections;

    @AfterEach
    void clear() {
        CurrentHolder.remove();
    }

    @Test
    void studentAndCompanyCannotWriteAnyEvaluation() {
        login(7L, "S007", "ROLE_STUDENT");
        assertThrows(AccessDeniedException.class, () -> policy.requireStudent(7L, false));
        login(12L, "C012", "ROLE_COMPANY");
        assertThrows(AccessDeniedException.class, () -> policy.requireStudent(7L, false));
    }

    @Test
    void eachTeacherTypeUsesItsConfiguredStudentScope() {
        for (String type : List.of("COUNSELOR", "DEPARTMENT", "COLLEGE")) {
            TeacherUser teacher = teacher(type);
            when(scope.contains(teacher, 7L)).thenReturn(1);
            policy.requireStudent(7L, false);
            verify(scope).contains(teacher, 7L);
            assertThrows(AccessDeniedException.class, () -> policy.requireStudent(8L, false));
        }
    }

    @Test
    void disabledOrMismatchedTeacherIsRejected() {
        TeacherUser teacher = teacher("COUNSELOR");
        teacher.setStatus(0);
        assertThrows(AccessDeniedException.class, () -> policy.requireStudent(7L, false));
        teacher.setStatus(1);
        login(23L, "another-account", "ROLE_TEACHER_COUNSELOR");
        assertThrows(AccessDeniedException.class, () -> policy.requireStudent(7L, false));
    }

    @Test
    void counselorWithoutClassesGetsNoStudentsAndMapperFailureDoesNotGrantAccess() {
        TeacherUser teacher = teacher("COUNSELOR");
        when(scope.studentIds(teacher)).thenReturn(List.of());
        assertEquals(java.util.Set.of(), policy.readableStudentIds());
        assertThrows(AccessDeniedException.class, () -> policy.requireStudent(7L, false));
        when(scope.contains(teacher, 7L)).thenThrow(new IllegalStateException("scope unavailable"));
        assertThrows(IllegalStateException.class, () -> policy.requireStudent(7L, false));
    }

    @Test
    void reflectionOwnershipComesFromStoredRecord() {
        TeacherUser teacher = teacher("COUNSELOR");
        InternshipReflection reflection = new InternshipReflection();
        reflection.setId(99L);
        reflection.setStudentId(7L);
        reflection.setDeleted(0);
        when(reflections.findById(99L)).thenReturn(reflection);
        when(scope.contains(teacher, 7L)).thenReturn(1);

        assertThrows(AccessDeniedException.class, () -> policy.requireReflection(99L, 8L, false));
        assertEquals(7L, policy.requireReflection(99L, 7L, false));
        reflection.setDeleted(1);
        assertThrows(AccessDeniedException.class, () -> policy.requireReflection(99L, 7L, false));
    }

    @Test
    void adminRetainsAllStudentScopeAndStudentCanOnlyReadSelf() {
        login(1L, "admin", "ROLE_ADMIN");
        policy.requireStudent(7L, false);
        assertNull(policy.readableStudentIds());
        login(7L, "S007", "ROLE_STUDENT");
        policy.requireStudent(7L, true);
        assertThrows(AccessDeniedException.class, () -> policy.requireStudent(8L, true));
    }

    @Test
    void legacyEvaluationWithForgedStudentIdIsNotReturnedInTeacherList() {
        login(23L, "T0023", "ROLE_TEACHER_COUNSELOR");
        InternshipReflection reflection = new InternshipReflection();
        reflection.setId(99L);
        reflection.setStudentId(8L);
        when(reflections.findById(99L)).thenReturn(reflection);
        StudentReflectionEvaluation poisoned = new StudentReflectionEvaluation();
        poisoned.setReflectionId(99L);
        poisoned.setStudentId(7L);

        assertEquals(List.of(), policy.readableEvaluations(List.of(poisoned)));
    }

    private TeacherUser teacher(String type) {
        login(23L, "T0023", "ROLE_TEACHER_" + type);
        TeacherUser teacher = new TeacherUser();
        teacher.setId(23L);
        teacher.setTeacherUserId("T0023");
        teacher.setTeacherType(type);
        teacher.setStatus(1);
        when(teachers.findById(23L)).thenReturn(teacher);
        return teacher;
    }

    private void login(Long id, String username, String role) {
        User actor = new User();
        actor.setId(id);
        actor.setUsername(username);
        actor.setRole(role);
        CurrentHolder.setUser(actor);
    }
}
