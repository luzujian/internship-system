package com.internship.service.impl;

import com.internship.agent.business.AgentVoiceSettings;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
    private volatile String seenRequestBody;
    private volatile String seenPath;
    private volatile String seenMethod;

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
    void duplicateReturnsUnsavedTemplateWithoutInventingAProviderModelId() {
        AIModel source = stored(3L, "deepseek-chat");
        source.setModelName("DeepSeek-V4.1-Flash");
        source.setApiKey("sk-source-key");
        when(aiModelMapper.findById(3L)).thenReturn(source);

        AIModel copy = service.duplicateModel(3L, "admin");

        assertEquals("deepseek-chat", copy.getModelCode());
        assertTrue(copy.getModelName().contains("副本"));
        assertEquals(Integer.valueOf(0), copy.getIsDefault());
        assertEquals(0, copy.getStatus());
        assertNull(copy.getApiKey());
        assertNull(copy.getId());
        assertEquals("sk-source-key", source.getApiKey());
        verify(aiModelMapper, never()).insert(any());
        verifyNoInteractions(aiConfigRefreshService);
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
    void editingCurrentChatModelCannotDisableIt() {
        AIModel existing = stored(3L, "deepseek-chat");
        existing.setIsDefault(1);
        when(aiModelMapper.findById(3L)).thenReturn(existing);
        AIModel patch = new AIModel();
        patch.setId(3L);
        patch.setStatus(0);

        BusinessException error = assertThrows(BusinessException.class, () -> service.update(patch));

        assertTrue(error.getMessage().contains("先选择其他模型"));
        verify(aiModelMapper, never()).update(any());
        verifyNoInteractions(aiConfigRefreshService);
    }

    @Test
    void editingCurrentChatModelCannotClearItsDefaultAssignment() {
        AIModel existing = stored(3L, "deepseek-chat");
        existing.setIsDefault(1);
        when(aiModelMapper.findById(3L)).thenReturn(existing);
        AIModel patch = new AIModel();
        patch.setId(3L);
        patch.setIsDefault(0);

        BusinessException error = assertThrows(BusinessException.class, () -> service.update(patch));

        assertTrue(error.getMessage().contains("其他聊天模型设为默认"));
        verify(aiModelMapper, never()).update(any());
        verifyNoInteractions(aiConfigRefreshService);
    }

    @Test
    void editingCurrentVoiceModelCannotDisableItEvenWhileClearingItsAssignment() {
        AIModel existing = stored(3L, "qwen3-asr-flash-realtime");
        when(aiModelMapper.findById(3L)).thenReturn(existing);
        AgentVoiceSettings voice = mock(AgentVoiceSettings.class);
        ReflectionTestUtils.setField(service, "voiceSettings", voice);
        when(voice.selectedId()).thenReturn(3L);
        AIModel patch = new AIModel();
        patch.setId(3L);
        patch.setStatus(0);
        patch.setUseForVoice(false);

        assertThrows(BusinessException.class, () -> service.update(patch));

        verify(aiModelMapper, never()).update(any());
        verify(voice, never()).save(anyLong());
        verifyNoInteractions(aiConfigRefreshService);
    }

    @Test
    void currentChatModelCanStillEditParametersWithoutChangingItsAssignment() {
        AIModel existing = stored(3L, "deepseek-chat");
        existing.setIsDefault(1);
        when(aiModelMapper.findById(3L)).thenReturn(existing);
        when(aiModelMapper.update(any())).thenReturn(1);
        AIModel patch = new AIModel();
        patch.setId(3L);
        patch.setModelName("当前聊天模型的新名称");
        patch.setMaxTokens(2048);

        assertEquals(1, service.update(patch));

        verify(aiModelMapper).update(patch);
        assertNull(patch.getIsDefault());
        assertEquals(1, existing.getIsDefault());
        verify(aiConfigRefreshService).refreshAIConfig();
    }

    @Test
    void enabledVoiceModelCanStillClearItsAssignmentUsingExistingVoiceRules() {
        AIModel existing = stored(3L, "qwen3-asr-flash-realtime");
        when(aiModelMapper.findById(3L)).thenReturn(existing);
        when(aiModelMapper.update(any())).thenReturn(1);
        AgentVoiceSettings voice = mock(AgentVoiceSettings.class);
        ReflectionTestUtils.setField(service, "voiceSettings", voice);
        when(voice.selectedId()).thenReturn(3L, 3L, 0L);
        AIModel patch = new AIModel();
        patch.setId(3L);
        patch.setStatus(1);
        patch.setUseForVoice(false);

        assertEquals(1, service.update(patch));

        verify(voice).save(0L);
        verify(voice, never()).resolve();
        verify(aiModelMapper).update(patch);
        verify(aiConfigRefreshService).refreshAIConfig();
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
        assertTrue(seenRequestBody.contains("\"max_tokens\":4096"));
        assertFalse(seenRequestBody.contains("\"temperature\""));
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
    void probeUsesConfiguredProviderOutputBudget() throws Exception {
        stubBody = chatCompletion("OK");
        startStub();
        AIModel input = probe();
        input.setMaxTokens(2048);
        assertEquals(true, service.testConnection(input).get("ok"));
        assertTrue(seenRequestBody.contains("\"max_tokens\":2048"));
        input.setMaxTokens(0);
        assertThrows(BusinessException.class, () -> service.testConnection(input));
    }

    @Test
    void savedProbeUsesSavedProviderOutputBudget() throws Exception {
        stubBody = chatCompletion("OK");
        startStub();
        AIModel existing = stored(3L, "Baichuan4-Turbo");
        existing.setApiEndpoint(stubBase());
        existing.setApiKey("sk-stored");
        existing.setMaxTokens(2048);
        when(aiModelMapper.findById(3L)).thenReturn(existing);
        AIModel input = new AIModel();
        input.setId(3L);
        assertEquals(true, service.testConnection(input).get("ok"));
        assertTrue(seenRequestBody.contains("\"max_tokens\":2048"));
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

    @ParameterizedTest
    @ValueSource(strings = {"", "<html>Gateway login</html>", "{}", "{\"choices\":[]}",
            "{\"error\":{\"code\":\"invalid_api_key\",\"message\":\"sk-probe\"}}",
            "{\"choices\":[{\"message\":{\"content\":\"\",\"reasoning_content\":\"thinking\"}}]}"})
    void successfulHttpStatusAloneNeverProvesChatWorks(String response) throws Exception {
        stubBody = response;
        startStub();

        Map<String, Object> result = service.testConnection(probe());

        assertEquals(false, result.get("ok"));
        assertNull(result.get("reply"));
        assertNotNull(result.get("error"));
        assertFalse(result.toString().contains("sk-probe"));
    }

    @Test
    void maliciousUpstreamCannotEchoKeyThroughErrorsOrReplies() throws Exception {
        stubStatus = 400;
        stubBody = "bad token: sk-probe";
        startStub();
        assertFalse(service.testConnection(probe()).toString().contains("sk-probe"));

        stubStatus = 200;
        stubBody = chatCompletion("Your key is sk-probe");
        Map<String, Object> result = service.testConnection(probe());
        assertEquals(true, result.get("ok"));
        assertFalse(result.toString().contains("sk-probe"));
    }

    @Test
    void discoveryUsesNormalizedGatewayPathAndDeduplicatesExactIds() throws Exception {
        stubBody = "{\"data\":[{\"id\":\"deepseek-ai/DeepSeek-V3\"},{\"id\":\"ep-2026010101-abcd\"},"
                + "{\"id\":\"deepseek-ai/DeepSeek-V3\"},{\"id\":\"\"},{\"name\":\"ignored\"}]}";
        startStub();
        AIModel input = probe();
        input.setApiEndpoint(stubBase() + "/gateway/v1/chat/completions/");
        input.setModelCode(null);

        Map<String, Object> result = service.discoverModels(input);

        assertEquals(List.of("deepseek-ai/DeepSeek-V3", "ep-2026010101-abcd"), result.get("models"));
        assertEquals(stubBase() + "/gateway/v1", result.get("endpoint"));
        assertEquals("/gateway/v1/models", seenPath);
        assertEquals("GET", seenMethod);
        assertEquals("Bearer sk-probe", seenAuth);
        verifyNoInteractions(aiModelMapper);
    }

    @Test
    void discoveryUsesSavedCredentialsAndRejectsNonModelResponses() throws Exception {
        stubBody = "{\"data\":[{\"id\":\"glm-4-plus\"}]}";
        startStub();
        AIModel existing = stored(3L, "glm-4-plus");
        existing.setApiEndpoint(stubBase());
        existing.setApiKey("sk-stored");
        when(aiModelMapper.findById(3L)).thenReturn(existing);
        AIModel input = new AIModel();
        input.setId(3L);

        assertEquals(List.of("glm-4-plus"), service.discoverModels(input).get("models"));
        assertEquals("Bearer sk-stored", seenAuth);
        stubBody = "<html>login</html>";
        assertThrows(BusinessException.class, () -> service.discoverModels(input));
        stubBody = "{\"choices\":[]}";
        assertThrows(BusinessException.class, () -> service.discoverModels(input));
        stubStatus = 401;
        stubBody = "Your API Key sk-stored is invalid";
        assertFalse(assertThrows(BusinessException.class, () -> service.discoverModels(input)).getMessage().contains("sk-stored"));
    }

    @Test
    void changingGatewayWithoutNewKeyFailsBeforeNetworkForSaveProbeAndDiscovery() {
        AIModel existing = stored(3L, "deepseek-chat");
        existing.setApiEndpoint("https://old.example/v1");
        existing.setApiKey("sk-old");
        when(aiModelMapper.findById(3L)).thenReturn(existing);
        AIModel input = new AIModel();
        input.setId(3L);
        input.setApiEndpoint("https://new.example/v1");

        assertThrows(BusinessException.class, () -> service.testConnection(input));
        assertThrows(BusinessException.class, () -> service.discoverModels(input));
        assertThrows(BusinessException.class, () -> service.update(input));
        verify(aiModelMapper, never()).update(any());
    }

    @Test
    void equivalentEndpointKeepsStoredKeyAndClearConfigUsesWholeGlobalPair() throws Exception {
        stubBody = chatCompletion("OK");
        startStub();
        AIModel existing = stored(3L, "deepseek-chat");
        existing.setApiEndpoint(stubBase() + "/v1");
        existing.setApiKey("sk-stored");
        when(aiModelMapper.findById(3L)).thenReturn(existing);
        AIModel input = new AIModel();
        input.setId(3L);
        input.setApiEndpoint(stubBase() + "/v1/chat/completions/");
        assertEquals(true, service.testConnection(input).get("ok"));
        assertEquals("Bearer sk-stored", seenAuth);

        ReflectionTestUtils.setField(service, "globalEndpoint", stubBase() + "/global/v1");
        ReflectionTestUtils.setField(service, "globalApiKey", "sk-global");
        input.setClearApiConfig(true);
        input.setApiKey("sk-ignored");
        assertEquals(true, service.testConnection(input).get("ok"));
        assertEquals("/global/v1/chat/completions", seenPath);
        assertEquals("Bearer sk-global", seenAuth);
        stubBody = "{\"data\":[]}";
        assertEquals(List.of(), service.discoverModels(input).get("models"));
        assertEquals("/global/v1/models", seenPath);
        assertEquals("Bearer sk-global", seenAuth);
    }

    @Test
    void independentGatewayNeverReceivesGlobalKey() {
        ReflectionTestUtils.setField(service, "globalApiKey", "sk-global");
        AIModel input = new AIModel();
        input.setApiEndpoint("https://independent.example/v1");
        input.setModelCode("deepseek-chat");
        assertThrows(BusinessException.class, () -> service.testConnection(input));
        assertThrows(BusinessException.class, () -> service.discoverModels(input));
    }

    @Test
    void invalidIdAndMaskedCredentialsFailBeforeNetwork() {
        AIModel input = new AIModel();
        input.setId(0L);
        assertThrows(BusinessException.class, () -> service.testConnection(input));
        assertThrows(BusinessException.class, () -> service.discoverModels(input));
        input.setId(999L);
        assertThrows(BusinessException.class, () -> service.testConnection(input));
        assertThrows(BusinessException.class, () -> service.discoverModels(input));
        input.setId(null);
        input.setApiKey("sk-****abcd");
        assertThrows(BusinessException.class, () -> service.testConnection(input));
    }

    @Test
    void savingNamespacedProviderIdPreservesItsRealValue() {
        AIModel input = stored(null, "deepseek-ai/DeepSeek-V3.2");
        input.setProvider("siliconflow");
        input.setApiEndpoint("https://api.siliconflow.cn/v1/chat/completions");
        input.setApiKey("  sk-provider  ");

        service.insert(input);

        ArgumentCaptor<AIModel> captor = ArgumentCaptor.forClass(AIModel.class);
        verify(aiModelMapper).insert(captor.capture());
        assertEquals("deepseek-ai/DeepSeek-V3.2", captor.getValue().getModelCode());
        assertEquals("https://api.siliconflow.cn/v1", captor.getValue().getApiEndpoint());
        assertEquals("sk-provider", captor.getValue().getApiKey());
    }

    @Test
    void tooLongProviderIdIsRejectedWithoutSilentlyChangingIt() {
        AIModel input = stored(null, "a".repeat(51));
        assertThrows(BusinessException.class, () -> service.insert(input));
        assertEquals("a".repeat(51), input.getModelCode());
        verify(aiModelMapper, never()).insert(any());
    }

    private AIModel probe() {
        AIModel input = new AIModel();
        input.setApiEndpoint(stubBase());
        input.setApiKey("sk-probe");
        input.setModelCode("stub-model");
        return input;
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
        server.createContext("/", exchange -> {
            try {
                seenAuth = exchange.getRequestHeaders().getFirst("Authorization");
                seenPath = exchange.getRequestURI().getPath();
                seenMethod = exchange.getRequestMethod();
                seenRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
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
