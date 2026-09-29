package com.internship.agent.business;

import com.internship.entity.AIModel;
import com.internship.util.AiEndpointSupport;
import java.util.Set;

/**
 * 只读助手的模型参数来源：优先 ai_model 表的默认行（is_default=1 且启用），
 * 缺失或异常时回落到 agent.model 配置与内置默认值，避免后台配置异常导致助手不可用。
 * 取 model_code / temperature / max_tokens，以及该行的独立 apiEndpoint / apiKey；
 * 行内未配时回落环境配置（调用方决定），不随行切换的旧约束已解除。
 */
public final class AgentModelSettings {
    public static final String FALLBACK_MODEL = "deepseek-flash";
    public static final double FALLBACK_TEMPERATURE = 0.2d;
    public static final int FALLBACK_MAX_TOKENS = 2200;
    private static final Set<String> ALIASES = Set.of("deepseek-v4-flash");
    private static final int MAX_TOKENS_CEILING = 8000;

    private AgentModelSettings() {}

    public record Resolved(String modelCode, double temperature, int maxTokens, String source,
                           String endpoint, String apiKey) {
        /** 兼容旧调用：只关心前四个分量时使用。 */
        public Resolved(String modelCode, double temperature, int maxTokens, String source) {
            this(modelCode, temperature, maxTokens, source, null, null);
        }
    }

    public static Resolved resolve(AIModel row, String configuredModel) {
        String fallbackModel = normalize(configuredModel);
        if (row == null || row.getModelCode() == null || row.getModelCode().isBlank()) {
            return new Resolved(fallbackModel, FALLBACK_TEMPERATURE, FALLBACK_MAX_TOKENS, "fallback");
        }
        double temperature = row.getTemperature() == null ? FALLBACK_TEMPERATURE : clamp(row.getTemperature().doubleValue());
        int maxTokens = row.getMaxTokens() == null || row.getMaxTokens() <= 0
                ? FALLBACK_MAX_TOKENS : Math.min(row.getMaxTokens(), MAX_TOKENS_CEILING);
        String endpoint = AiEndpointSupport.normalizeQuiet(row.getApiEndpoint());
        String rawCode = row.getModelCode().trim();
        // Explicit administrator model identifiers must be sent verbatim.
        String modelCode = row.getModelCode().trim();
        return new Resolved(modelCode, temperature, maxTokens, "ai_model.default",
                AiEndpointSupport.normalizeQuiet(row.getApiEndpoint()),
                AiEndpointSupport.isBlank(row.getApiKey()) ? null : row.getApiKey().trim());
    }

    static String normalize(String code) {
        if (code == null) return FALLBACK_MODEL;
        String value = code.trim();
        if (value.isEmpty()) return FALLBACK_MODEL;
        return ALIASES.contains(value) ? FALLBACK_MODEL : value;
    }

    static double clamp(double value) {
        if (Double.isNaN(value)) return FALLBACK_TEMPERATURE;
        return Math.max(0d, Math.min(2d, value));
    }
}
