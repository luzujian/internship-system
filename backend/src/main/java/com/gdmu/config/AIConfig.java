package com.gdmu.config;

import com.gdmu.entity.AIModel;
import com.gdmu.service.impl.AIConfigRefreshServiceImpl;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
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
    public DeepSeekApi deepSeekApi() {
        return DeepSeekApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();
    }

    @Bean
    public DynamicChatClientFactory dynamicChatClientFactory(DeepSeekApi deepSeekApi) {
        return new DynamicChatClientFactory(deepSeekApi, aiConfigRefreshService);
    }
}
