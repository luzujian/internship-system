package com.internship.config;
import com.internship.websocket.AnnouncementWebSocketHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.config.annotation.ServletWebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.*;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.mock.web.*;
import org.springframework.http.server.*;
import static org.junit.jupiter.api.Assertions.*;
class WebSocketOriginBoundaryTest {
    @Test void registeredEndpointRejectsSiblingOriginAndAllowsSameOrigin() throws Exception {
        var config=new WebSocketConfig();
        ReflectionTestUtils.setField(config,"announcementWebSocketHandler",new AnnouncementWebSocketHandler());
        var registry=new ServletWebSocketHandlerRegistry();config.registerWebSocketHandlers(registry);
        var mapping=(SimpleUrlHandlerMapping)registry.getHandlerMapping();
        var handler=(WebSocketHttpRequestHandler)mapping.getUrlMap().get("/ws/announcement");
        var origin=handler.getHandshakeInterceptors().stream().filter(OriginHandshakeInterceptor.class::isInstance).findFirst().orElseThrow();
        for (String value:java.util.List.of("https://app.example.test","https://sibling.example.test")) {
            var request=new MockHttpServletRequest("GET","/ws/announcement");request.setScheme("https");request.setServerName("app.example.test");request.setServerPort(443);request.addHeader("Origin",value);
            boolean allowed=origin.beforeHandshake(new ServletServerHttpRequest(request),new ServletServerHttpResponse(new MockHttpServletResponse()),handler.getWebSocketHandler(),new java.util.HashMap<>());
            assertEquals(value.equals("https://app.example.test"),allowed);
        }
    }
}
