package com.gdmu.service;

import com.gdmu.config.DynamicChatClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashMap;
import java.util.Map;

/**
 * 岗位描述生成AI服务
 * 根据岗位名称和基本信息，AI生成专业的岗位描述和任职要求
 */
@Service
public class JobDescriptionGeneratorService {
    private static final Logger log = LoggerFactory.getLogger(JobDescriptionGeneratorService.class);

    private final DynamicChatClientFactory chatClientFactory;

    private static final String SYSTEM_PROMPT = "你是实习岗位描述生成专家，负责为HR生成专业、吸引人的岗位描述和任职要求。";

    @Autowired
    public JobDescriptionGeneratorService(DynamicChatClientFactory chatClientFactory) {
        this.chatClientFactory = chatClientFactory;
    }

    /**
     * 生成岗位描述和任职要求
     * @param positionName 岗位名称
     * @param department 所属部门
     * @param positionType 岗位类型（实习/全职/兼职）
     * @param model 使用的模型
     * @return 包含description和requirements的Map
     */
    public Map<String, String> generateJobDescription(String positionName, String department, String positionType, String model) {
        log.info("开始生成岗位描述: positionName={}, department={}, positionType={}", positionName, department, positionType);

        Map<String, String> result = new HashMap<>();

        try {
            String userPrompt = buildUserPrompt(positionName, department, positionType);
            String systemPrompt = SYSTEM_PROMPT;

            ChatClient client = getChatClientByModel(model);

            List<Message> messages = List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(userPrompt)
            );

            String aiResponse = client.prompt()
                    .messages(messages)
                    .call()
                    .content();

            log.info("AI生成结果:\n{}", aiResponse);

            // 解析返回内容
            result = parseGeneratedContent(aiResponse);

            return result;

        } catch (Exception e) {
            log.error("生成岗位描述异常: {}", e.getMessage());
            result.put("description", "AI生成失败，请手动填写");
            result.put("requirements", "AI生成失败，请手动填写");
            return result;
        }
    }

    /**
     * 构建用户提示词
     */
    private String buildUserPrompt(String positionName, String department, String positionType) {
        String typeDesc = "实习";
        if ("全职".equals(positionType)) {
            typeDesc = "全职";
        } else if ("兼职".equals(positionType)) {
            typeDesc = "兼职";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("请为以下岗位生成专业的岗位描述和任职要求：\n\n");
        sb.append("岗位名称：").append(positionName != null ? positionName : "").append("\n");
        sb.append("所属部门：").append(department != null ? department : "").append("\n");
        sb.append("岗位类型：").append(typeDesc).append("\n\n");
        sb.append("要求：\n");
        sb.append("1. 岗位描述要简洁明了，包含核心职责和工作内容，控制在100字以内\n");
        sb.append("2. 语言要专业、吸引人，符合招聘需求\n");
        sb.append("3. 只返回岗位描述内容，不要其他解释\n\n");
        sb.append("请使用以下格式返回：\n\n");
        sb.append("【岗位描述】\n（这里填写生成的岗位描述）");
        return sb.toString();
    }

    /**
     * 解析AI返回的内容
     */
    private Map<String, String> parseGeneratedContent(String aiResponse) {
        Map<String, String> result = new HashMap<>();
        result.put("description", "");

        if (aiResponse == null || aiResponse.trim().isEmpty()) {
            return result;
        }

        try {
            // 提取岗位描述
            Pattern descPattern = Pattern.compile("【岗位描述】\\s*\\n?([\\s\\S]*?)$", Pattern.CASE_INSENSITIVE);
            Matcher descMatcher = descPattern.matcher(aiResponse);
            if (descMatcher.find()) {
                result.put("description", descMatcher.group(1).trim());
            }

            // 如果解析失败，直接返回原始内容
            if (result.get("description").isEmpty()) {
                result.put("description", aiResponse.trim());
            }

        } catch (Exception e) {
            log.error("解析生成内容异常: {}", e.getMessage());
        }

        return result;
    }

    /**
     * 根据模型名称获取ChatClient
     */
    private ChatClient getChatClientByModel(String model) {
        String modelCode;
        if (model == null || model.trim().isEmpty()) {
            modelCode = "deepseek-chat";
        } else if ("deepseek-reasoner".equalsIgnoreCase(model)) {
            modelCode = "deepseek-reasoner";
        } else {
            modelCode = "deepseek-chat";
        }
        return chatClientFactory.createChatClient(modelCode, SYSTEM_PROMPT);
    }
}
