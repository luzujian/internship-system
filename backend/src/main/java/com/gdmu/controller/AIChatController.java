package com.gdmu.controller;


import com.gdmu.config.DynamicChatClientFactory;
import com.gdmu.service.JobRecommendationAgentService;
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
    private final JobRecommendationAgentService jobRecommendationAgentService;

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private static final Logger log = LoggerFactory.getLogger(AIChatController.class);


    @Autowired
    public AIChatController(DynamicChatClientFactory chatClientFactory, JobRecommendationAgentService jobRecommendationAgentService) {
        this.chatClientFactory = chatClientFactory;
        this.jobRecommendationAgentService = jobRecommendationAgentService;
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
        String modelCode;
        if (model != null && model.equals("deepseek-reasoner")) {
            modelCode = "deepseek-reasoner";
        } else if (model != null && model.equals("deepseek-v4-flash")) {
            modelCode = "deepseek-v4-flash";
        } else if (model != null && model.equals("deepseek-v4-pro")) {
            modelCode = "deepseek-v4-pro";
        } else {
            modelCode = "deepseek-chat";
        }
        String systemPrompt = getSystemPromptByRole(role);
        return chatClientFactory.createChatClient(modelCode, systemPrompt);
    }

    private boolean isJobRecommendationRequest(String message, String role) {
        if (!"student".equals(role)) {
            return false;
        }
        if (message == null || message.trim().isEmpty()) {
            return false;
        }
        String lowerMsg = message.toLowerCase();
        return lowerMsg.contains("推荐") && (lowerMsg.contains("岗位") || lowerMsg.contains("职位") || lowerMsg.contains("实习"));
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, Object> request) {
        try {
            String userMessage = (String) request.get("message");
            List<Map<String, String>> context = (List<Map<String, String>>) request.get("context");
            String model = (String) request.get("model");
            String role = (String) request.get("role");

            if (isJobRecommendationRequest(userMessage, role)) {
                Map<String, Object> result = jobRecommendationAgentService.processJobRecommendation(
                    userMessage,
                    (String) request.get("username"),
                    model
                );
                return ResponseEntity.ok(result);
            }

            List<Message> messages = buildMessages(userMessage, context);
            ChatClient selectedClient = getChatClientByModel(model, role);

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
            String role = (String) request.get("role");

            if (isJobRecommendationRequest(userMessage, role)) {
                new Thread(() -> {
                    try {
                        Map<String, Object> result = jobRecommendationAgentService.processJobRecommendation(
                            userMessage,
                            (String) request.get("username"),
                            (String) request.get("model")
                        );

                        Map<String, Object> data = new HashMap<>();
                        data.put("type", "job_recommendation");
                        data.put("content", result.get("message"));
                        data.put("data", result);

                        emitter.send(SseEmitter.event()
                                .data(data)
                                .name("job_recommendation"));

                        Map<String, Object> endData = new HashMap<>();
                        endData.put("type", "end");
                        emitter.send(SseEmitter.event()
                                .data(endData)
                                .name("end"));
                    } catch (Exception e) {
                        log.error("岗位推荐失败", e);
                        try {
                            Map<String, Object> errorData = new HashMap<>();
                            errorData.put("type", "error");
                            errorData.put("message", "推荐服务暂时不可用");
                            emitter.send(SseEmitter.event()
                                    .data(errorData)
                                    .name("error"));
                        } catch (IOException ex) {
                            // ignore
                        }
                    } finally {
                        emitter.complete();
                    }
                }).start();
                return emitter;
            }

            List<Map<String, String>> context = (List<Map<String, String>>) request.get("context");
            List<Message> messages = buildMessages(userMessage, context);
            ChatClient selectedClient = getChatClientByModel((String) request.get("model"), role);

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
                            // ignore
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
                            // ignore
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
                // ignore
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
