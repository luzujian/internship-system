package com.internship.service.impl;

import com.internship.entity.AdminUser;
import com.internship.entity.TeacherUser;
import com.internship.mapper.CompanyUserMapper;
import com.internship.service.AdminUserService;
import com.internship.service.StudentUserService;
import com.internship.service.TeacherUserService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UsernamePasswordPreservationTest {
    @Test
    void renamingAdminDoesNotSendExistingHashAsNewPassword() {
        var service = new UserServiceImpl();
        var admins = mock(AdminUserService.class);
        ReflectionTestUtils.setField(service, "adminUserService", admins);
        ReflectionTestUtils.setField(service, "teacherUserService", mock(TeacherUserService.class));
        ReflectionTestUtils.setField(service, "studentUserService", mock(StudentUserService.class));
        ReflectionTestUtils.setField(service, "companyUserMapper", mock(CompanyUserMapper.class));
        AdminUser existing = new AdminUser();
        existing.setUsername("old-admin");
        existing.setPassword("stored-hash");
        when(admins.findByUsername("old-admin")).thenReturn(existing);
        when(admins.update(any())).thenAnswer(invocation -> {
            AdminUser updated = invocation.getArgument(0);
            assertEquals("new-admin", updated.getUsername());
            assertNull(updated.getPassword());
            return 1;
        });

        assertEquals(1, service.updateUsername("old-admin", "new-admin"));
    }

    @Test
    void renamingTeacherDoesNotSendExistingHashAsNewPassword() {
        var service = new UserServiceImpl();
        var teachers = mock(TeacherUserService.class);
        ReflectionTestUtils.setField(service, "adminUserService", mock(AdminUserService.class));
        ReflectionTestUtils.setField(service, "teacherUserService", teachers);
        ReflectionTestUtils.setField(service, "studentUserService", mock(StudentUserService.class));
        ReflectionTestUtils.setField(service, "companyUserMapper", mock(CompanyUserMapper.class));
        TeacherUser existing = new TeacherUser();
        existing.setUsername("old-teacher");
        existing.setPassword("stored-hash");
        when(teachers.findByTeacherUserId("old-teacher")).thenReturn(existing);
        when(teachers.update(any())).thenAnswer(invocation -> {
            TeacherUser updated = invocation.getArgument(0);
            assertEquals("new-teacher", updated.getUsername());
            assertNull(updated.getPassword());
            return 1;
        });

        assertEquals(1, service.updateUsername("old-teacher", "new-teacher"));
    }
}
