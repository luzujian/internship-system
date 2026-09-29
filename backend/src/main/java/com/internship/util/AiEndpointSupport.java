package com.internship.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 模型接入点归一化 / Key 脱敏小工具。
 *
 * <p>约定（与管理端表单占位提示保持一致）：apiEndpoint 填写 OpenAI 兼容服务的
 * 基地址，例如官方 {@code https://api.deepseek.com}，或中转站给的
 * {@code https://xxx/v1}（{@code /v1} 必须保留，中转网关靠它路由）。
 * 后端只做归一化：去空白、去末尾斜杠、去末尾 {@code /chat/completions}
 * （防止有人把完整接口 URL 粘进来导致拼接重复）。</p>
 */
public final class AiEndpointSupport {

    /** Zen Go 这类中转网关要求的路由头：缺失时厂商直接 400 MissingSessionID。只有 host 命中网关域名的请求才带该头，官方直连不受影响。 */
    public static final String OPENCODE_SESSION_HEADER = "x-opencode-session";

    /** 发往厂商的 UA，便于网关侧排查问题时识别来源。 */
    public static final String CLIENT_USER_AGENT = "DeepIntern-AI/1.0";

    /** 需要附带路由头的网关 host 片段（小写匹配）。 */
    private static final String GATEWAY_HOST_MARKER = "opencode.ai/zen";

    private AiEndpointSupport() {
    }

    /** 按归一化后的接入点返回需要附加的请求头；非网关地址返回空 Map，调用方直接循环追加即可。 */
    public static Map<String, String> extraHeaders(String normalizedEndpoint, String modelCode) {
        if (normalizedEndpoint == null
                || !normalizedEndpoint.toLowerCase().contains(GATEWAY_HOST_MARKER)) {
            return Collections.emptyMap();
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(OPENCODE_SESSION_HEADER, sessionIdFor(modelCode));
        return headers;
    }

    /** 网关路由会话 ID：deepintern-加清洗后 modelCode；只保留小写字母/数字/点/横线/下划线，其余转横线。 */
    public static String sessionIdFor(String modelCode) {
        String base = isBlank(modelCode) ? "" : modelCode.trim().toLowerCase().replaceAll("[^a-z0-9._-]+", "-");
        base = base.replaceAll("^-+", "").replaceAll("-+$", "");
        if (base.isEmpty()) {
            return "deepintern";
        }
        String id = "deepintern-" + base;
        return id.length() <= 64 ? id : id.substring(0, 64);
    }

    /** 是否为空（null / 空串 / 纯空白）。 */
    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 归一化接入点：去首尾空白、去末尾斜杠、去末尾 {@code /v1}（OpenAI 兼容写法）。
     * 返回 null 表示输入为空（调用方含义：沿用全局配置 / 保持原值）。
     *
     * @throws IllegalArgumentException 地址非空但不是 http(s) 开头时抛出
     */
    public static String normalizeEndpoint(String endpoint) {
        if (isBlank(endpoint)) {
            return null;
        }
        String value = endpoint.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        // 有人把完整接口 URL（如 .../v1/chat/completions）粘进来时退回基地址，
        // 避免调用方再拼一次 /chat/completions 造成重复；/v1 必须保留。
        if (value.toLowerCase().endsWith("/chat/completions")) {
            value = value.substring(0, value.length() - "/chat/completions".length());
            while (value.endsWith("/")) {
                value = value.substring(0, value.length() - 1);
            }
        }
        String lower = value.toLowerCase();
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw new IllegalArgumentException("API 地址必须以 http:// 或 https:// 开头");
        }
        if (value.length() > 255) {
            throw new IllegalArgumentException("API 地址过长（最多 255 个字符）");
        }
        return value;
    }

    /**
     * 安静版归一化：空输入返回 null；格式非法同样返回 null（调用方回退全局配置），
     * 需要精确报错时请用 {@link #normalizeEndpoint(String)}。
     */
    public static String normalizeQuiet(String endpoint) {
        if (isBlank(endpoint)) {
            return null;
        }
        try {
            return normalizeEndpoint(endpoint);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    /** 有效地址（归一化后）与原值是否一致；不一致说明用户写法被纠正过，可用于前端提示。 */
    public static boolean endpointWasNormalized(String raw, String normalized) {
        if (raw == null || normalized == null) {
            return false;
        }
        return !raw.trim().equals(normalized);
    }

    /**
     * Key 脱敏：保留前 3 + 后 4，其余打码；过短的 Key 整体打码。
     * null / 空返回空串（调用方再按“未配置”展示）。
     */
    public static String maskApiKey(String apiKey) {
        if (isBlank(apiKey)) {
            return "";
        }
        String key = apiKey.trim();
        if (key.length() <= 8) {
            return "****";
        }
        return key.substring(0, 3) + "****" + key.substring(key.length() - 4);
    }
}
