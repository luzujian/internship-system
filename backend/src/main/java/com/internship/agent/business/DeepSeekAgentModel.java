package com.internship.agent.business;

import com.internship.entity.AIModel;
import com.internship.service.AIModelService;
import com.internship.util.AiEndpointSupport;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.*;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.annotation.Value;
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
    private final DeepSeekChatModel model;
    private final String envApiKey;
    private final String envBaseUrl;
    private final String configuredModel;
    private final AIModelService aiModelService;
    private volatile AgentModelSettings.Resolved current;
    /** 独立接入点客户端缓存：地址+Key 指纹 -> 模型，默认行改配后自动重建。 */
    private final Map<String, DeepSeekChatModel> dedicatedModels = new ConcurrentHashMap<>();

    public DeepSeekAgentModel(@Value("${spring.ai.deepseek.api-key}") String key,
                             @Value("${spring.ai.deepseek.base-url}") String baseUrl,
                             @Value("${agent.model:deepseek-flash}") String configuredModel,
                             AIModelService aiModelService) {
        this.envApiKey = key;
        this.envBaseUrl = baseUrl;
        this.configuredModel=configuredModel;
        this.aiModelService=aiModelService;
        this.model = buildModel(key, baseUrl);
    }

    private static DeepSeekChatModel buildModel(String key, String baseUrl) {
        return buildModel(key, baseUrl, null);
    }

    /** modelCode 仅用于决定是否附加网关路由头（如 Zen Go 的 x-opencode-session），官方直连无影响。 */
    private static DeepSeekChatModel buildModel(String key, String baseUrl, String modelCode) {
        var factory=new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10)); factory.setReadTimeout(Duration.ofSeconds(40));
        RestClient.Builder rest = RestClient.builder().requestFactory(factory).requestInterceptor(com.internship.util.QwenRequestSupport.interceptor())
                .defaultHeader("User-Agent", AiEndpointSupport.CLIENT_USER_AGENT);
        WebClient.Builder web=WebClient.builder().filter(com.internship.util.QwenRequestSupport.filter()).clientConnector(new JdkClientHttpConnector(
                java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()))
                .defaultHeader("User-Agent",AiEndpointSupport.CLIENT_USER_AGENT);
        // 中转网关（如 Zen Go）要求 x-opencode-session 路由头，官方直连时为空 Map，无影响。
        for (Map.Entry<String, String> gatewayHeader
                : AiEndpointSupport.extraHeaders(AiEndpointSupport.normalizeQuiet(baseUrl), modelCode).entrySet()) {
            rest = rest.defaultHeader(gatewayHeader.getKey(), gatewayHeader.getValue());
            web = web.defaultHeader(gatewayHeader.getKey(),gatewayHeader.getValue());
        }
        var api=DeepSeekApi.builder().apiKey(key).baseUrl(baseUrl)
                .restClientBuilder(rest).webClientBuilder(web).build();
        return DeepSeekChatModel.builder().deepSeekApi(api)
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).build()).build();
    }

    @Override public ChatResponse call(List<Message> messages,List<AgentReadTools.Spec> specs) {
        var settings=resolve();
        return selectModel(settings).call(prompt(messages,specs,settings));
    }

    @Override public Flux<ChatResponse> stream(List<Message> messages,List<AgentReadTools.Spec> specs){
        return Flux.defer(()->{
            var settings=resolve();
            return selectModel(settings).stream(prompt(messages,specs,settings)).timeout(Duration.ofSeconds(40));
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

    /**
     * 默认行配了独立接入点时用专属客户端（同等超时/不重试），否则沿用环境配置客户端。
     * 回归测试用空配置构造，必然走环境分支，行为与此前完全一致。
     */
    private DeepSeekChatModel selectModel(AgentModelSettings.Resolved settings) {
        String endpoint = AiEndpointSupport.isBlank(settings.endpoint()) ? envBaseUrl : settings.endpoint();
        String key = AiEndpointSupport.isBlank(settings.apiKey()) ? envApiKey : settings.apiKey();
        boolean dedicated = !AiEndpointSupport.isBlank(settings.endpoint()) || !AiEndpointSupport.isBlank(settings.apiKey());
        if (!dedicated) {
            return model;
        }
        String fingerprint = endpoint + "\n" + key + "\n" + settings.modelCode();
        if (dedicatedModels.size() > 8) {
            dedicatedModels.clear();
        }
        return dedicatedModels.computeIfAbsent(fingerprint, k -> {
            log.info("Agent 模型切换到独立接入点，地址: {}", endpoint);
            return buildModel(key, endpoint, settings.modelCode());
        });
    }

    /** 每次调用重新解析，后台修改默认模型后无需重启；仅在参数变化时打印一行日志，便于核对来源。 */
    AgentModelSettings.Resolved resolve(){
        AIModel row=aiModelService.findDefaultModel();
        if(row==null || !Integer.valueOf(1).equals(row.getStatus()) || row.getModelCode()==null || row.getModelCode().isBlank())
            throw new AgentException("MODEL_UNAVAILABLE","请管理员在 AI 大模型选择页面启用并设置默认模型。",503);
        AgentModelSettings.Resolved next=AgentModelSettings.resolve(row,configuredModel);
        var previous=current;
        if(previous==null||!previous.equals(next)){
            current=next;
            log.info("Agent 模型参数：model={} temperature={} maxTokens={} 来源={}",
                    next.modelCode(),next.temperature(),next.maxTokens(),next.source());
        }
        return next;
    }
}
