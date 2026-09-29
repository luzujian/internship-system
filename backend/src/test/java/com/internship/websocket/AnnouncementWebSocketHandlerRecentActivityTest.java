package com.internship.websocket;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnnouncementWebSocketHandlerRecentActivityTest {

    @Test
    void collidingTeacherIdDoesNotReceiveStudentEvents() throws Exception {
        var handler = new AnnouncementWebSocketHandler();
        var student = mock(WebSocketSession.class);
        var teacher = mock(WebSocketSession.class);
        when(student.getId()).thenReturn("student");
        when(teacher.getId()).thenReturn("teacher");
        when(student.isOpen()).thenReturn(true);
        when(teacher.isOpen()).thenReturn(true);
        when(student.getAttributes()).thenReturn(Map.of("userId", 42L, "role", "ROLE_STUDENT"));
        when(teacher.getAttributes()).thenReturn(Map.of("userId", 42L, "role", "ROLE_TEACHER_COUNSELOR"));
        handler.afterConnectionEstablished(student);
        handler.afterConnectionEstablished(teacher);
        org.mockito.Mockito.clearInvocations(student, teacher);
        handler.sendStudentRecentActivityChanged(42L, 123L);
        verify(student).sendMessage(org.mockito.ArgumentMatchers.any());
        verify(teacher, org.mockito.Mockito.never()).sendMessage(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.clearInvocations(student, teacher);
        handler.sendAnnouncementToRole("TEACHER_COLLEGE", Map.of("title","restricted"));
        verify(teacher, org.mockito.Mockito.never()).sendMessage(org.mockito.ArgumentMatchers.any());
        handler.sendAnnouncementToRole("TEACHER_COUNSELOR", Map.of("title","allowed"));
        verify(teacher).sendMessage(org.mockito.ArgumentMatchers.any());
        verify(student, org.mockito.Mockito.never()).sendMessage(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void sendsRecentActivityRefreshOnlyToTheStudentSession() throws Exception {
        AnnouncementWebSocketHandler handler = new AnnouncementWebSocketHandler();
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("student-session");
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(Map.of(
                "userId", 42L,
                "username", "s001",
                "role", "ROLE_STUDENT"));
        handler.afterConnectionEstablished(session);

        handler.sendStudentRecentActivityChanged(42L, 123L);

        ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, times(2)).sendMessage(messages.capture());
        List<TextMessage> sent = messages.getAllValues();
        String refreshPayload = sent.get(1).getPayload();
        assertTrue(refreshPayload.contains("\"type\":\"student_recent_activity_changed\""));
        assertTrue(refreshPayload.contains("\"activityId\":123"));
    }
}
