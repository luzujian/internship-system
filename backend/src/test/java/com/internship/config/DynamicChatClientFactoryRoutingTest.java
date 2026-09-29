package com.internship.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.entity.AIModel;
import com.internship.service.impl.AIConfigRefreshServiceImpl;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.deepseek.api.DeepSeekApi;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 接入点路由回归：配了独立地址/Key 的行走专属客户端，未配的行沿用全局客户端。
 * 全部走本地回环桩，不连外网。
 */
class DynamicChatClientFactoryRoutingTest {

    private HttpServer dedicatedServer;
    private HttpServer globalServer;
    private final AtomicInteger dedicatedHits = new AtomicInteger();
    private final AtomicInteger globalHits = new AtomicInteger();

    private AIConfigRefreshServiceImpl refreshService;
    private DynamicChatClientFactory factory;

    @BeforeEach
    void setUp() throws IOException {
        dedicatedServer = stub(dedicatedHits, "dedicated-reply");
        globalServer = stub(globalHits, "global-reply");

        refreshService = mock(AIConfigRefreshServiceImpl.class);
        AIModel dedicatedRow = row("dedicated-model",
                "http://127.0.0.1:" + dedicatedServer.getAddress().getPort(), "sk-dedicated");
        when(refreshService.getModelConfig("dedicated-model")).thenReturn(dedicatedRow);
        when(refreshService.getModelConfig("missing-model")).thenReturn(null);

        DeepSeekApi globalApi = DeepSeekApi.builder()
                .baseUrl("http://127.0.0.1:" + globalServer.getAddress().getPort())
                .apiKey("sk-global")
                .build();
        factory = new DynamicChatClientFactory(globalApi, refreshService,
                "http://127.0.0.1:" + globalServer.getAddress().getPort(), "sk-global");
    }

    @AfterEach
    void tearDown() {
        dedicatedServer.stop(0);
        globalServer.stop(0);
    }

    @Test
    void rowWithOwnEndpointUsesDedicatedClient() {
        String reply = factory.createChatClient("dedicated-model", "sys")
                .prompt().user("hi").call().content();

        assertEquals("dedicated-reply", reply);
        assertEquals(1, dedicatedHits.get());
        assertEquals(0, globalHits.get());
    }

    @Test
    void rowWithoutOwnEndpointFallsBackToGlobalClient() {
        String reply = factory.createChatClient("missing-model", "sys")
                .prompt().user("hi").call().content();

        assertEquals("global-reply", reply);
        assertEquals(0, dedicatedHits.get());
        assertEquals(1, globalHits.get());
    }

    @Test
    void dedicatedClientIsReusedAcrossCalls() {
        factory.createChatClient("dedicated-model", "sys").prompt().user("a").call().content();
        factory.createChatClient("dedicated-model", "sys").prompt().user("b").call().content();

        assertEquals(2, dedicatedHits.get());
    }

    @Test
    void migrationOverrideRoutesLegacySelectorsToConfiguredProvider() {
        when(refreshService.getDefaultModelConfig()).thenReturn(row("qwen3.8-omni-flash",
                "http://127.0.0.1:" + dedicatedServer.getAddress().getPort(), "sk-test"));
        assertEquals("qwen3.8-omni-flash", factory.resolveModelCode("deepseek-reasoner"));
        assertEquals("dedicated-reply", factory.createChatClient("deepseek-reasoner", "sys").prompt().user("hi").call().content());
        assertEquals(1, dedicatedHits.get()); assertEquals(0, globalHits.get());
    }

    @Test
    void administratorSwitchAndEndpointEditsApplyWithoutRecreatingFactory() {
        var first=row("first-model", "http://127.0.0.1:" + dedicatedServer.getAddress().getPort(), "first-key");
        var second=row("second-model", "http://127.0.0.1:" + globalServer.getAddress().getPort(), "second-key");
        when(refreshService.getDefaultModelConfig()).thenReturn(first);
        assertEquals("dedicated-reply",factory.createChatClient("legacy", "sys").prompt().user("a").call().content());
        when(refreshService.getDefaultModelConfig()).thenReturn(second);
        assertEquals("global-reply",factory.createChatClient("legacy", "sys").prompt().user("b").call().content());
        assertEquals("global-reply",factory.createPreviewChatClient("deepseek-chat", "sys").prompt().user("c").call().content());
        second.setApiEndpoint(first.getApiEndpoint());second.setApiKey("edited-key");
        assertEquals("dedicated-reply",factory.createChatClient("legacy", "sys").prompt().user("d").call().content());
        assertEquals(2,dedicatedHits.get()); assertEquals(2,globalHits.get());
    }

    private static AIModel row(String code, String endpoint, String key) {
        AIModel model = new AIModel();
        model.setModelCode(code);
        model.setApiEndpoint(endpoint);
        model.setApiKey(key);
        model.setTemperature(new BigDecimal("0.50"));
        model.setMaxTokens(512);
        return model;
    }

    private static HttpServer stub(AtomicInteger hits, String reply) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/chat/completions", exchange -> {
            try {
                hits.incrementAndGet();
                exchange.getRequestBody().readAllBytes();
                String body = new ObjectMapper().writeValueAsString(Map.of(
                        "id", "t", "object", "chat.completion", "created", 1,
                        "model", "stub", "choices", List.of(Map.of("index", 0,
                                "message", Map.of("role", "assistant", "content", reply),
                                "finish_reason", "stop")),
                        "usage", Map.of("prompt_tokens", 1, "completion_tokens", 1, "total_tokens", 2)));
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            } finally {
                exchange.close();
            }
        });
        server.start();
        return server;
    }
}
