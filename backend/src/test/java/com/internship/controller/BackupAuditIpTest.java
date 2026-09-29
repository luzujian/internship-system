package com.internship.controller;
import com.internship.config.TrustedProxyConfig;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
class BackupAuditIpTest {
    @Test void forwardedAddressRequiresTrustedProxy() {
        new TrustedProxyConfig("");
        var request=new MockHttpServletRequest();request.setRemoteAddr("198.51.100.1");request.addHeader("X-Forwarded-For","203.0.113.9");
        assertEquals("198.51.100.1",ReflectionTestUtils.invokeMethod(new BackupController(),"getClientIp",request));
        try {
            new TrustedProxyConfig("198.51.100.1");
            assertEquals("203.0.113.9",ReflectionTestUtils.invokeMethod(new BackupController(),"getClientIp",request));
        } finally {new TrustedProxyConfig("");}
    }
}
