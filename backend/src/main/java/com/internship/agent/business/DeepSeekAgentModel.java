package com.internship.agent.business;

import com.internship.entity.AIModel;
import com.internship.service.AIModelService;
import com.internship.util.AiEndpointSupport;
import com.internship.util.QwenRequestSupport;
import com.internship.util.AiCallGuard;
import com.internship.config.AiRuntimeProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.*;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import reactor.core.publisher.Flux;

/**
 * Agent 对话/智能填表共用模型入口。各链路总等待上限（P4 发布门禁， regression 见 DeepSeekAgentModelTimeoutTest）：
 * <ul>
 * <li>本入口：连接10秒 + 读取40秒，maxAttempts=1 不重试，单次调用约50秒封顶；</li>
 * <li>教师/管理员 preview（DynamicChatClientFactory.createPreviewChatClient）：连接2秒 + 读取10秒，不重试，约12秒封顶；</li>
 * <li>前端 axios 外层 60 秒兜底，超时中文提示且不卡 loading。</li>
 * </ul>
 * 默认模型行若配了独立 apiEndpoint/apiKey，本入口自动切换到该接入点（与 DynamicChatClientFactory 一致）；
 * 未配则沿用环境配置。超时与不重试上限在两种接入点下保持一致。
 * Shares existing provider configuration; explicit tool execution stays in the coordinator.
 */
@Slf4j
@Service
public class DeepSeekAgentModel implements AgentModel {
    private final String envApiKey;
    private final String envBaseUrl;
    private final String configuredModel;
    private final AIModelService aiModelService;
    private final AiCallGuard calls;
    private final AiRuntimeProperties limits;
    private volatile AgentModelSettings.Resolved current;
    /** 独立接入点客户端缓存：地址+Key 指纹 -> 模型，默认行改配后自动重建。 */
    private final Map<String, DeepSeekChatModel> dedicatedModels = new ConcurrentHashMap<>();

    public DeepSeekAgentModel(String key, String baseUrl, String configuredModel, AIModelService aiModelService) {
        this(key, baseUrl, configuredModel, aiModelService,
                new AiCallGuard(new AiRuntimeProperties()), new AiRuntimeProperties());
    }

    @Autowired
    public DeepSeekAgentModel(@Value("${spring.ai.deepseek.api-key}") String key,
                             @Value("${spring.ai.deepseek.base-url}") String baseUrl,
                             @Value("${agent.model:deepseek-flash}") String configuredModel,
                             AIModelService aiModelService, AiCallGuard calls, AiRuntimeProperties limits) {
        this.envApiKey = key;
        this.envBaseUrl = baseUrl;
        this.configuredModel=configuredModel;
        this.aiModelService=aiModelService;
        this.calls=calls;
        this.limits=limits;
    }

    /** modelCode 仅用于决定是否附加网关路由头（如 Zen Go 的 x-opencode-session），官方直连无影响。 */
    private DeepSeekChatModel buildModel(String key, String baseUrl, String modelCode, List<Message> messages, boolean answerReview) {
        var factory=new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(limits.getConnectTimeoutMillis()); factory.setReadTimeout(limits.getReadTimeoutMillis());
        RestClient.Builder rest = RestClient.builder().requestFactory(factory).requestInterceptor(QwenRequestSupport.interceptor(messages))
                .defaultHeader("User-Agent", AiEndpointSupport.CLIENT_USER_AGENT);
        WebClient.Builder web=WebClient.builder().filter(answerReview ? QwenRequestSupport.reviewFilter() : QwenRequestSupport.filter(messages)).clientConnector(new JdkClientHttpConnector(
                java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofMillis(limits.getConnectTimeoutMillis())).build()))
                .defaultHeader("User-Agent",AiEndpointSupport.CLIENT_USER_AGENT);
        // 中转网关（如 Zen Go）要求 x-opencode-session 路由头，官方直连时为空 Map，无影响。
        for (Map.Entry<String, String> gatewayHeader
                : AiEndpointSupport.extraHeaders(AiEndpointSupport.normalizeQuiet(baseUrl), modelCode).entrySet()) {
            rest = rest.defaultHeader(gatewayHeader.getKey(), gatewayHeader.getValue());
            web = web.defaultHeader(gatewayHeader.getKey(),gatewayHeader.getValue());
        }
        var api=DeepSeekApi.builder().apiKey(key).baseUrl(baseUrl).completionsPath("/chat/completions")
                .restClientBuilder(rest).webClientBuilder(web).build();
        return DeepSeekChatModel.builder().deepSeekApi(api)
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).build()).build();
    }

    @Override public ChatResponse call(List<Message> messages,List<AgentReadTools.Spec> specs) {
        return calls.call(() -> {
            var settings=resolve();
            return selectModel(settings,messages).call(prompt(messages,specs,settings));
        });
    }

    @Override public Flux<ChatResponse> stream(List<Message> messages,List<AgentReadTools.Spec> specs){
        return stream(messages, specs, false);
    }

    @Override public Flux<ChatResponse> review(List<Message> messages) {
        return stream(messages, List.of(), true);
    }

    private Flux<ChatResponse> stream(List<Message> messages,List<AgentReadTools.Spec> specs,boolean answerReview){
        return calls.stream(()->{
            var settings=resolve();
            StringBuilder reasoning = new StringBuilder();
            return selectModel(settings,messages,answerReview).stream(prompt(messages,specs,settings))
                    .map(response -> preserveReasoning(response, reasoning));
        });
    }

    private Prompt prompt(List<Message> messages,List<AgentReadTools.Spec> specs,AgentModelSettings.Resolved settings){
        var builder=DeepSeekChatOptions.builder().model(settings.modelCode())
                .temperature(settings.temperature()).maxTokens(settings.maxTokens());
        // 空工具列表（如填表意图解析）退化为普通调用；有工具时保持显式手动执行。
        if(specs!=null&&!specs.isEmpty()){
            List<ToolCallback> callbacks=specs.stream().map(spec->(ToolCallback)new ToolCallback(){
                @Override public ToolDefinition getToolDefinition(){return ToolDefinition.builder().name(spec.name()).description(spec.description()).inputSchema(spec.schema()).build();}
                @Override public String call(String input){throw new IllegalStateException("Automatic tool execution is disabled");}
            }).toList();
            builder.toolCallbacks(callbacks).internalToolExecutionEnabled(false);
        }
        return new Prompt(messages,builder.build());
    }

    /** Resolve endpoint/key together; reuse clients only when they contain no conversation state. */
    private DeepSeekChatModel selectModel(AgentModelSettings.Resolved settings, List<Message> messages) {
        return selectModel(settings, messages, false);
    }

    private DeepSeekChatModel selectModel(AgentModelSettings.Resolved settings, List<Message> messages, boolean answerReview) {
        AiEndpointSupport.Connection connection;
        try {
            connection = AiEndpointSupport.resolveConnection(settings.endpoint(), settings.apiKey(), envBaseUrl, envApiKey);
        } catch (IllegalArgumentException e) {
            throw new AgentException("MODEL_UNAVAILABLE", e.getMessage(), 503);
        }
        String endpoint = connection.endpoint();
        String key = connection.apiKey();
        // Spring AI 1.0.2 drops reasoning_content from assistant request messages.
        // Scope the repair to this request, never cache conversation data across users.
        if (messages.stream().anyMatch(message -> QwenRequestSupport.reasoningContent(message) != null)) {
            return buildModel(key, endpoint, settings.modelCode(), List.copyOf(messages), answerReview);
        }
        String fingerprint = endpoint + "\n" + key + "\n" + settings.modelCode() + "\nreview=" + answerReview;
        if (dedicatedModels.size() > 8) {
            dedicatedModels.clear();
        }
        return dedicatedModels.computeIfAbsent(fingerprint, k -> {
            log.info("Agent 模型使用接入点，地址: {}", endpoint);
            return buildModel(key, endpoint, settings.modelCode(), List.of(), answerReview);
        });
    }

    /** MessageAggregator keeps metadata but loses the DeepSeekAssistantMessage subtype. */
    private ChatResponse preserveReasoning(ChatResponse response, StringBuilder reasoning) {
        if (response.getResult() == null) return response;
        var result = response.getResult();
        var output = result.getOutput();
        String delta = QwenRequestSupport.reasoningContent(output);
        if (delta != null) reasoning.append(delta);
        if (reasoning.isEmpty()) return response;
        var metadata = new java.util.HashMap<>(output.getMetadata());
        metadata.put("reasoning_content", reasoning.toString());
        var preserved = new AssistantMessage(output.getText(), metadata, output.getToolCalls());
        return new ChatResponse(List.of(new Generation(preserved, result.getMetadata())), response.getMetadata());
    }

    /** 每次调用重新解析，后台修改默认模型后无需重启；仅在参数变化时打印一行日志，便于核对来源。 */
    AgentModelSettings.Resolved resolve(){
        AIModel row=aiModelService.findDefaultModel();
        if(row==null || !Integer.valueOf(1).equals(row.getStatus()) || row.getModelCode()==null || row.getModelCode().isBlank())
            throw new AgentException("MODEL_UNAVAILABLE","请管理员在 AI 大模型选择页面启用并设置默认模型。",503);
        AgentModelSettings.Resolved next;
        try {
            next=AgentModelSettings.resolve(row,configuredModel);
        } catch (IllegalArgumentException e) {
            throw new AgentException("MODEL_UNAVAILABLE",e.getMessage(),503);
        }
        var previous=current;
        if(previous==null||!previous.equals(next)){
            current=next;
            log.info("Agent 模型参数：model={} temperature={} maxTokens={} 来源={}",
                    next.modelCode(),next.temperature(),next.maxTokens(),next.source());
        }
        return next;
    }
}
