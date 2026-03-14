package com.gdmu.config;

import com.gdmu.entity.AIModel;
import com.gdmu.service.impl.AIConfigRefreshServiceImpl;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DynamicChatClientFactory {

    private static final Logger log = LoggerFactory.getLogger(DynamicChatClientFactory.class);

    private final DeepSeekApi deepSeekApi;
    private final AIConfigRefreshServiceImpl aiConfigRefreshService;

    public DynamicChatClientFactory(DeepSeekApi deepSeekApi, AIConfigRefreshServiceImpl aiConfigRefreshService) {
        this.deepSeekApi = deepSeekApi;
        this.aiConfigRefreshService = aiConfigRefreshService;
    }

    public ChatClient createChatClient(String modelCode, String systemPrompt) {
        AIModel modelConfig = aiConfigRefreshService.getModelConfig(modelCode);
        if (modelConfig == null) {
            log.warn("未找到模型配置: {}，使用默认配置", modelCode);
            modelConfig = createDefaultConfig();
        }

        DeepSeekChatOptions options = DeepSeekChatOptions.builder()
                .model(modelCode)
                .temperature(modelConfig.getTemperature().doubleValue())
                .build();

        DeepSeekChatModel chatModel = DeepSeekChatModel.builder()
                .deepSeekApi(deepSeekApi)
                .defaultOptions(options)
                .build();

        return ChatClient.builder(chatModel)
                .defaultSystem(systemPrompt)
                .build();
    }

    private AIModel createDefaultConfig() {
        AIModel defaultConfig = new AIModel();
        defaultConfig.setTemperature(new java.math.BigDecimal("0.7"));
        defaultConfig.setMaxTokens(2000);
        return defaultConfig;
    }
}
