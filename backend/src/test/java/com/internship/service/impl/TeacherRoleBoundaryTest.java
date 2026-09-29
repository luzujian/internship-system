package com.internship.service.impl;

import com.internship.entity.TeacherUser;
import com.internship.entity.Division;
import com.internship.exception.BusinessException;
import com.internship.mapper.RolePermissionMapper;
import com.internship.mapper.TeacherUserMapper;
import com.internship.service.DivisionService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class TeacherRoleBoundaryTest {
    @Test
    void registrationRejectsMissingOrUnknownTeacherTypeBeforeWriting() {
        TeacherUserServiceImpl service = new TeacherUserServiceImpl();
        TeacherUser teacher = teacher("t-1", null);
        assertThrows(BusinessException.class, () -> service.register(teacher));
        teacher.setTeacherType("GENERAL");
        assertThrows(BusinessException.class, () -> service.register(teacher));
    }

    @Test
    void legacyGenericRoleNeverBecomesAnAuthority() {
        UserServiceImpl service = new UserServiceImpl();
        RolePermissionMapper roles = mock(RolePermissionMapper.class);
        ReflectionTestUtils.setField(service, "rolePermissionMapper", roles);
        TeacherUser teacher = teacher("t-2", "COLLEGE");
        teacher.setRole("ROLE_TEACHER");
        when(roles.findPermissionsByRoleCode("ROLE_TEACHER_COLLEGE")).thenReturn(List.of());

        UserDetails details = ReflectionTestUtils.invokeMethod(service, "createTeacherUserDetails", teacher);
        assertEquals(List.of("ROLE_TEACHER_COLLEGE"), details.getAuthorities().stream()
                .map(authority -> authority.getAuthority()).toList());
        verify(roles).findPermissionsByRoleCode("ROLE_TEACHER_COLLEGE");

        teacher.setTeacherType(null);
        assertThrows(UsernameNotFoundException.class, () -> ReflectionTestUtils.invokeMethod(
                service, "createTeacherUserDetails", teacher));
    }

    @Test
    void departmentTeacherMustHaveDivisionInTheirCollege() {
        TeacherUserServiceImpl service = new TeacherUserServiceImpl();
        TeacherUser teacher = teacher("t-3", "DEPARTMENT");
        assertThrows(BusinessException.class, () -> service.register(teacher));

        teacher.setDivisionId("2");
        Division division = new Division();
        division.setId(2L);
        division.setDepartmentId(3L);
        DivisionService divisions = mock(DivisionService.class);
        when(divisions.findById(2L)).thenReturn(division);
        ReflectionTestUtils.setField(service, "divisionService", divisions);
        assertThrows(BusinessException.class, () -> service.register(teacher));
    }

    @Test
    void departmentTeacherScopeUpdateRejectsAnotherCollegesDivision() {
        TeacherUserServiceImpl service = new TeacherUserServiceImpl();
        TeacherUserMapper mapper = mock(TeacherUserMapper.class);
        DivisionService divisions = mock(DivisionService.class);
        ReflectionTestUtils.setField(service, "teacherUserMapper", mapper);
        ReflectionTestUtils.setField(service, "divisionService", divisions);
        TeacherUser existing = teacher("t-4", "DEPARTMENT");
        existing.setId(11L);
        existing.setDivisionId("2");
        when(mapper.findById(11L)).thenReturn(existing);
        Division foreignDivision = new Division();
        foreignDivision.setId(3L);
        foreignDivision.setDepartmentId(2L);
        when(divisions.findById(3L)).thenReturn(foreignDivision);

        TeacherUser update = new TeacherUser();
        update.setId(11L);
        update.setDivisionId("3");

        assertThrows(BusinessException.class, () -> service.update(update));
        verify(mapper, never()).update(any());
    }

    private TeacherUser teacher(String username, String type) {
        TeacherUser teacher = new TeacherUser();
        teacher.setTeacherUserId(username);
        teacher.setName("教师");
        teacher.setDepartmentId("1");
        teacher.setPassword("encoded");
        teacher.setTeacherType(type);
        return teacher;
    }
}
