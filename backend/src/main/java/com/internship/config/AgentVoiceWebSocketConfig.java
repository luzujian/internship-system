package com.internship.config;

import com.internship.agent.business.AgentVoiceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
public class AgentVoiceWebSocketConfig implements WebSocketConfigurer {
    private final AgentVoiceService voice;
    private final String origins;
    public AgentVoiceWebSocketConfig(AgentVoiceService voice,
            @Value("${agent.voice.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}") String origins){this.voice=voice;this.origins=origins;}
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry){
        registry.addHandler(voice,"/ws/agent-voice").setAllowedOrigins(origins.split(","));
    }
}
