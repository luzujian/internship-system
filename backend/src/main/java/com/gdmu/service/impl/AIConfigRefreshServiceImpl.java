package com.gdmu.service.impl;

import com.gdmu.entity.AIModel;
import com.gdmu.mapper.AIModelMapper;
import com.gdmu.service.AIConfigRefreshService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class AIConfigRefreshServiceImpl implements AIConfigRefreshService {

    @Autowired
    private AIModelMapper aiModelMapper;

    @Autowired
    private ApplicationContext applicationContext;

    private final Map<String, AIModel> modelConfigCache = new ConcurrentHashMap<>();

    @Override
    public void refreshAIConfig() {
        log.info("开始刷新AI模型配置");
        try {
            List<AIModel> enabledModels = aiModelMapper.findEnabledModels();
            
            modelConfigCache.clear();
            
            for (AIModel model : enabledModels) {
                modelConfigCache.put(model.getModelCode().toLowerCase(), model);
                log.info("加载AI模型配置: {} (温度: {}, 最大Token: {})", 
                    model.getModelCode(), model.getTemperature(), model.getMaxTokens());
            }
            
            log.info("AI模型配置刷新完成，共加载 {} 个模型", modelConfigCache.size());
        } catch (Exception e) {
            log.error("刷新AI模型配置失败", e);
            throw new RuntimeException("刷新AI模型配置失败: " + e.getMessage(), e);
        }
    }

    public AIModel getModelConfig(String modelCode) {
        if (modelConfigCache.isEmpty()) {
            refreshAIConfig();
        }
        return modelConfigCache.get(modelCode.toLowerCase());
    }

    public Map<String, AIModel> getAllModelConfigs() {
        if (modelConfigCache.isEmpty()) {
            refreshAIConfig();
        }
        return new ConcurrentHashMap<>(modelConfigCache);
    }
}
