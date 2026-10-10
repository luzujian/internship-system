package com.internship.service;

import com.internship.config.DynamicChatClientFactory;
import com.internship.exception.BusinessException;
import com.internship.service.impl.AIConfigRefreshServiceImpl;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.deepseek.api.DeepSeekApi;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PreviewHttpClientTest {
    private HttpServer server;
    private ExecutorService executor;
    private DynamicChatClientFactory factory;
    private final AtomicInteger requests = new AtomicInteger();
    private volatile boolean slow;
    private volatile String response;

    @BeforeEach void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/chat/completions", exchange -> {
            int attempt = requests.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            try {
                if (slow) Thread.sleep(12000);
                int status = response != null ? 200 : attempt == 1 ? 503 : 400;
                byte[] body = (response != null ? response : "{\"error\":\"local stub failure\"}")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        DeepSeekApi api = DeepSeekApi.builder().baseUrl(baseUrl).apiKey("local-stub-only").build();
        factory = new DynamicChatClientFactory(api, mock(AIConfigRefreshServiceImpl.class));
        org.springframework.test.util.ReflectionTestUtils.setField(factory, "previewBaseUrl", baseUrl);
        org.springframework.test.util.ReflectionTestUtils.setField(factory, "previewApiKey", "local-stub-only");
    }

    @AfterEach void cleanup() {
        server.stop(0);
        executor.shutdownNow();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void previewHttpFailureDoesNotRetry(boolean teacher) {
        long started = System.nanoTime();
        BusinessException error = assertThrows(BusinessException.class, () -> parse(teacher));
        long elapsed = (System.nanoTime() - started) / 1_000_000;
        System.out.printf("PREVIEW_HTTP scenario=503 teacher=%s requests=%d elapsedMs=%d%n", teacher, requests.get(), elapsed);
        assertTrue(error.getMessage().contains("手动"));
        assertEquals(1, requests.get());
        assertTrue(elapsed < 5000, "elapsedMs=" + elapsed);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void previewReadTimeoutIsBoundedWithoutRetry(boolean teacher) {
        slow = true;
        response = "{}";
        long started = System.nanoTime();
        assertThrows(BusinessException.class, () -> parse(teacher));
        long elapsed = (System.nanoTime() - started) / 1_000_000;
        System.out.printf("PREVIEW_HTTP scenario=read-timeout teacher=%s requests=%d elapsedMs=%d%n", teacher, requests.get(), elapsed);
        assertEquals(1, requests.get());
        assertTrue(elapsed >= 9000 && elapsed < 11500, "elapsedMs=" + elapsed);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void validHttpResponseProducesDraftAndUsage(boolean teacher) throws Exception {
        String content = teacher
                ? "{\"name\":\"鉴定提醒\",\"triggerCondition\":\"APPRAISAL_MISSING\",\"action\":\"STATION_REMINDER\",\"schedule\":{\"mode\":\"WINDOW\"},\"questions\":[]}"
                : "{\"roleDefaults\":{\"teacher\":[\"auto_task\"]},\"questions\":[]}";
        response = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of(
                "id", "local-response", "object", "chat.completion", "created", 1,
                "model", "deepseek-chat", "choices", java.util.List.of(Map.of("index", 0,
                        "message", Map.of("role", "assistant", "content", content), "finish_reason", "stop")),
                "usage", Map.of("prompt_tokens", 12, "completion_tokens", 8, "total_tokens", 20)));
        Map<String, Object> result = parse(teacher);
        assertEquals(1, requests.get());
        assertNotNull(result.get("usage"));
        assertTrue(result.containsKey(teacher ? "triggerCondition" : "proposedRoleDefaults"));
    }

    @org.junit.jupiter.api.Test
    void normalClientAlsoKeepsOneAttemptForRuntimeSafety() {
        factory.createPreviewChatClient("deepseek-chat", "local");
        long started = System.nanoTime();
        assertThrows(Exception.class, () -> factory.createChatClient("deepseek-chat", "local")
                .prompt().user("local").call().chatResponse());
        System.out.printf("PREVIEW_HTTP scenario=normal-client requests=%d elapsedMs=%d%n",
                requests.get(), (System.nanoTime() - started) / 1_000_000);
        assertEquals(1, requests.get());
    }

    private Map<String, Object> parse(boolean teacher) {
        return teacher ? new TeacherAutoTaskParseService(factory).parse("截止前两天提醒未交鉴定的学生")
                : new AgentCapabilityParseService(factory).parse("为教师开启自动化任务", Map.of());
    }
}
