package com.internship.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.deepseek.DeepSeekAssistantMessage;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.reactive.ClientHttpRequestDecorator;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ClientRequest;
import reactor.core.publisher.Mono;

/** Provider-specific Chat Completions compatibility shared by probes and business calls. */
public final class QwenRequestSupport {
    private static final ObjectMapper JSON = new ObjectMapper();
    private QwenRequestSupport() {}

    public static byte[] normalize(byte[] body) throws IOException {
        return normalize(body, List.of());
    }

    private static byte[] normalize(byte[] body, List<Message> messages) throws IOException {
        return normalize(body, messages, false);
    }

    private static byte[] normalize(byte[] body, List<Message> messages, boolean answerReview) throws IOException {
        var value = JSON.readTree(body);
        if (value instanceof ObjectNode object) {
            String model = object.path("model").asText();
            if ("qwen3.8-omni-flash".equals(model) && !object.has("reasoning_effort") && !object.has("thinking_budget")) {
                object.put("reasoning_effort", "none");
            }
            String modelName = model.substring(model.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
            // Extractive JSON review needs no private reasoning; retain its independent budget and strict verdict.
            // Unknown providers retain their existing options instead of receiving unsupported DeepSeek fields.
            if (answerReview && modelName.matches("deepseek-(?:v4-)?(?:flash|pro)")) {
                object.putObject("thinking").put("type", "disabled");
                object.remove("reasoning_effort");
                object.putObject("response_format").put("type", "json_object");
                object.put("max_tokens", 4096);
            }
            // Fixed parameters reject the app's normal temperature. Use provider defaults.
            // https://platform.kimi.com/docs/api/models-overview
            if (modelName.matches("kimi-k(?:2\\.[567](?:[-.].*)?|2-thinking(?:-.*)?|3(?:[-.].*)?)")) {
                object.remove(List.of("temperature", "top_p", "presence_penalty", "frequency_penalty"));
            }
            var assistants = messages.stream().filter(message -> message.getMessageType() == MessageType.ASSISTANT).toList();
            int assistantIndex = 0;
            for (var message : object.path("messages")) {
                if ("assistant".equals(message.path("role").asText()) && assistantIndex < assistants.size()) {
                    String reasoning = reasoningContent(assistants.get(assistantIndex++));
                    if (reasoning != null && message instanceof ObjectNode assistant) assistant.put("reasoning_content", reasoning);
                }
            }
            return JSON.writeValueAsBytes(object);
        }
        return body;
    }
    public static ClientHttpRequestInterceptor interceptor() {
        return interceptor(List.of());
    }
    public static ClientHttpRequestInterceptor interceptor(List<Message> messages) {
        return (request, body, execution) -> {
            byte[] normalized = normalize(body, messages);
            request.getHeaders().setContentLength(normalized.length);
            return execution.execute(request, normalized);
        };
    }
    public static ExchangeFilterFunction filter() {
        return filter(List.of());
    }
    public static ExchangeFilterFunction filter(List<Message> messages) {
        return filter(messages, false);
    }
    public static ExchangeFilterFunction reviewFilter() {
        return filter(List.of(), true);
    }
    private static ExchangeFilterFunction filter(List<Message> messages, boolean answerReview) {
        return (request, next) -> next.exchange(ClientRequest.from(request).body((output, context) ->
            request.body().insert(new ClientHttpRequestDecorator(output) {
                @Override public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends org.springframework.core.io.buffer.DataBuffer> body) {
                    return DataBufferUtils.join(body).flatMap(buffer -> {
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes); DataBufferUtils.release(buffer);
                        try {
                            byte[] normalized = normalize(bytes, messages, answerReview);
                            getHeaders().setContentLength(normalized.length);
                            return super.writeWith(Mono.just(bufferFactory().wrap(normalized)));
                        } catch (IOException e) { return Mono.error(e); }
                    });
                }
            }, context)).build());
    }

    public static String reasoningContent(Message message) {
        if (message instanceof DeepSeekAssistantMessage assistant && assistant.getReasoningContent() != null) {
            return assistant.getReasoningContent();
        }
        Object reasoning = message.getMetadata().get("reasoning_content");
        return reasoning instanceof String content ? content : null;
    }
}
