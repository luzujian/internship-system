package com.internship.config;

import com.internship.service.impl.AIConfigRefreshServiceImpl;
import com.internship.util.AiEndpointSupport;
import com.internship.util.AiCallGuard;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AIConfig {

    private static final Logger log = LoggerFactory.getLogger(AIConfig.class);

    @Value("${spring.ai.deepseek.api-key}")
    private String apiKey;

    @Value("${spring.ai.deepseek.base-url}")
    private String baseUrl;

    @Autowired
    private AIConfigRefreshServiceImpl aiConfigRefreshService;

    @Bean
    @Primary
    public DeepSeekApi deepSeekApi(AiRuntimeProperties limits) {
        var requests = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(limits.getConnectTimeoutMillis());
        requests.setReadTimeout(limits.getReadTimeoutMillis());
        return DeepSeekApi.builder()
                .baseUrl(AiEndpointSupport.normalizeEndpoint(baseUrl))
                .completionsPath("/chat/completions")
                .restClientBuilder(org.springframework.web.client.RestClient.builder().requestFactory(requests).requestInterceptor(com.internship.util.QwenRequestSupport.interceptor()))
                .webClientBuilder(org.springframework.web.reactive.function.client.WebClient.builder().filter(com.internship.util.QwenRequestSupport.filter()))
                .apiKey(apiKey)
                .build();
    }

    @Bean
    public DynamicChatClientFactory dynamicChatClientFactory(DeepSeekApi deepSeekApi, AiCallGuard calls, AiRuntimeProperties limits) {
        // 全局地址/Key 显式传入：模型行未配独立接入点时回退到这里。
        return new DynamicChatClientFactory(deepSeekApi, aiConfigRefreshService, baseUrl, apiKey, calls, limits);
    }
}
