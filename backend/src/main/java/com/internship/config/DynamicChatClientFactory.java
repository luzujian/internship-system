package com.internship.config;

import com.internship.entity.AIModel;
import com.internship.service.impl.AIConfigRefreshServiceImpl;
import com.internship.util.AiEndpointSupport;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按模型动态构建 ChatClient。
 *
 * <p>ccswitch 式多接入点：每个模型行可独立配置 {@code apiEndpoint}/{@code apiKey}，
 * 为空时回退全局环境变量（spring.ai.deepseek.*）。独立配置的模型使用专属
 * DeepSeekApi 客户端（按 地址+Key+模型+参数 指纹缓存复用）；未配置独立
 * 接入点的行沿用注入的全局客户端，行为与此前完全一致。</p>
 */
public class DynamicChatClientFactory {

    private static final Logger log = LoggerFactory.getLogger(DynamicChatClientFactory.class);

    /** Legacy selectors now follow the administrator's global default. */
    public String resolveModelCode(String requested) {
        AIModel configured = aiConfigRefreshService.getDefaultModelConfig();
        return configured == null ? requested : configured.getModelCode();
    }

    private final DeepSeekApi deepSeekApi;
    private final AIConfigRefreshServiceImpl aiConfigRefreshService;

    @Value("${spring.ai.deepseek.base-url}")
    private String previewBaseUrl;

    @Value("${spring.ai.deepseek.api-key}")
    private String previewApiKey;

    /** AIConfig 显式传入的全局兜底（与上面 @Value 同源，显式传参便于单测与排查）。 */
    private String globalBaseUrl;
    private String globalApiKey;

    /** 独立接入点客户端缓存：指纹 -> 模型，模型行改配后指纹变化自动重建。 */
    private final ConcurrentHashMap<String, DeepSeekChatModel> dedicatedModels = new ConcurrentHashMap<>();

    public DynamicChatClientFactory(DeepSeekApi deepSeekApi, AIConfigRefreshServiceImpl aiConfigRefreshService) {
        this.deepSeekApi = deepSeekApi;
        this.aiConfigRefreshService = aiConfigRefreshService;
    }

    public DynamicChatClientFactory(DeepSeekApi deepSeekApi, AIConfigRefreshServiceImpl aiConfigRefreshService,
                                    String globalBaseUrl, String globalApiKey) {
        this(deepSeekApi, aiConfigRefreshService);
        this.globalBaseUrl = globalBaseUrl;
        this.globalApiKey = globalApiKey;
    }

    /** 预览专用：连接2秒/读取10秒上限，maxAttempts=1 不重试；不影响其他模型调用的客户端。 */
    public ChatClient createPreviewChatClient(String modelCode, String systemPrompt) {
        return createPreviewChatClient(modelCode, systemPrompt, 10000);
    }

    /** Complex automation drafts may need longer than a short UI intent. Still bounded, without retries. */
    public ChatClient createPreviewChatClient(String modelCode, String systemPrompt, int readTimeoutMillis) {
        AIModel configured = aiConfigRefreshService.getDefaultModelConfig();
        if (configured != null) modelCode = configured.getModelCode();
        String endpoint = configured == null ? null : AiEndpointSupport.normalizeQuiet(configured.getApiEndpoint());
        String key = configured == null ? null : configured.getApiKey();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2000);
        requestFactory.setReadTimeout(Math.max(1000, Math.min(30000, readTimeoutMillis)));
        DeepSeekApi previewApi = DeepSeekApi.builder()
                .baseUrl(AiEndpointSupport.isBlank(endpoint) ? effectiveGlobalBaseUrl() : endpoint)
                .apiKey(AiEndpointSupport.isBlank(key) ? effectiveGlobalApiKey() : key)
                .restClientBuilder(RestClient.builder().requestInterceptor(com.internship.util.QwenRequestSupport.interceptor()).requestFactory(requestFactory))
                .build();
        DeepSeekChatModel chatModel = DeepSeekChatModel.builder()
                .deepSeekApi(previewApi)
                .defaultOptions(DeepSeekChatOptions.builder().model(modelCode).build())
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).build())
                .build();
        return ChatClient.builder(chatModel).defaultSystem(systemPrompt).build();
    }

    public ChatClient createChatClient(String modelCode, String systemPrompt) {
        AIModel modelConfig = aiConfigRefreshService.getDefaultModelConfig();
        if (modelConfig != null) modelCode = modelConfig.getModelCode();
        else modelConfig = aiConfigRefreshService.getModelConfig(modelCode);
        if (modelConfig == null) {
            log.warn("未找到模型配置: {}，使用默认配置", modelCode);
            modelConfig = createDefaultConfig();
        }

        String effectiveModel = modelCode;
        double temperature = modelConfig.getTemperature().doubleValue();
        int maxTokens = modelConfig.getMaxTokens();

        String endpoint = AiEndpointSupport.normalizeQuiet(modelConfig.getApiEndpoint());
        String apiKey = AiEndpointSupport.isBlank(modelConfig.getApiKey()) ? null : modelConfig.getApiKey().trim();
        String globalEndpoint = AiEndpointSupport.normalizeQuiet(effectiveGlobalBaseUrl());
        String globalKey = AiEndpointSupport.isBlank(effectiveGlobalApiKey()) ? null : effectiveGlobalApiKey().trim();

        boolean useDedicated = !AiEndpointSupport.isBlank(endpoint) || !AiEndpointSupport.isBlank(apiKey);
        if (!useDedicated) {
            endpoint = globalEndpoint;
            apiKey = globalKey;
        } else {
            if (AiEndpointSupport.isBlank(endpoint)) {
                endpoint = globalEndpoint;
            }
            if (AiEndpointSupport.isBlank(apiKey)) {
                apiKey = globalKey;
            }
        }
        // The administrator controls the exact provider model identifier.
        effectiveModel = modelCode;

        DeepSeekChatModel chatModel;
        if (!useDedicated || AiEndpointSupport.isBlank(endpoint)) {
            // 与此前一致：走全局注入客户端（默认重试策略保持不变）。
            chatModel = DeepSeekChatModel.builder()
                    .deepSeekApi(deepSeekApi)
                    .defaultOptions(DeepSeekChatOptions.builder()
                            .model(effectiveModel)
                            .temperature(temperature)
                            .maxTokens(maxTokens)
                            .build())
                    .build();
        } else {
            final String finalEndpoint = endpoint;
            final String finalModel = effectiveModel;
            final String finalKey = apiKey;
            final String fingerprint = finalEndpoint + "\n" + finalKey + "\n"
                    + finalModel + "\n" + temperature + "\n" + maxTokens;
            if (dedicatedModels.size() > 32) {
                dedicatedModels.clear();
            }
            chatModel = dedicatedModels.computeIfAbsent(fingerprint, key -> {
                log.info("为模型 {} 构建独立接入客户端，地址: {}", finalModel, finalEndpoint);
                RestClient.Builder dedicatedRest = RestClient.builder().requestInterceptor(com.internship.util.QwenRequestSupport.interceptor())
                        .defaultHeader("User-Agent", AiEndpointSupport.CLIENT_USER_AGENT);
                var dedicatedWeb = org.springframework.web.reactive.function.client.WebClient.builder()
                        .filter(com.internship.util.QwenRequestSupport.filter())
                        .defaultHeader("User-Agent", AiEndpointSupport.CLIENT_USER_AGENT);
                // 中转网关（如 Zen Go）要求 x-opencode-session 路由头，官方直连时为空 Map，无影响。
                for (Map.Entry<String, String> gatewayHeader
                        : AiEndpointSupport.extraHeaders(finalEndpoint, finalModel).entrySet()) {
                    dedicatedRest = dedicatedRest.defaultHeader(gatewayHeader.getKey(), gatewayHeader.getValue());
                    dedicatedWeb = dedicatedWeb.defaultHeader(gatewayHeader.getKey(), gatewayHeader.getValue());
                }
                DeepSeekApi dedicatedApi = DeepSeekApi.builder()
                        .baseUrl(finalEndpoint)
                        .apiKey(finalKey)
                        .restClientBuilder(dedicatedRest)
                        .webClientBuilder(dedicatedWeb)
                        .build();
                return DeepSeekChatModel.builder()
                        .deepSeekApi(dedicatedApi)
                        .defaultOptions(DeepSeekChatOptions.builder()
                                .model(finalModel)
                                .temperature(temperature)
                                .maxTokens(maxTokens)
                                .build())
                        .build();
            });
        }

        return ChatClient.builder(chatModel)
                .defaultSystem(systemPrompt)
                .build();
    }

    private String effectiveGlobalBaseUrl() {
        return AiEndpointSupport.isBlank(globalBaseUrl) ? previewBaseUrl : globalBaseUrl;
    }

    private String effectiveGlobalApiKey() {
        return AiEndpointSupport.isBlank(globalApiKey) ? previewApiKey : globalApiKey;
    }

    private AIModel createDefaultConfig() {
        AIModel defaultConfig = new AIModel();
        defaultConfig.setTemperature(new java.math.BigDecimal("0.7"));
        defaultConfig.setMaxTokens(2000);
        return defaultConfig;
    }
}
