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

    @Test
    void preservesDomesticProviderAndGatewayPaths() {
        for (String base : new String[] {
                "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "https://open.bigmodel.cn/api/paas/v4",
                "https://ark.cn-beijing.volces.com/api/v3",
                "https://qianfan.baidubce.com/v2",
                "http://127.0.0.1:4000/team/china/v1"}) {
            assertEquals(base, AiEndpointSupport.normalizeEndpoint(base + "/chat/completions/"));
        }
    }

    @Test
    void rejectsMalformedOrCredentialBearingAddresses() {
        for (String invalid : new String[] {"https://", "http:///v1", "https://api.example:99999/v1",
                "https://key:secret@api.example/v1", "https://api.example/v1?api_key=secret",
                "https://api.example/v1#chat", "https://api.example/v1/responses",
                "https://api.example/v1/messages", "https://api.example/a b"}) {
            assertThrows(IllegalArgumentException.class, () -> AiEndpointSupport.normalizeEndpoint(invalid), invalid);
        }
    }

    @Test
    void routeHeaderRequiresExactHostAndZenPath() {
        for (String other : new String[] {"https://other.example/opencode.ai/zen/v1",
                "https://opencode.ai.evil.example/zen/v1", "https://opencode.ai/zenn/v1"}) {
            assertTrue(AiEndpointSupport.extraHeaders(other, "model").isEmpty());
        }
    }

    @Test
    void gatewayNeverInheritsUnrelatedGlobalKey() {
        assertThrows(IllegalArgumentException.class, () -> AiEndpointSupport.resolveConnection(
                "https://gateway.example/v1", "", "https://api.deepseek.com", "global-key"));
        var own = AiEndpointSupport.resolveConnection("https://gateway.example/v1/", "own-key",
                "https://api.deepseek.com", "global-key");
        assertEquals("https://gateway.example/v1", own.endpoint());
        assertEquals("own-key", own.apiKey());
        assertFalse(own.toString().contains("own-key"));
    }

    @Test
    void globalPairAndKeyOnlyOverrideAreResolvedTogether() {
        var inherited = AiEndpointSupport.resolveConnection(null, null, "https://api.deepseek.com/", "global-key");
        assertEquals("https://api.deepseek.com", inherited.endpoint());
        assertEquals("global-key", inherited.apiKey());
        var keyOnly = AiEndpointSupport.resolveConnection(null, "own-key", "https://api.deepseek.com", "global-key");
        assertEquals("own-key", keyOnly.apiKey());
        assertThrows(IllegalArgumentException.class, () -> AiEndpointSupport.resolveConnection(null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> AiEndpointSupport.resolveConnection(
                "https://gateway.example/v1", "bad\r\nkey", null, null));
    }
}
