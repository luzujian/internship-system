package com.internship.service.impl;

import com.internship.entity.TeacherUser;
import com.internship.mapper.TeacherUserMapper;
import com.internship.service.OnlineUserService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeacherContactUpdateTest {
    @Test
    void clearingPhoneKeepsDisabledAccountBlocked() {
        TeacherUserServiceImpl service = new TeacherUserServiceImpl();
        TeacherUserMapper mapper = mock(TeacherUserMapper.class);
        OnlineUserService online = mock(OnlineUserService.class);
        ReflectionTestUtils.setField(service, "teacherUserMapper", mapper);
        ReflectionTestUtils.setField(service, "onlineUserService", online);

        TeacherUser existing = new TeacherUser();
        existing.setId(1L);
        existing.setTeacherUserId("teacher-1");
        existing.setTeacherType("COLLEGE");
        existing.setStatus(0);
        existing.setPassword("existing-hash");
        when(mapper.findById(1L)).thenReturn(existing);

        TeacherUser update = new TeacherUser();
        update.setId(1L);
        update.setPhone("");
        update.setStatus(null);
        when(mapper.update(update)).thenReturn(1);

        assertEquals(1, service.update(update));
        assertEquals("", update.getPhone());
        assertEquals("existing-hash", update.getPassword());
        verify(online).accountChanged("TEACHER", "teacher-1", "teacher-1", 0);
    }
}
