package com.gdmu.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdmu.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
@Slf4j
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    @Autowired
    private JwtUtils jwtUtils;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, 
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        log.info("WebSocket握手请求开始");
        
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest httpRequest = servletRequest.getServletRequest();
            
            String token = httpRequest.getParameter("token");
            if (token == null || token.isEmpty()) {
                String authHeader = httpRequest.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    token = authHeader.substring(7);
                }
            }
            
            if (token == null || token.isEmpty()) {
                log.warn("WebSocket握手失败：缺少token");
                return false;
            }
            
            try {
                Claims claims = jwtUtils.parseToken(token);
                
                Long userId = claims.get("id", Long.class);
                if (userId == null) {
                    userId = claims.get("userId", Long.class);
                }
                String username = claims.get("username", String.class);
                String role = claims.get("role", String.class);
                
                if (userId == null) {
                    log.warn("WebSocket握手失败：用户ID为空");
                    return false;
                }
                
                attributes.put("userId", userId);
                attributes.put("username", username);
                attributes.put("role", role);
                
                log.info("WebSocket握手成功：userId={}, username={}, role={}", userId, username, role);
                return true;
                
            } catch (Exception e) {
                log.error("WebSocket握手失败：token验证异常 - {}", e.getMessage());
                return false;
            }
        }
        
        log.warn("WebSocket握手失败：请求类型不正确");
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, 
                               WebSocketHandler wsHandler, Exception exception) {
        if (exception != null) {
            log.error("WebSocket握手后异常：{}", exception.getMessage());
        } else {
            log.info("WebSocket握手完成");
        }
    }
}
