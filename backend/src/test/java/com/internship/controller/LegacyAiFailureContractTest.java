package com.internship.controller;

import com.internship.agent.business.AgentException;
import com.internship.agent.business.AgentTypes;
import com.internship.agent.business.LegacyAiAccessPolicy;
import com.internship.agent.business.LegacyAiRequestLimiter;
import com.internship.config.DynamicChatClientFactory;
import com.internship.entity.User;
import com.internship.exception.BusinessException;
import com.internship.exception.GlobalExceptionHandler;
import com.internship.service.JobDescriptionGeneratorService;
import com.internship.service.JobRecommendationAgentService;
import com.internship.utils.CurrentHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LegacyAiFailureContractTest {
    private final DynamicChatClientFactory clients = mock(DynamicChatClientFactory.class);
    private final ChatClient model = mock(ChatClient.class, RETURNS_DEEP_STUBS);
    private final JobRecommendationAgentService recommendations = mock(JobRecommendationAgentService.class);
    private final JobDescriptionGeneratorService descriptions = mock(JobDescriptionGeneratorService.class);
    private final LegacyAiAccessPolicy access = mock(LegacyAiAccessPolicy.class);
    private final AgentException busy = new AgentException("AI_BUSY", "AI繁忙，请稍后重试", 429);
    private AIChatController chat;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        when(clients.createChatClient(anyString(), anyString())).thenReturn(model);
        when(access.require()).thenReturn(actor("ROLE_COMPANY"));
        chat = new AIChatController(clients, recommendations);
        var generation = new JobDescriptionGeneratorController(descriptions);
        var limiter = new LegacyAiRequestLimiter();
        for (Object controller : List.of(chat, generation)) {
            ReflectionTestUtils.setField(controller, "legacyAccess", access);
            ReflectionTestUtils.setField(controller, "requestLimiter", limiter);
        }
        var user = new User();
        user.setUsername("authenticated-student");
        CurrentHolder.setUser(user);
        mvc = MockMvcBuilders.standaloneSetup(chat, generation)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void tearDown() {
        CurrentHolder.remove();
        chat.shutdown();
    }

    @Test
    void descriptionBusyKeeps429AndReleasesRequestLease() throws Exception {
        when(descriptions.generateJobDescription(anyString(), isNull(), isNull(), isNull())).thenThrow(busy);
        for (int attempt = 0; attempt < 2; attempt++) {
            expectBusy("/api/ai/generate-job-description", "{\"positionName\":\"开发\"}");
        }
        verify(descriptions, times(2)).generateJobDescription("开发", null, null, null);
    }

    @Test
    void normalChatBusyKeeps429AndReleasesRequestLease() throws Exception {
        when(model.prompt().messages(anyList()).call().content()).thenThrow(busy);
        for (int attempt = 0; attempt < 2; attempt++) {
            expectBusy("/api/ai/chat", "{\"message\":\"说明实习申请流程\"}");
        }
        verify(clients, times(2)).createChatClient(anyString(), anyString());
    }

    @Test
    void recommendationBusyKeeps429() throws Exception {
        when(access.require()).thenReturn(actor("ROLE_STUDENT"));
        when(recommendations.processJobRecommendation(anyString(), anyString(), isNull())).thenThrow(busy);
        expectBusy("/api/ai/chat", "{\"message\":\"推荐实习岗位\"}");
        verifyNoInteractions(clients);
    }

    @Test
    void streamStartupBusyKeeps429AndCleansUp() throws Exception {
        when(clients.createChatClient(anyString(), anyString())).thenThrow(busy);
        for (int attempt = 0; attempt < 2; attempt++) {
            expectBusy("/api/ai/chat/stream", "{\"message\":\"说明实习申请流程\"}");
        }
        verify(clients, times(2)).createChatClient(anyString(), anyString());
        assertTrue(((List<?>) ReflectionTestUtils.getField(chat, "emitters")).isEmpty());
    }

    @Test
    void streamingModelBusyKeepsErrorEventAndStatusDetails() throws Exception {
        when(model.prompt().messages(anyList()).stream().content()).thenReturn(Flux.error(busy));
        expectStreamBusy("说明实习申请流程", "生成过程中发生错误: " + busy.getMessage());
        expectStreamBusy("说明实习申请流程", "生成过程中发生错误: " + busy.getMessage());
    }

    @Test
    void recommendationWorkerBusyKeepsErrorEventAndStatusDetails() throws Exception {
        when(access.require()).thenReturn(actor("ROLE_STUDENT"));
        when(recommendations.processJobRecommendation(anyString(), anyString(), isNull())).thenThrow(busy);
        expectStreamBusy("推荐实习岗位", "推荐服务暂时不可用");
        verifyNoInteractions(clients);
    }

    @Test
    void ordinaryFailuresKeepLegacyJsonContract() throws Exception {
        when(descriptions.generateJobDescription(anyString(), isNull(), isNull(), isNull()))
                .thenThrow(new BusinessException("AI未返回岗位描述"));
        mvc.perform(post("/api/ai/generate-job-description").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionName\":\"开发\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("生成失败: AI未返回岗位描述"))
                .andExpect(jsonPath("$.timestamp").isNumber());
        when(clients.createChatClient(anyString(), anyString())).thenThrow(new IllegalStateException("provider unavailable"));
        mvc.perform(post("/api/ai/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"说明实习申请流程\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("AI服务暂时不可用"))
                .andExpect(jsonPath("$.timestamp").isNumber());
    }

    @Test
    void unauthenticatedRequestKeeps401() throws Exception {
        when(access.require()).thenThrow(new AgentException("UNAUTHORIZED", "请先登录", 401));
        for (String path : List.of("/api/ai/chat", "/api/ai/chat/stream", "/api/ai/generate-job-description")) {
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(401))
                    .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
        }
        verifyNoInteractions(clients, recommendations, descriptions);
    }

    private void expectStreamBusy(String message, String expectedMessage) throws Exception {
        var stream = mvc.perform(post("/api/ai/chat/stream").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + message + "\"}"))
                .andExpect(request().asyncStarted()).andReturn();
        var response = mvc.perform(asyncDispatch(stream)).andExpect(status().isOk()).andReturn();
        String events = response.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(events.contains("event:error"), events);
        assertFalse(events.contains("event:end"), events);
        String data = events.lines().filter(line -> line.startsWith("data:")).findFirst().orElseThrow().substring(5);
        var error = new com.fasterxml.jackson.databind.ObjectMapper().readTree(data);
        assertEquals("error", error.get("type").asText());
        assertEquals(expectedMessage, error.get("message").asText());
        assertEquals(429, error.get("code").asInt());
        assertEquals(429, error.get("status").asInt());
        assertEquals("AI_BUSY", error.get("errorCode").asText());
    }

    private void expectBusy(String path, String body) throws Exception {
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(429))
                .andExpect(jsonPath("$.errorCode").value("AI_BUSY"))
                .andExpect(jsonPath("$.message").value(busy.getMessage()));
    }

    private AgentTypes.Actor actor(String role) {
        return new AgentTypes.Actor(role + ":1", 1L, role, "test", null, null, null, false, Set.of());
    }
}
