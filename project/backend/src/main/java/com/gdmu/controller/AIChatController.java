package com.gdmu.controller;


import com.gdmu.config.DynamicChatClientFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/ai")
public class AIChatController {

    private final DynamicChatClientFactory chatClientFactory;

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private static final Logger log = LoggerFactory.getLogger(AIChatController.class);

    private static final String SYSTEM_PROMPT = "你是实习管理系统的管理员端AI助手，专门为系统管理员提供智能辅助服务。\n" +
            "你的主要职责包括：\n" +
            "1. 解答管理员关于系统功能、用户管理和权限设置的问题\n" +
            "2. 协助处理系统数据统计、分析和报表生成\n" +
            "3. 提供系统维护、故障排查和性能优化建议\n" +
            "4. 协助管理实习相关业务，包括学生实习、企业招聘、实习审核等\n" +
            "5. 帮助分析学生实习数据、企业信息和招聘趋势\n" +
            "6. 提供系统安全性和数据保护建议\n" +
            "7. 指导管理员进行AI模型测试，包括测试步骤、方法和评估标准\n\n\n" +
            "关于AI模型测试的指导：\n" +
            "- 测试模型响应速度：发送简单问题（如1+1=？），观察AI的响应时间\n" +
            "- 测试模型稳定性：连续发送多个问题，检查AI是否能够稳定响应\n" +
            "- 测试模型切换：在系统中切换不同的AI模型，验证切换是否生效\n" +
            "- 测试模型准确性：发送专业问题，检查AI的回答是否准确、专业\n" +
            "- 测试模型理解能力：发送复杂问题，检查AI是否能够理解并给出合理答案\n" +
            "- 评估测试结果：根据响应时间、准确性、稳定性等指标评估模型性能\n\n" +
            "请保持专业、准确、可靠的回答风格，对于不确定的问题要明确说明。\n" +
            "特别注意：所有回复内容必须去除Markdown格式，使用纯文本格式，确保在网页中正常显示。";

    @Autowired
    public AIChatController(DynamicChatClientFactory chatClientFactory) {
        this.chatClientFactory = chatClientFactory;
    }

    private ChatClient getChatClientByModel(String model) {
        String modelCode = (model != null && model.equals("deepseek-reasoner")) ? "deepseek-reasoner" : "deepseek-chat";
        return chatClientFactory.createChatClient(modelCode, SYSTEM_PROMPT);
    }

    // 普通聊天接口
    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, Object> request) {
        try {
            String userMessage = (String) request.get("message");
            List<Map<String, String>> context = (List<Map<String, String>>) request.get("context");
            String model = (String) request.get("model");

            List<Message> messages = buildMessages(userMessage, context);
            ChatClient selectedClient = getChatClientByModel(model);

            // Spring AI 1.0.0 调整了响应获取方式
            String aiResponse = selectedClient.prompt()
                    .messages(messages)
                    .call()
                    .content();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", aiResponse);
            response.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "AI服务暂时不可用");
            errorResponse.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    // 流式聊天接口 (Spring AI 1.0.0 流式API变更)
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody Map<String, Object> request) {
        SseEmitter emitter = new SseEmitter(120_000L);

        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            emitters.remove(emitter);
        });

        try {
            String userMessage = (String) request.get("message");
            List<Map<String, String>> context = (List<Map<String, String>>) request.get("context");
            List<Message> messages = buildMessages(userMessage, context);
            ChatClient selectedClient = getChatClientByModel((String) request.get("model"));

            // Spring AI 1.0.0 流式处理方式变更
            Flux<String> aiResponseStream = selectedClient.prompt()
                    .messages(messages)
                    .stream()
                    .content();

            aiResponseStream.subscribe(
                    chunk -> {
                        try {
                            Map<String, Object> data = new HashMap<>();
                            data.put("content", chunk);
                            data.put("type", "chunk");
                            emitter.send(SseEmitter.event()
                                    .data(data)
                                    .name("message"));
                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    },
                    error -> {
                        try {
                            Map<String, Object> errorData = new HashMap<>();
                            errorData.put("type", "error");
                            errorData.put("message", "生成过程中发生错误: " + error.getMessage());
                            emitter.send(SseEmitter.event()
                                    .data(errorData)
                                    .name("error"));
                        } catch (IOException ex) {
                            // 忽略发送错误
                        } finally {
                            emitter.complete();
                        }
                    },
                    () -> {
                        try {
                            Map<String, Object> endData = new HashMap<>();
                            endData.put("type", "end");
                            endData.put("message", "生成完成");
                            emitter.send(SseEmitter.event()
                                    .data(endData)
                                    .name("end"));
                        } catch (IOException e) {
                            // 忽略发送结束信息错误
                        } finally {
                            emitter.complete();
                        }
                    }
            );

        } catch (Exception e) {
            try {
                Map<String, Object> errorData = new HashMap<>();
                errorData.put("type", "error");
                errorData.put("message", "服务暂时不可用: " + e.getMessage());
                emitter.send(SseEmitter.event()
                        .data(errorData)
                        .name("error"));
            } catch (IOException ex) {
                // 忽略发送错误
            } finally {
                emitter.complete();
            }
        }

        return emitter;
    }

    private List<Message> buildMessages(String userMessage, List<Map<String, String>> context) {
        List<Message> messages = new ArrayList<>();

        if (context != null && !context.isEmpty()) {
            for (Map<String, String> msg : context) {
                String role = msg.get("role");
                String content = msg.get("content");
                if ("user".equals(role)) {
                    messages.add(new UserMessage(content));
                } else if ("assistant".equals(role)) {
                    messages.add(new AssistantMessage(content));
                }
            }
        }

        messages.add(new UserMessage(userMessage));
        return messages;
    }
    

}