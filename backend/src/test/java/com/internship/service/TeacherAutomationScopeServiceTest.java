package com.internship.service;

import com.internship.entity.TeacherUser;
import com.internship.exception.BusinessException;
import com.internship.mapper.TeacherAutomationScopeMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TeacherAutomationScopeServiceTest {
    @Test void exactAccountWinsOverSameDisplayNameWithinAuthorizedScope() {
        var mapper = mock(TeacherAutomationScopeMapper.class);
        var permissions = mock(TeacherPermissionService.class);
        var service = new TeacherAutomationScopeService(mapper, permissions);
        var teacher = new TeacherUser(); teacher.setId(9L); teacher.setStatus(1); teacher.setTeacherType("COUNSELOR");
        when(permissions.getPermissionCodesByRoleCode("COUNSELOR")).thenReturn(List.of("view:student_tracking"));
        var byName = new com.internship.entity.StudentUser(); byName.setUsername("s002"); byName.setName("s001");
        var byAccount = new com.internship.entity.StudentUser(); byAccount.setUsername("s001"); byAccount.setName("学生一");
        when(mapper.matchStudents(teacher, "s001")).thenReturn(List.of(byName, byAccount));
        assertEquals(List.of(byAccount), service.matchStudents(teacher, "s001"));
        when(permissions.getPermissionCodesByRoleCode("COUNSELOR")).thenReturn(List.of());
        assertThrows(BusinessException.class, () -> service.matchStudents(teacher, "s001"));
        verify(mapper, times(1)).matchStudents(teacher, "s001");
    }
    @Test void revokedOriginalTrackingPermissionBlocksOfflineResolution() {
        var mapper = mock(TeacherAutomationScopeMapper.class);
        var permissions = mock(TeacherPermissionService.class);
        var service = new TeacherAutomationScopeService(mapper, permissions);
        var teacher = new TeacherUser(); teacher.setTeacherType("DEPARTMENT"); teacher.setStatus(1); teacher.setDivisionId("3");
        when(permissions.getPermissionCodesByRoleCode("DEPARTMENT")).thenReturn(List.of("view:student_tracking"));
        service.studentIds(teacher);
        when(permissions.getPermissionCodesByRoleCode("DEPARTMENT")).thenReturn(List.of());
        assertThrows(BusinessException.class, () -> service.studentIds(teacher));
        verify(mapper, times(1)).studentIds(teacher);
    }
    @Test void departmentCannotFallBackToCollegeWhenDivisionMissing() {
        var mapper = mock(TeacherAutomationScopeMapper.class);
        var service = new TeacherAutomationScopeService(mapper, mock(TeacherPermissionService.class));
        var teacher = new TeacherUser(); teacher.setTeacherType("DEPARTMENT"); teacher.setStatus(1); teacher.setDepartmentId("1");
        assertThrows(BusinessException.class, () -> service.studentIds(teacher));
        verifyNoInteractions(mapper);
        assertEquals("OWN_CLASSES", TeacherAutomationScopeService.scopeOf("COUNSELOR"));
        assertEquals("OWN_DIVISION", TeacherAutomationScopeService.scopeOf("DEPARTMENT"));
        assertEquals("OWN_COLLEGE", TeacherAutomationScopeService.scopeOf("COLLEGE"));
    }
}
