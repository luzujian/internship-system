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
