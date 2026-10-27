package com.internship.service;

import com.internship.entity.*;
import com.internship.mapper.*;
import com.internship.service.impl.*;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercise the real account-update paths with in-memory authentication and mocked SQL mappers. */
class OnlineAccountLifecycleTest {
    private final JwtRedisService jwt = new JwtRedisService();
    private final OnlineUserService online = new OnlineUserService(jwt);
    private void assertDisablingRemovesPresence(Object service, String role, Runnable update) {
        String sid = UUID.randomUUID().toString(); jwt.startSession(sid, 3_600_000);
        var claims = Jwts.claims().add(Map.of("sid", sid, "username", "qa-user", "role", role, "name", "QA", "ver", 1)).build();
        long activity = jwt.checkSession(sid, 900_000, false);
        online.observe(claims, activity); assertEquals(1, online.summary().totalOnline());
        ReflectionTestUtils.setField(service, "onlineUserService", online);
        update.run();
        online.observe(claims, activity);
        assertEquals(0, online.summary().totalOnline());
    }
    @Test void studentDisableUpdatesOnlineState() {
        var mapper = mock(StudentUserMapper.class); var user = new StudentUser();
        user.setId(1L); user.setStudentId("qa-user"); user.setStatus(1);
        when(mapper.findById(1L)).thenReturn(user); when(mapper.update(any())).thenReturn(1);
        var service = mock(StudentUserServiceImpl.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(service, "studentUserMapper", mapper);
        var patch = new StudentUser(); patch.setId(1L); patch.setStatus(0);
        assertDisablingRemovesPresence(service, "ROLE_STUDENT", () -> service.update(patch));
    }
    @Test void teacherDisableUpdatesOnlineState() {
        var mapper = mock(TeacherUserMapper.class); var user = new TeacherUser();
        user.setId(1L); user.setTeacherUserId("qa-user"); user.setTeacherType("COUNSELOR"); user.setStatus(1);
        when(mapper.findById(1L)).thenReturn(user); when(mapper.update(any())).thenReturn(1);
        var service = mock(TeacherUserServiceImpl.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(service, "teacherUserMapper", mapper);
        var patch = new TeacherUser(); patch.setId(1L); patch.setStatus(0);
        assertDisablingRemovesPresence(service, "ROLE_TEACHER_COUNSELOR", () -> service.update(patch));
    }
    @Test void companyDisableUpdatesOnlineState() {
        var mapper = mock(CompanyUserMapper.class); var user = new CompanyUser();
        user.setId(1L); user.setUsername("qa-user"); user.setStatus(1);
        when(mapper.findById(1L)).thenReturn(user); when(mapper.update(any())).thenReturn(1);
        var service = mock(CompanyUserServiceImpl.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(service, "companyUserMapper", mapper);
        var patch = new CompanyUser(); patch.setId(1L); patch.setStatus(0);
        assertDisablingRemovesPresence(service, "ROLE_COMPANY", () -> service.update(patch));
    }
    @Test void adminDisableUpdatesOnlineState() {
        var mapper = mock(AdminUserMapper.class); var user = new AdminUser();
        user.setId(1L); user.setUsername("qa-user"); user.setStatus(1);
        when(mapper.findById(1L)).thenReturn(user); when(mapper.update(any())).thenReturn(1);
        var service = mock(AdminUserServiceImpl.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(service, "adminUserMapper", mapper);
        var patch = new AdminUser(); patch.setId(1L); patch.setUsername("qa-user"); patch.setStatus(0);
        assertDisablingRemovesPresence(service, "ROLE_ADMIN", () -> service.update(patch));
    }
}
