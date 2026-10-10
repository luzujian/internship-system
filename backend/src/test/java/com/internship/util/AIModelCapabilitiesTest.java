package com.internship.util;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AIModelCapabilitiesTest {
    @Test void purposesMatchImplementedAdapters() {
        assertEquals(List.of("chat","voice"),AIModelCapabilities.supported("qwen3.8-omni-flash"));
        assertEquals(List.of("voice"),AIModelCapabilities.supported("qwen3-asr-flash-realtime"));
        assertEquals(List.of("chat"),AIModelCapabilities.supported("deepseek-chat"));
        assertEquals(List.of("chat"),AIModelCapabilities.supported("qwen3.5-plus"));
        assertTrue(AIModelCapabilities.supported("text-embedding-v3").isEmpty());
        assertEquals(List.of("chat"),AIModelCapabilities.supported("unknown-model"));
        assertTrue(AIModelCapabilities.supported("qwen3.5-omni-flash-realtime").isEmpty());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"kimi-k2.5","MiniMax-M2.5","ERNIE-4.5-turbo-128k","doubao-seed-1-6", "hunyuan-turbos-latest","4.0Ultra","ep-20261002-AbC123","Qwen/Qwen3-32B","internship-assistant","Qwen/Qwen2.5-Omni-7B","gateway-omni-chat"})
    void acceptsProviderModelsAndCaseSensitiveGatewayAliases(String code) {
        assertEquals(List.of("chat"),AIModelCapabilities.supported(code));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"text-EMBEDDING-v3","BAAI/bge-m3","Qwen3-ASR-1.7B","tts-1","whisper-1","bge-reranker-v2","qwen-omni-realtime", "", " "})
    void nonChatModelsStillCannotBecomeChatDefaults(String code) {
        assertTrue(AIModelCapabilities.supported(code).isEmpty());
    }
}
