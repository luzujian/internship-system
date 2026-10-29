package com.internship.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.entity.TeacherUser;
import com.internship.service.TeacherUserService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TeacherPhoneUpdateTest {
    @Test
    void administratorCanClearPhoneWithoutChangingAccountStatusOrPassword() throws Exception {
        TeacherUserController controller = new TeacherUserController();
        TeacherUserService service = mock(TeacherUserService.class);
        ReflectionTestUtils.setField(controller, "teacherUserService", service);
        TeacherUser update = new ObjectMapper().readValue(
                "{\"id\":1,\"phone\":\"\",\"password\":\"ignored\"}", TeacherUser.class);

        assertEquals(200, controller.updateTeacherUser(update).getCode());
        assertEquals("", update.getPhone());
        assertNull(update.getPassword());
        assertNull(update.getStatus());
        verify(service).update(update);
    }

    @Test
    void statusChangesStillUseTheDedicatedEndpoint() {
        TeacherUserController controller = new TeacherUserController();
        TeacherUserService service = mock(TeacherUserService.class);
        ReflectionTestUtils.setField(controller, "teacherUserService", service);

        assertEquals(200, controller.updateTeacherUserStatus(Map.of("id", 1, "status", 0)).getCode());
        org.mockito.ArgumentCaptor<TeacherUser> update = org.mockito.ArgumentCaptor.forClass(TeacherUser.class);
        verify(service).update(update.capture());
        assertEquals(0, update.getValue().getStatus());
        assertNull(update.getValue().getPhone());
    }
}
