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
        assertTrue(AIModelCapabilities.supported("unknown-model").isEmpty());
        assertTrue(AIModelCapabilities.supported("qwen3.5-omni-flash-realtime").isEmpty());
    }
}
