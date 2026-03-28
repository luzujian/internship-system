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


    @Autowired
    public AIChatController(DynamicChatClientFactory chatClientFactory) {
        this.chatClientFactory = chatClientFactory;
    }

    private String getSystemPromptByRole(String role) {
        if (role == null) role = "admin";
        switch (role) {
            case "student":
                return "您好！我是 DeepIntern 学生端 AI 助手，随时为您服务。";
            case "teacher":
                return "您好！我是 DeepIntern 教师端 AI 助手，随时为您服务。";
            case "company":
                return "您好！我是 DeepIntern 企业端 AI 助手，随时为您服务。";
            default:
                return "您好！我是 DeepIntern 管理端 AI 助手，随时为您服务。";
        }
    }

    private ChatClient getChatClientByModel(String model, String role) {
        String modelCode = (model != null && model.equals("deepseek-reasoner")) ? "deepseek-reasoner" : "deepseek-chat";
        String systemPrompt = getSystemPromptByRole(role);
        return chatClientFactory.createChatClient(modelCode, systemPrompt);
    }

    // 普通聊天接口
    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, Object> request) {
        try {
            String userMessage = (String) request.get("message");
            List<Map<String, String>> context = (List<Map<String, String>>) request.get("context");
            String model = (String) request.get("model");
            String role = (String) request.get("role");

            List<Message> messages = buildMessages(userMessage, context);
            ChatClient selectedClient = getChatClientByModel(model, role);

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
            String role = (String) request.get("role");
            List<Message> messages = buildMessages(userMessage, context);
            ChatClient selectedClient = getChatClientByModel((String) request.get("model"), role);

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