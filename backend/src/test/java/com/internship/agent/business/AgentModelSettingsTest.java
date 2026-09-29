package com.internship.agent.business;

import com.internship.entity.AIModel;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AgentModelSettingsTest {
    private AIModel row(String code, String temperature, Integer maxTokens) {
        AIModel model = new AIModel();
        model.setModelCode(code);
        if (temperature != null) model.setTemperature(new BigDecimal(temperature));
        model.setMaxTokens(maxTokens);
        return model;
    }

    @Test void defaultRowProvidesModelAndParameters() {
        var resolved = AgentModelSettings.resolve(row("deepseek-flash", "0.20", 2200), "deepseek-chat");
        assertEquals("deepseek-flash", resolved.modelCode());
        assertEquals(0.2d, resolved.temperature(), 1e-9);
        assertEquals(2200, resolved.maxTokens());
        assertEquals("ai_model.default", resolved.source());
    }

    @Test void missingRowOrCodeFallsBackToConfiguredModel() {
        assertEquals("deepseek-chat", AgentModelSettings.resolve(null, "deepseek-chat").modelCode());
        assertEquals("deepseek-chat", AgentModelSettings.resolve(row("  ", "0.9", 900), "deepseek-chat").modelCode());
        assertEquals(AgentModelSettings.FALLBACK_TEMPERATURE, AgentModelSettings.resolve(null, null).temperature(), 1e-9);
        assertEquals(AgentModelSettings.FALLBACK_MAX_TOKENS, AgentModelSettings.resolve(null, null).maxTokens());
    }

    @Test void administratorModelIdentifierIsNotSilentlyRewritten() {
        assertEquals("deepseek-v4-flash", AgentModelSettings.resolve(row("deepseek-v4-flash", "0.2", 2200), null).modelCode());
    }

    @Test void gatewayAliasIsKeptVerbatim() {
        AIModel gateway = row("deepseek-v4-flash", "0.2", 2200);
        gateway.setApiEndpoint("https://opencode.ai/zen/go/v1");
        gateway.setApiKey("sk-zen");
        var resolved = AgentModelSettings.resolve(gateway, null);
        assertEquals("deepseek-v4-flash", resolved.modelCode());
        assertEquals("https://opencode.ai/zen/go/v1", resolved.endpoint());
    }

    @Test void invalidNumbersAreClampedOrReplaced() {
        assertEquals(2d, AgentModelSettings.resolve(row("deepseek-flash", "7.5", 2200), null).temperature(), 1e-9);
        assertEquals(0d, AgentModelSettings.resolve(row("deepseek-flash", "-1", 2200), null).temperature(), 1e-9);
        assertEquals(AgentModelSettings.FALLBACK_TEMPERATURE, AgentModelSettings.resolve(row("deepseek-flash", null, 2200), null).temperature(), 1e-9);
        assertEquals(AgentModelSettings.FALLBACK_MAX_TOKENS, AgentModelSettings.resolve(row("deepseek-flash", "0.2", 0), null).maxTokens());
        assertEquals(8000, AgentModelSettings.resolve(row("deepseek-flash", "0.2", 99999), null).maxTokens());
    }
    @Test void runningAgentFollowsAdministratorDefaultAndFailsClosedWhenMissing() {
        var models=org.mockito.Mockito.mock(com.internship.service.AIModelService.class);
        var gateway=new DeepSeekAgentModel("test-key","http://127.0.0.1:1","legacy",models);
        AIModel first=row("qwen3.8-omni-flash","0.2",4000);first.setStatus(1);first.setApiEndpoint("https://first.example/v1");
        AIModel second=row("other-model","0.4",2000);second.setStatus(1);second.setApiEndpoint("https://second.example/v1");
        org.mockito.Mockito.when(models.findDefaultModel()).thenReturn(first,second,null);
        assertEquals("qwen3.8-omni-flash",gateway.resolve().modelCode());
        assertEquals("https://second.example/v1",gateway.resolve().endpoint());
        assertThrows(AgentException.class,gateway::resolve);
    }

}