package com.internship.utils;

import com.internship.config.TrustedProxyConfig;
import jakarta.servlet.http.HttpServletRequest;

/**
 * IP 地址工具类
 * 只有连接来源是已配置的可信反向代理时才读取转发头；否则仅使用连接来源 IP
 */
public class IpUtils {

    private IpUtils() {
        // 工具类不允许实例化
    }

    /**
     * 获取客户端真实 IP 地址
     * 未配置可信代理时仅返回连接来源 IP，避免审计 IP 被客户端伪造的转发头污染
     */
    public static String getClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        String fallback = remoteAddr != null && !remoteAddr.isEmpty() ? remoteAddr : "未知";

        if (!TrustedProxyConfig.isTrustedProxy(remoteAddr)) {
            return fallback;
        }

        String ip = firstForwardedIp(request);
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            return fallback;
        }
        return ip;
    }

    /**
     * 按优先级读取代理转发头，X-Forwarded-For 可能包含多个 IP（客户端, 代理1, 代理2），取第一个
     */
    private static String firstForwardedIp(HttpServletRequest request) {
        String[] headers = {
                "X-Forwarded-For",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_CLIENT_IP",
                "HTTP_X_FORWARDED_FOR"
        };
        for (String header : headers) {
            String value = request.getHeader(header);
            if (value != null && !value.isEmpty() && !"unknown".equalsIgnoreCase(value)) {
                return value.split(",")[0].trim();
            }
        }
        return null;
    }
}
