package com.internship.utils;

import com.internship.config.TrustedProxyConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2/IP 解析单元测试（计划要求：IP解析、只信任配置过的反向代理转发头）。
 * 未配置可信代理时仅使用连接来源 IP；配置后才采信 X-Forwarded-For 等转发头。
 */
class IpUtilsTest {

    @AfterEach
    void resetTrustedProxies() {
        // 复位为"不信任任何代理"，避免静态状态影响其他测试
        new TrustedProxyConfig("");
    }

    private MockHttpServletRequest request(String remoteAddr, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }

    @Test
    void forwardedHeaderIsIgnoredWhenNoProxyConfigured() {
        // 未配置可信代理：伪造的 X-Forwarded-For 必须被忽略
        new TrustedProxyConfig("");
        assertEquals("203.0.113.50", IpUtils.getClientIp(request("203.0.113.50", "1.2.3.4")));
    }

    @Test
    void forwardedHeaderIsIgnoredForUntrustedRemote() {
        // 来源不在可信列表：转发头不可信
        new TrustedProxyConfig("172.18.0.3");
        assertEquals("198.51.100.7", IpUtils.getClientIp(request("198.51.100.7", "1.2.3.4")));
    }

    @Test
    void forwardedHeaderIsTrustedForExactIpProxy() {
        new TrustedProxyConfig("172.18.0.3");
        assertEquals("1.2.3.4", IpUtils.getClientIp(request("172.18.0.3", "1.2.3.4")));
    }

    @Test
    void forwardedHeaderIsTrustedForCidrProxy() {
        new TrustedProxyConfig("172.18.0.0/16");
        assertEquals("1.2.3.4", IpUtils.getClientIp(request("172.18.0.9", "1.2.3.4")));
    }

    @Test
    void cidrOutsideRangeIsNotTrusted() {
        new TrustedProxyConfig("172.18.0.0/24");
        assertEquals("172.19.0.9", IpUtils.getClientIp(request("172.19.0.9", "1.2.3.4")));
    }

    @Test
    void multiHopForwardedForTakesFirstClient() {
        new TrustedProxyConfig("172.18.0.0/16");
        assertEquals("1.2.3.4", IpUtils.getClientIp(request("172.18.0.9", "1.2.3.4, 10.0.0.1, 10.0.0.2")));
    }

    @Test
    void trustedProxyWithoutHeaderFallsBackToRemoteAddr() {
        new TrustedProxyConfig("172.18.0.0/16");
        assertEquals("172.18.0.9", IpUtils.getClientIp(request("172.18.0.9", null)));
    }

    @Test
    void trustedProxyWithUnknownHeaderFallsBackToRemoteAddr() {
        new TrustedProxyConfig("172.18.0.0/16");
        assertEquals("172.18.0.9", IpUtils.getClientIp(request("172.18.0.9", "unknown")));
    }

    @Test
    void alternateProxyHeadersAreHonoredForTrustedProxy() {
        new TrustedProxyConfig("172.18.0.4");
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("172.18.0.4");
        req.addHeader("Proxy-Client-IP", "5.6.7.8");
        assertEquals("5.6.7.8", IpUtils.getClientIp(req));
    }

    @Test
    void proxyConfigMatching() {
        new TrustedProxyConfig("10.0.0.5, 192.168.1.0/24, bad-entry/999");
        assertTrue(TrustedProxyConfig.isTrustedProxy("10.0.0.5"), "精确IP命中");
        assertTrue(TrustedProxyConfig.isTrustedProxy("192.168.1.77"), "CIDR命中");
        assertFalse(TrustedProxyConfig.isTrustedProxy("192.168.2.1"), "CIDR外不命中");
        assertFalse(TrustedProxyConfig.isTrustedProxy("10.0.0.6"), "相邻IP不命中");
        assertFalse(TrustedProxyConfig.isTrustedProxy(null), "空地址不命中");
        assertFalse(TrustedProxyConfig.isTrustedProxy(""), "空字符串不命中");
    }

    @Test
    void emptyConfigTrustsNothing() {
        new TrustedProxyConfig("");
        assertFalse(TrustedProxyConfig.isTrustedProxy("172.18.0.3"));
        assertFalse(TrustedProxyConfig.isTrustedProxy("127.0.0.1"));
    }
}
