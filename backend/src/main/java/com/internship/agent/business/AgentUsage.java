package com.internship.agent.business;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.LinkedHashMap;
import java.util.Map;

/** 单次或累计模型用量；供应商未返回 Token 时 usageAvailable=false。 */
public record AgentUsage(long promptTokens, long completionTokens, long totalTokens,
                         long durationMs, boolean usageAvailable) {

    public static AgentUsage empty() {
        return new AgentUsage(0, 0, 0, 0, false);
    }

    public static AgentUsage from(ChatResponse response, long durationMs) {
        if (response == null || response.getMetadata() == null) return new AgentUsage(0, 0, 0, durationMs, false);
        Usage usage = response.getMetadata().getUsage();
        if (usage == null) return new AgentUsage(0, 0, 0, durationMs, false);
        long prompt = number(usage.getPromptTokens());
        long completion = number(usage.getCompletionTokens());
        long total = number(usage.getTotalTokens());
        if (total <= 0) total = prompt + completion;
        return new AgentUsage(prompt, completion, total, Math.max(durationMs, 0), prompt > 0 || completion > 0 || total > 0);
    }

    public AgentUsage merge(AgentUsage other) {
        if (other == null) return this;
        return new AgentUsage(promptTokens + other.promptTokens,
                completionTokens + other.completionTokens,
                totalTokens + other.totalTokens,
                durationMs + other.durationMs,
                usageAvailable || other.usageAvailable);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("promptTokens", promptTokens);
        result.put("completionTokens", completionTokens);
        result.put("totalTokens", totalTokens);
        result.put("modelDurationMs", durationMs);
        result.put("usageAvailable", usageAvailable);
        return result;
    }

    private static long number(Integer value) {
        return value == null ? 0L : Math.max(value.longValue(), 0L);
    }
}
