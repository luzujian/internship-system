package com.internship.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiEndpointSupportTest {

    @Test
    void blankInputsReturnNull() {
        assertNull(AiEndpointSupport.normalizeEndpoint(null));
        assertNull(AiEndpointSupport.normalizeEndpoint("   "));
        assertNull(AiEndpointSupport.normalizeQuiet(null));
        assertTrue(AiEndpointSupport.isBlank("  "));
    }

    @Test
    void trailingSlashIsTrimmedButV1IsKept() {
        assertEquals("https://api.deepseek.com",
                AiEndpointSupport.normalizeEndpoint("https://api.deepseek.com/"));
        // OpenAI 规范中转地址的 /v1 必须保留，网关靠它路由。
        assertEquals("https://opencode.ai/zen/go/v1",
                AiEndpointSupport.normalizeEndpoint("https://opencode.ai/zen/go/v1/"));
        assertEquals("https://opencode.ai/zen/go/v1",
                AiEndpointSupport.normalizeEndpoint("  https://opencode.ai/zen/go/v1  "));
    }

    @Test
    void fullChatCompletionsUrlIsReducedToBase() {
        assertEquals("https://opencode.ai/zen/go/v1",
                AiEndpointSupport.normalizeEndpoint("https://opencode.ai/zen/go/v1/chat/completions"));
    }

    @Test
    void nonHttpSchemeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> AiEndpointSupport.normalizeEndpoint("api.deepseek.com"));
        assertThrows(IllegalArgumentException.class,
                () -> AiEndpointSupport.normalizeEndpoint("ftp://x.example"));
        assertNull(AiEndpointSupport.normalizeQuiet("api.deepseek.com"));
    }

    @Test
    void maskKeepsHeadAndTail() {
        assertEquals("", AiEndpointSupport.maskApiKey(null));
        assertEquals("", AiEndpointSupport.maskApiKey("  "));
        assertEquals("****", AiEndpointSupport.maskApiKey("short"));
        assertEquals("sk-****54f3", AiEndpointSupport.maskApiKey("sk-abc12354f3"));
    }

    @Test
    void normalizationDifferenceIsDetectable() {
        assertTrue(AiEndpointSupport.endpointWasNormalized(
                "https://api.deepseek.com/", "https://api.deepseek.com"));
        assertFalse(AiEndpointSupport.endpointWasNormalized(
                "https://api.deepseek.com", "https://api.deepseek.com"));
    }

    @Test
    void gatewayGetsSessionHeaderButOfficialEndpointDoesNot() {
        var gateway = AiEndpointSupport.extraHeaders(
                "https://opencode.ai/zen/go/v1", "deepseek-v4.1-flash");
        assertEquals("deepintern-deepseek-v4.1-flash",
                gateway.get(AiEndpointSupport.OPENCODE_SESSION_HEADER));
        assertTrue(AiEndpointSupport.extraHeaders(
                "https://api.deepseek.com", "deepseek-chat").isEmpty());
        assertTrue(AiEndpointSupport.extraHeaders(null, "deepseek-chat").isEmpty());
    }

    @Test
    void sessionIdIsSanitized() {
        assertEquals("deepintern", AiEndpointSupport.sessionIdFor(null));
        assertEquals("deepintern", AiEndpointSupport.sessionIdFor("  "));
        assertEquals("deepintern-a-b", AiEndpointSupport.sessionIdFor("A/B"));
        assertEquals("deepintern-deepseek-v4-pro",
                AiEndpointSupport.sessionIdFor("DeepSeek-V4-Pro"));
    }
}
