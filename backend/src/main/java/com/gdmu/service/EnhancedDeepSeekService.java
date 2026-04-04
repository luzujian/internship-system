package com.gdmu.service;

import com.gdmu.config.DynamicChatClientFactory;
import com.gdmu.entity.LearningResource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Service
public class EnhancedDeepSeekService {

    private static final Logger log = LoggerFactory.getLogger(EnhancedDeepSeekService.class);

    @Autowired
    @Lazy
    private ResourceService resourceService;

    @Autowired
    private DynamicChatClientFactory chatClientFactory;
    
    private static final String SYSTEM_PROMPT = "你是实习管理系统的资源助手，基于学习资源回答用户问题。";

    /**
     * 基于资源库的AI对话增强功能
     * @param question 用户提问
     * @param useReasoner 是否使用推理模型
     * @return AI回答结果
     */
    public String chatWithResources(String question, boolean useReasoner) {
        try {
            String modelCode = useReasoner ? "deepseek-reasoner" : "deepseek-chat";
            ChatClient chatClient = chatClientFactory.createChatClient(modelCode, SYSTEM_PROMPT);
            
            // 1. 搜索相关资源
            List<LearningResource> resources = resourceService.semanticSearch(question);
            log.info("搜索到相关资源数量: {}", resources.size());

            // 2. 构建资源上下文
            String context = buildResourceContext(resources);
            log.info("构建的资源上下文长度: {}", context.length());

            // 3. 调用DeepSeek API
            return chatWithContext(question, context, useReasoner);
        } catch (Exception e) {
            log.error("基于资源的AI对话失败", e);
            return "抱歉，资源搜索和AI对话处理过程中出现错误。";
        }
    }

    /**
     * 生成资源摘要
     * @param resource 学习资源
     * @return AI生成的资源摘要
     */
    public String generateResourceSummary(LearningResource resource) {
        try {
            ChatClient chatClient = chatClientFactory.createChatClient("deepseek-chat", SYSTEM_PROMPT);
            
            // 构建摘要生成提示
            String prompt = String.format("请为以下学习资源生成简洁的中文摘要（100-200字）：\n标题：%s\n描述：%s",
                    resource.getTitle(), resource.getDescription());

            // 使用deepseek-chat模型生成摘要
            return chatClient.prompt(prompt).call().content();
        } catch (Exception e) {
            log.error("生成资源摘要失败", e);
            return "摘要生成失败";
        }
    }

    /**
     * 生成文本嵌入向量
     * @param text 需要生成嵌入的文本
     * @return 嵌入向量（使用本地实现避免外部API超时）
     */
    public String generateEmbedding(String text) {
        try {
            // 提供一个简单的本地实现替代外部API调用
            // 避免依赖外部服务导致的超时问题
            if (text == null || text.trim().isEmpty()) {
                return "[]";
            }
            
            // 简单的哈希-based嵌入实现
            // 在生产环境中应替换为真正的嵌入模型
            StringBuilder embeddingBuilder = new StringBuilder("[");
            
            // 生成一个固定长度的向量
            int vectorLength = 384;
            long hash = 0;
            
            for (int i = 0; i < vectorLength; i++) {
                // 使用简单的哈希算法生成伪嵌入值
                for (char c : (text + i).toCharArray()) {
                    hash = 31 * hash + c;
                }
                
                // 将哈希值归一化到[-1, 1]范围
                double normalizedValue = ((hash & 0xFFFF) / (double)0xFFFF) * 2 - 1;
                
                // 添加到向量中，保留4位小数
                if (i > 0) {
                    embeddingBuilder.append(", ");
                }
                embeddingBuilder.append(String.format("%.4f", normalizedValue));
            }
            
            embeddingBuilder.append("]");
            String embedding = embeddingBuilder.toString();
            
            log.info("本地生成文本嵌入向量成功，文本长度: {}", text.length());
            return embedding;
        } catch (Exception e) {
            log.error("生成嵌入向量失败", e);
            // 返回一个默认向量而不是空字符串
            return "[0.0]";
        }
    }

    /**
     * 构建资源上下文
     * @param resources 学习资源列表
     * @return 格式化的上下文文本
     */
    private String buildResourceContext(List<LearningResource> resources) {
        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("相关学习资源信息：\n");
        
        // 限制上下文资源数量，避免token溢出
        int maxResources = 5;
        int resourceCount = Math.min(resources.size(), maxResources);
        
        for (int i = 0; i < resourceCount; i++) {
            LearningResource resource = resources.get(i);
            contextBuilder.append(String.format("资源%d：\n", i + 1));
            contextBuilder.append(String.format("标题：%s\n", resource.getTitle()));
            contextBuilder.append(String.format("类型：%s\n", resource.getFileType()));
            contextBuilder.append(String.format("描述：%s\n", resource.getDescription() != null ? resource.getDescription() : "无描述"));
            if (resource.getAiSummary() != null) {
                contextBuilder.append(String.format("摘要：%s\n", resource.getAiSummary()));
            }
            contextBuilder.append("\n");
        }
        
        return contextBuilder.toString();
    }

    /**
     * 基于上下文的AI对话
     * @param question 用户提问
     * @param context 上下文信息
     * @param useReasoner 是否使用推理模型
     * @return AI回答
     */
    private String chatWithContext(String question, String context, boolean useReasoner) {
        String modelCode = useReasoner ? "deepseek-reasoner" : "deepseek-chat";
        ChatClient client = chatClientFactory.createChatClient(modelCode, SYSTEM_PROMPT);
        
        // 构建系统提示
        String systemPrompt = "你是一个基于实习管理系统的智能助手。请利用提供的资源信息回答用户问题。" +
                "如果资源中有相关信息，请引用；如果没有，请根据你的知识回答，但要说明信息来源。" +
                "回答应当简洁、准确，适合实习管理环境使用。";
        
        // 构建用户消息
        String userMessage = String.format("问题：%s\n\n上下文：%s", question, context);
        
        // 调用AI模型
        return client.prompt().system(systemPrompt).user(userMessage).call()
                .content();
    }
    
    /**
     * 批量生成资源摘要
     * @param resources 资源列表
     * @return 摘要映射
     */
    public Map<Long, String> batchGenerateSummaries(List<LearningResource> resources) {
        Map<Long, String> summaryMap = new HashMap<>();
        
        for (LearningResource resource : resources) {
            try {
                String summary = generateResourceSummary(resource);
                summaryMap.put(resource.getId(), summary);
            } catch (Exception e) {
                log.error("生成资源摘要失败: {}", resource.getId(), e);
                summaryMap.put(resource.getId(), "摘要生成失败");
            }
        }
        
        return summaryMap;
    }
}