package com.internship.config;

import com.internship.websocket.AnnouncementWebSocketHandler;
import com.internship.websocket.WebSocketAuthInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private AnnouncementWebSocketHandler announcementWebSocketHandler;

    @Autowired
    private WebSocketAuthInterceptor webSocketAuthInterceptor;

    // 复用部署时的明确域名白名单，兼容 HTTPS 在反向代理处终止的场景。
    @Value("${ALLOWED_ORIGINS:}")
    private String allowedOrigins = "";

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(announcementWebSocketHandler, "/ws/announcement")
                .addInterceptors(webSocketAuthInterceptor)
                .setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                        .map(String::trim)
                        .filter(origin -> !origin.isEmpty() && !origin.contains("*"))
                        .toArray(String[]::new));
    }
}
