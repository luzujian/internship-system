package com.gdmu.utils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * IP 地址工具类
 * 从请求中提取真实客户端 IP，按优先级检查各代理头
 */
public class IpUtils {

    private IpUtils() {
        // 工具类不允许实例化
    }

    /**
     * 获取客户端真实 IP 地址
     * 优先级：X-Forwarded-For > Proxy-Client-IP > WL-Proxy-Client-IP > HTTP_CLIENT_IP > getRemoteAddr
     */
    public static String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // X-Forwarded-For 可能包含多个 IP（客户端, 代理1, 代理2），取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "未知";
    }
}
