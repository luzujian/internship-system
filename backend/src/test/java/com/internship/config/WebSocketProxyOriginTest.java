package com.internship.config;

import com.internship.websocket.AnnouncementWebSocketHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.socket.config.annotation.ServletWebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.OriginHandshakeInterceptor;
import org.springframework.web.socket.server.support.WebSocketHttpRequestHandler;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebSocketProxyOriginTest {
    @Test
    void configuredHttpsSiteCanConnectThroughHttpReverseProxy() throws Exception {
        var handler = registeredHandler(" https://intern.gdmu.school , https://other.example.test ");
        assertTrue(accepts(handler, "https://intern.gdmu.school"));
        assertTrue(accepts(handler, "https://other.example.test"));
        assertFalse(accepts(handler, "https://untrusted.example.test"));
        assertFalse(accepts(handler, "https://intern.gdmu.school.evil.test"));
        assertFalse(accepts(handler, "https://intern.gdmu.school:8443"));
    }

    @Test
    void noAllowlistPreservesSameOriginRestriction() throws Exception {
        var handler = registeredHandler("");
        assertTrue(accepts(handler, "http://intern.gdmu.school"));
        // Reproduces the original HTTPS-proxy mismatch before configuration is applied.
        assertFalse(accepts(handler, "https://intern.gdmu.school"));
    }

    @Test
    void wildcardConfigurationCannotAllowArbitraryOrigins() throws Exception {
        var handler = registeredHandler("*, https://*.example.test, https://intern.gdmu.school");
        assertTrue(accepts(handler, "https://intern.gdmu.school"));
        assertFalse(accepts(handler, "https://attacker.example.test"));
    }

    private WebSocketHttpRequestHandler registeredHandler(String allowedOrigins) {
        var config = new WebSocketConfig();
        ReflectionTestUtils.setField(config, "announcementWebSocketHandler", new AnnouncementWebSocketHandler());
        ReflectionTestUtils.setField(config, "allowedOrigins", allowedOrigins);
        var registry = new ServletWebSocketHandlerRegistry();
        config.registerWebSocketHandlers(registry);
        var mapping = (SimpleUrlHandlerMapping) registry.getHandlerMapping();
        return (WebSocketHttpRequestHandler) mapping.getUrlMap().get("/ws/announcement");
    }

    private boolean accepts(WebSocketHttpRequestHandler handler, String origin) throws Exception {
        var request = new MockHttpServletRequest("GET", "/ws/announcement");
        request.setScheme("http");
        request.setServerName("intern.gdmu.school");
        request.setServerPort(80);
        request.addHeader("Origin", origin);
        var interceptor = handler.getHandshakeInterceptors().stream()
                .filter(OriginHandshakeInterceptor.class::isInstance).findFirst().orElseThrow();
        return interceptor.beforeHandshake(new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(new MockHttpServletResponse()),
                handler.getWebSocketHandler(), new HashMap<>());
    }
}
