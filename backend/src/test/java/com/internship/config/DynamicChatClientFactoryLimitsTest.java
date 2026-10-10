package com.internship.config;

import com.internship.entity.AIModel;
import com.internship.service.impl.AIConfigRefreshServiceImpl;
import com.internship.util.AiCallGuard;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.deepseek.api.DeepSeekApi;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Network failure tests use only loopback; no configured credentials or production service. */
class DynamicChatClientFactoryLimitsTest {
    private HttpServer server;
    private final ExecutorService handlers = Executors.newVirtualThreadPerTaskExecutor();
    private final CountDownLatch release = new CountDownLatch(1);
    private final AtomicInteger requests = new AtomicInteger();

    @AfterEach void close() {
        release.countDown();
        if (server != null) server.stop(0);
        handlers.shutdownNow();
    }

    private DynamicChatClientFactory factory(boolean dedicated, boolean silent) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(handlers);
        server.createContext("/", exchange -> {
            try {
                exchange.getRequestBody().readAllBytes(); requests.incrementAndGet();
                if (silent) release.await(5, TimeUnit.SECONDS);
                byte[] error = "{\"error\":{\"message\":\"synthetic busy\"}}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(503, error.length); exchange.getResponseBody().write(error);
            } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        String endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
        var row = new AIModel(); row.setModelCode("test-model");
        if (dedicated) { row.setApiEndpoint(endpoint); row.setApiKey("dedicated-test-key"); }
        var refresh = mock(AIConfigRefreshServiceImpl.class); when(refresh.getDefaultModelConfig()).thenReturn(row);
        var limits = new AiRuntimeProperties(); limits.setConnectTimeoutMillis(500); limits.setReadTimeoutMillis(200);
        return new DynamicChatClientFactory(DeepSeekApi.builder().baseUrl(endpoint).apiKey("test-key").build(),
                refresh, endpoint, "test-key", new AiCallGuard(limits), limits);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void normalGlobalAndDedicatedCallsDoNotRetryServerFailures(boolean dedicated) throws Exception {
        var client = factory(dedicated, false).createChatClient("ignored", "test");
        assertThrows(Exception.class, () -> client.prompt().user("test").call().content());
        assertEquals(1, requests.get());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void normalGlobalAndDedicatedCallsHaveABoundedReadTimeout(boolean dedicated) throws Exception {
        var client = factory(dedicated, true).createChatClient("ignored", "test");
        long started = System.nanoTime();
        assertThrows(Exception.class, () -> client.prompt().user("test").call().content());
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 3000);
        assertEquals(1, requests.get());
    }
}
