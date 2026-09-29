package com.internship.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * 审计可信反向代理配置（audit.trusted-proxies，逗号分隔，支持精确 IP 与 IPv4 CIDR）。
 * 只有连接来源命中配置时才信任 X-Forwarded-For 等转发头；
 * 未配置可信代理时仅使用连接来源 IP，防止审计 IP 被客户端伪造。
 */
@Slf4j
@Component
public class TrustedProxyConfig {

    private static volatile List<String> trustedProxies = List.of();

    public TrustedProxyConfig(@Value("${audit.trusted-proxies:}") String configuredProxies) {
        List<String> entries = new ArrayList<>();
        for (String entry : configuredProxies.split(",")) {
            String trimmed = entry.trim();
            if (!trimmed.isEmpty()) {
                entries.add(trimmed);
            }
        }
        trustedProxies = List.copyOf(entries);
        log.info("审计可信代理配置完成: {} 条", entries.size());
    }

    /**
     * 判断连接来源是否为已配置的可信反向代理
     */
    public static boolean isTrustedProxy(String remoteAddr) {
        if (remoteAddr == null || remoteAddr.isEmpty()) {
            return false;
        }
        for (String entry : trustedProxies) {
            if (matches(entry, remoteAddr)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(String entry, String remoteAddr) {
        // 精确 IP（含 IPv6）直接字符串比较
        if (!entry.contains("/")) {
            return entry.equals(remoteAddr) || normalize(entry).equals(normalize(remoteAddr));
        }

        // IPv4 CIDR 匹配，如 172.18.0.0/16
        try {
            String[] parts = entry.split("/", 2);
            byte[] network = InetAddress.getByName(parts[0]).getAddress();
            int prefix = Integer.parseInt(parts[1]);
            if (network.length != 4 || prefix < 0 || prefix > 32) {
                return false;
            }
            byte[] addr = InetAddress.getByName(remoteAddr).getAddress();
            if (addr.length != 4) {
                return false;
            }
            long mask = prefix == 0 ? 0L : (-1L << (32 - prefix)) & 0xFFFFFFFFL;
            return (toLong(addr) & mask) == (toLong(network) & mask);
        } catch (UnknownHostException | NumberFormatException e) {
            return false;
        }
    }

    private static String normalize(String ip) {
        try {
            return InetAddress.getByName(ip).getHostAddress();
        } catch (UnknownHostException e) {
            return ip;
        }
    }

    private static long toLong(byte[] addr) {
        long value = 0;
        for (byte b : addr) {
            value = (value << 8) | (b & 0xFF);
        }
        return value;
    }
}
