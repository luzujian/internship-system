package com.internship.service.impl;

import com.internship.entity.AIModel;
import com.internship.exception.BusinessException;
import com.internship.mapper.AIModelMapper;
import com.internship.mapper.CounselorAISettingsMapper;
import com.internship.service.AIConfigRefreshService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 模型管理新能力回归：脱敏输出、复制配置、一键测连、空 Key 保持原值。
 * 全部走本地回环桩，不连外网、不碰业务数据。
 */
@ExtendWith(MockitoExtension.class)
class AIModelConnectionManagementTest {

    @Mock
    private AIModelMapper aiModelMapper;
    @Mock
    private AIConfigRefreshService aiConfigRefreshService;
    @Mock
    private CounselorAISettingsMapper counselorAISettingsMapper;

    private AIModelServiceImpl service;

    private HttpServer server;
    private volatile int stubStatus = 200;
    private volatile String stubBody;
    private volatile String seenAuth;

    @BeforeEach
    void setUp() {
        service = new AIModelServiceImpl(aiModelMapper);
        ReflectionTestUtils.setField(service, "aiConfigRefreshService", aiConfigRefreshService);
        ReflectionTestUtils.setField(service, "counselorAISettingsMapper", counselorAISettingsMapper);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void listOutputMasksApiKey() {
        AIModel row = stored(7L, "deepseek-chat");
        row.setApiKey("sk-live-key-1234567890");

        List<AIModel> masked = service.maskForOutput(List.of(row));

        assertEquals(Boolean.TRUE, masked.get(0).getHasApiKey());
        assertNotEquals("sk-live-key-1234567890", masked.get(0).getApiKey());
        assertTrue(masked.get(0).getApiKey().endsWith("7890"));
        assertNull(masked.get(0).getClearApiConfig());
    }

    @Test
    void missingKeyIsFlaggedAsUnconfigured() {
        AIModel row = stored(8L, "deepseek-chat");
        row.setApiKey(null);

        service.maskForOutput(row);

        assertEquals(Boolean.FALSE, row.getHasApiKey());
        assertEquals("", row.getApiKey());
    }

    @Test
    void duplicateCopiesConfigWithUniqueCode() {
        AIModel source = stored(3L, "deepseek-chat");
        source.setModelName("DeepSeek-V4.1-Flash");
        source.setApiKey("sk-source-key");
        when(aiModelMapper.findById(3L)).thenReturn(source);
        when(aiModelMapper.findByModelCode("deepseek-chat-copy")).thenReturn(null);

        AIModel copy = service.duplicateModel(3L, "admin");

        assertEquals("deepseek-chat-copy", copy.getModelCode());
        assertTrue(copy.getModelName().contains("副本"));
        assertEquals(Integer.valueOf(0), copy.getIsDefault());
        assertEquals("sk-source-key", copy.getApiKey());
        ArgumentCaptor<AIModel> captor = ArgumentCaptor.forClass(AIModel.class);
        verify(aiModelMapper).insert(captor.capture());
        assertEquals("deepseek-chat-copy", captor.getValue().getModelCode());
    }

    @Test
    void blankKeyOnUpdateKeepsStoredValue() {
        AIModel existing = stored(3L, "deepseek-chat");
        existing.setApiKey("sk-stored");
        when(aiModelMapper.findById(3L)).thenReturn(existing);
        when(aiModelMapper.update(any())).thenReturn(1);

        AIModel patch = new AIModel();
        patch.setId(3L);
        patch.setModelName("新展示名");
        patch.setApiKey("");
        patch.setApiEndpoint("  ");

        service.update(patch);

        ArgumentCaptor<AIModel> captor = ArgumentCaptor.forClass(AIModel.class);
        verify(aiModelMapper).update(captor.capture());
        assertNull(captor.getValue().getApiKey(), "空 Key 必须置 null 以便 Mapper 跳过该列");
        assertNull(captor.getValue().getApiEndpoint());
        assertEquals("新展示名", captor.getValue().getModelName());
    }

    @Test
    void renameSyncsCounselorModelCode() {
        AIModel existing = stored(3L, "deepseek-chat");
        when(aiModelMapper.findById(3L)).thenReturn(existing);
        when(aiModelMapper.findByModelCode("deepseek-reasoner")).thenReturn(null);
        when(aiModelMapper.update(any())).thenReturn(1);

        AIModel patch = new AIModel();
        patch.setId(3L);
        patch.setModelCode("deepseek-reasoner");

        service.update(patch);

        verify(counselorAISettingsMapper).updateModelCode("deepseek-chat", "deepseek-reasoner");
    }

    @Test
    void testConnectionSuccessReportsLatencyAndReply() throws Exception {
        stubBody = chatCompletion("hello-test");
        startStub();

        AIModel probe = new AIModel();
        probe.setApiEndpoint(stubBase());
        probe.setApiKey("sk-probe");
        probe.setModelCode("stub-model");

        Map<String, Object> result = service.testConnection(probe);

        assertEquals(true, result.get("ok"));
        assertEquals(200, result.get("httpStatus"));
        assertTrue((Long) result.get("latencyMs") >= 0);
        assertEquals("hello-test", result.get("reply"));
        assertEquals("Bearer sk-probe", seenAuth);
    }

    @Test
    void testConnectionSurfacesVendorRejection() throws Exception {
        stubStatus = 401;
        stubBody = "{\"error\":{\"message\":\"Authentication Fails, Your api key is invalid\"}}";
        startStub();

        AIModel probe = new AIModel();
        probe.setApiEndpoint(stubBase());
        probe.setApiKey("sk-bad");
        probe.setModelCode("stub-model");

        Map<String, Object> result = service.testConnection(probe);

        assertEquals(false, result.get("ok"));
        assertEquals(401, result.get("httpStatus"));
        assertTrue(String.valueOf(result.get("error")).contains("401"));
    }

    @Test
    void testConnectionUsesStoredKeyWhenProbeKeyBlank() throws Exception {
        stubBody = chatCompletion("stored-key-ok");
        startStub();

        AIModel existing = stored(3L, "stub-model");
        existing.setApiEndpoint(stubBase());
        existing.setApiKey("sk-stored");
        when(aiModelMapper.findById(3L)).thenReturn(existing);

        AIModel probe = new AIModel();
        probe.setId(3L);

        Map<String, Object> result = service.testConnection(probe);

        assertEquals(true, result.get("ok"));
        assertEquals("Bearer sk-stored", seenAuth);
    }

    @Test
    void testConnectionWithoutEndpointOrKeyFailsFast() {
        AIModel probe = new AIModel();
        probe.setModelCode("stub-model");
        assertThrows(BusinessException.class, () -> service.testConnection(probe));

        probe.setApiEndpoint("not-a-url");
        probe.setApiKey("sk-x");
        assertThrows(BusinessException.class, () -> service.testConnection(probe));
    }

    @Test
    void unreachableEndpointReportsNetworkError() {
        int closedPort;
        try (java.net.ServerSocket s = new java.net.ServerSocket(0)) {
            closedPort = s.getLocalPort();
        } catch (IOException e) {
            fail("无法分配测试端口");
            return;
        }

        AIModel probe = new AIModel();
        probe.setApiEndpoint("http://127.0.0.1:" + closedPort);
        probe.setApiKey("sk-x");
        probe.setModelCode("stub-model");

        Map<String, Object> result = service.testConnection(probe);

        assertEquals(false, result.get("ok"));
        assertTrue(String.valueOf(result.get("error")).contains("不可达或超时"));
    }

    @Test
    void deletePhysicallyRemovesRow() {
        AIModel row = stored(5L, "deepseek-flash-copy");
        row.setDeleted(0);
        when(aiModelMapper.findById(5L)).thenReturn(row);
        when(aiModelMapper.deleteById(5L, "admin")).thenReturn(1);

        assertEquals(1, service.deleteById(5L));
        verify(aiModelMapper).deleteById(5L, "admin");
    }

    @Test
    void deleteRefusesDefaultModel() {
        AIModel def = stored(3L, "deepseek-flash");
        def.setDeleted(0);
        def.setIsDefault(1);
        when(aiModelMapper.findById(3L)).thenReturn(def);

        assertThrows(BusinessException.class, () -> service.deleteById(3L));
        verify(aiModelMapper, never()).deleteById(any(), any());
    }


    private AIModel stored(Long id, String code) {
        AIModel model = new AIModel();
        model.setId(id);
        model.setModelName("DeepSeek");
        model.setModelCode(code);
        model.setProvider("deepseek");
        model.setMaxTokens(2200);
        model.setTemperature(new BigDecimal("0.20"));
        model.setStatus(1);
        model.setIsDefault(0);
        return model;
    }

    private void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/chat/completions", exchange -> {
            try {
                seenAuth = exchange.getRequestHeaders().getFirst("Authorization");
                exchange.getRequestBody().readAllBytes();
                byte[] body = stubBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(stubStatus, body.length);
                exchange.getResponseBody().write(body);
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    private String stubBase() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static String chatCompletion(String content) {
        return "{\"id\":\"t\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"stub\","
                + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\""
                + content + "\"},\"finish_reason\":\"stop\"}],"
                + "\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}";
    }
}
