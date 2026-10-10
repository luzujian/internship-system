package com.internship.config;

import com.internship.entity.AIModel;
import com.internship.service.impl.AIConfigRefreshServiceImpl;
import com.internship.util.AiEndpointSupport;
import com.internship.util.AiCallGuard;
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
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按模型动态构建 ChatClient。
 *
 * <p>ccswitch 式多接入点：每个模型行可独立配置 {@code apiEndpoint}/{@code apiKey}，
 * 两者均为空时回退全局环境变量（spring.ai.deepseek.*），独立地址必须配置独立 Key。独立配置的模型使用专属
 * DeepSeekApi 客户端（按 地址+Key+模型+参数 指纹缓存复用）；全局与独立
 * 接入点使用同样的超时、单次尝试和共享容量限制。</p>
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
    private AiRuntimeProperties limits = new AiRuntimeProperties();
    private AiCallGuard calls = new AiCallGuard(limits);

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

    public DynamicChatClientFactory(DeepSeekApi deepSeekApi, AIConfigRefreshServiceImpl aiConfigRefreshService,
                                    String globalBaseUrl, String globalApiKey, AiCallGuard calls, AiRuntimeProperties limits) {
        this(deepSeekApi, aiConfigRefreshService, globalBaseUrl, globalApiKey);
        this.calls = calls;
        this.limits = limits;
    }

    /** 预览专用：连接2秒/读取10秒上限，maxAttempts=1 不重试；不影响其他模型调用的客户端。 */
    public ChatClient createPreviewChatClient(String modelCode, String systemPrompt) {
        return createPreviewChatClient(modelCode, systemPrompt, 10000);
    }

    /** Complex automation drafts may need longer than a short UI intent. Still bounded, without retries. */
    public ChatClient createPreviewChatClient(String modelCode, String systemPrompt, int readTimeoutMillis) {
        AIModel configured = aiConfigRefreshService.getDefaultModelConfig();
        if (configured != null) modelCode = configured.getModelCode();
        var connection = resolveConnection(configured);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2000);
        requestFactory.setReadTimeout(Math.max(1000, Math.min(30000, readTimeoutMillis)));
        DeepSeekApi previewApi = createApi(connection, modelCode, requestFactory);
        DeepSeekChatModel chatModel = DeepSeekChatModel.builder()
                .deepSeekApi(previewApi)
                .defaultOptions(DeepSeekChatOptions.builder().model(modelCode).build())
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).build())
                .build();
        return ChatClient.builder(calls.wrap(chatModel)).defaultSystem(systemPrompt).build();
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
        double temperature = modelConfig.getTemperature() == null ? 0.7 : modelConfig.getTemperature().doubleValue();
        int maxTokens = modelConfig.getMaxTokens() == null ? 2000 : modelConfig.getMaxTokens();

        var connection = resolveConnection(modelConfig);
        // Build global and dedicated transports with the same bounded timeout/retry policy.
        final String finalEndpoint = connection.endpoint();
        final String finalModel = effectiveModel;
        final String finalKey = connection.apiKey();
        final String fingerprint = finalEndpoint + "\n" + finalKey + "\n"
                + finalModel + "\n" + temperature + "\n" + maxTokens;
        if (dedicatedModels.size() > 32) dedicatedModels.clear();
        DeepSeekChatModel chatModel = dedicatedModels.computeIfAbsent(fingerprint, key -> {
            log.info("为模型 {} 构建调用客户端，地址: {}", finalModel, finalEndpoint);
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(limits.getConnectTimeoutMillis());
            requestFactory.setReadTimeout(limits.getReadTimeoutMillis());
            DeepSeekApi dedicatedApi = createApi(connection, finalModel, requestFactory);
            return DeepSeekChatModel.builder()
                    .deepSeekApi(dedicatedApi)
                    .defaultOptions(DeepSeekChatOptions.builder()
                            .model(finalModel).temperature(temperature).maxTokens(maxTokens).build())
                    .retryTemplate(RetryTemplate.builder().maxAttempts(1).build())
                    .build();
        });

        return ChatClient.builder(calls.wrap(chatModel))
                .defaultSystem(systemPrompt)
                .build();
    }

    private String effectiveGlobalBaseUrl() {
        return AiEndpointSupport.isBlank(globalBaseUrl) ? previewBaseUrl : globalBaseUrl;
    }

    private AiEndpointSupport.Connection resolveConnection(AIModel model) {
        return AiEndpointSupport.resolveConnection(model == null ? null : model.getApiEndpoint(),
                model == null ? null : model.getApiKey(), effectiveGlobalBaseUrl(), effectiveGlobalApiKey());
    }

    /** Both preview and normal calls preserve the configured base path and gateway headers. */
    private DeepSeekApi createApi(AiEndpointSupport.Connection connection, String modelCode,
                                 SimpleClientHttpRequestFactory requestFactory) {
        RestClient.Builder rest = RestClient.builder()
                .requestInterceptor(com.internship.util.QwenRequestSupport.interceptor())
                .defaultHeader("User-Agent", AiEndpointSupport.CLIENT_USER_AGENT);
        if (requestFactory != null) rest.requestFactory(requestFactory);
        var web = org.springframework.web.reactive.function.client.WebClient.builder()
                .clientConnector(new org.springframework.http.client.reactive.JdkClientHttpConnector(
                        java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofMillis(limits.getConnectTimeoutMillis())).build()))
                .filter(com.internship.util.QwenRequestSupport.filter())
                .defaultHeader("User-Agent", AiEndpointSupport.CLIENT_USER_AGENT);
        for (Map.Entry<String, String> header : AiEndpointSupport.extraHeaders(connection.endpoint(), modelCode).entrySet()) {
            rest.defaultHeader(header.getKey(), header.getValue());
            web.defaultHeader(header.getKey(), header.getValue());
        }
        return DeepSeekApi.builder().baseUrl(connection.endpoint()).apiKey(connection.apiKey())
                .completionsPath("/chat/completions").restClientBuilder(rest).webClientBuilder(web).build();
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
