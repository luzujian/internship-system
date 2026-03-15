package com.gdmu.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class AnnouncementWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> userSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = (Long) session.getAttributes().get("userId");
        String username = (String) session.getAttributes().get("username");
        String role = (String) session.getAttributes().get("role");
        
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        
        userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
        
        log.info("WebSocket连接建立：sessionId={}, userId={}, username={}, role={}", 
                 sessionId, userId, username, role);
        
        Map<String, Object> connectMessage = Map.of(
            "type", "connected",
            "message", "连接成功",
            "userId", userId,
            "role", role != null ? role : "",
            "timestamp", System.currentTimeMillis()
        );
        sendMessage(session, connectMessage);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("收到WebSocket消息：{}", payload);
        
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);
            String type = (String) data.get("type");
            
            if ("ping".equals(type)) {
                Map<String, Object> pong = Map.of(
                    "type", "pong",
                    "timestamp", System.currentTimeMillis()
                );
                sendMessage(session, pong);
            }
        } catch (Exception e) {
            log.error("处理WebSocket消息失败：{}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        Long userId = (Long) session.getAttributes().get("userId");
        
        sessions.remove(sessionId);
        
        if (userId != null) {
            Set<String> userSessionSet = userSessions.get(userId);
            if (userSessionSet != null) {
                userSessionSet.remove(sessionId);
                if (userSessionSet.isEmpty()) {
                    userSessions.remove(userId);
                }
            }
        }
        
        log.info("WebSocket连接关闭：sessionId={}, userId={}, status={}", sessionId, userId, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket传输错误：sessionId={}, error={}", session.getId(), exception.getMessage());
    }

    public void sendAnnouncementToRole(String role, Map<String, Object> announcement) {
        log.info("向角色 {} 推送公告：{}", role, announcement.get("title"));
        
        for (WebSocketSession session : sessions.values()) {
            String sessionRole = (String) session.getAttributes().get("role");
            if (sessionRole != null && sessionRole.contains(role)) {
                try {
                    Map<String, Object> message = Map.of(
                        "type", "new_announcement",
                        "data", announcement,
                        "timestamp", System.currentTimeMillis()
                    );
                    sendMessage(session, message);
                    log.debug("已向用户 {} 推送公告", session.getAttributes().get("userId"));
                } catch (Exception e) {
                    log.error("推送公告失败：sessionId={}, error={}", session.getId(), e.getMessage());
                }
            }
        }
    }

    public void sendAnnouncementToUser(Long userId, Map<String, Object> announcement) {
        log.info("向用户 {} 推送公告：{}", userId, announcement.get("title"));
        
        Set<String> userSessionSet = userSessions.get(userId);
        if (userSessionSet != null && !userSessionSet.isEmpty()) {
            for (String sessionId : userSessionSet) {
                WebSocketSession session = sessions.get(sessionId);
                if (session != null && session.isOpen()) {
                    try {
                        Map<String, Object> message = Map.of(
                            "type", "new_announcement",
                            "data", announcement,
                            "timestamp", System.currentTimeMillis()
                        );
                        sendMessage(session, message);
                    } catch (Exception e) {
                        log.error("推送公告到用户失败：userId={}, error={}", userId, e.getMessage());
                    }
                }
            }
        }
    }

    public void broadcastAnnouncement(Map<String, Object> announcement) {
        log.info("广播公告：{}", announcement.get("title"));
        
        Map<String, Object> message = Map.of(
            "type", "new_announcement",
            "data", announcement,
            "timestamp", System.currentTimeMillis()
        );
        
        for (WebSocketSession session : sessions.values()) {
            if (session.isOpen()) {
                try {
                    sendMessage(session, message);
                } catch (Exception e) {
                    log.error("广播公告失败：sessionId={}, error={}", session.getId(), e.getMessage());
                }
            }
        }
    }

    public void sendFeedbackToAdmin(Map<String, Object> feedback) {
        log.info("向管理员推送问题反馈：{}", feedback.get("title"));
        
        Map<String, Object> message = Map.of(
            "type", "new_feedback",
            "data", feedback,
            "timestamp", System.currentTimeMillis()
        );
        
        for (WebSocketSession session : sessions.values()) {
            String sessionRole = (String) session.getAttributes().get("role");
            if (sessionRole != null && sessionRole.contains("ADMIN")) {
                if (session.isOpen()) {
                    try {
                        sendMessage(session, message);
                        log.debug("已向管理员 {} 推送问题反馈", session.getAttributes().get("userId"));
                    } catch (Exception e) {
                        log.error("推送问题反馈失败：sessionId={}, error={}", session.getId(), e.getMessage());
                    }
                }
            }
        }
    }

    private void sendMessage(WebSocketSession session, Map<String, Object> message) throws IOException {
        if (session.isOpen()) {
            String json = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(json));
        }
    }

    public int getOnlineCount() {
        return sessions.size();
    }

    public int getOnlineUserCount() {
        return userSessions.size();
    }
}
