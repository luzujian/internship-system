package com.internship.agent.business;
import com.internship.entity.AIModel;
import com.internship.entity.SystemConfig;
import com.internship.mapper.AIModelMapper;
import com.internship.service.SystemConfigService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AgentVoiceSettingsTest {
    final AIModelMapper models=mock(AIModelMapper.class);
    final SystemConfigService configs=mock(SystemConfigService.class);
    final AgentVoiceSettings settings=new AgentVoiceSettings(models,configs);
    AIModel voice(){var row=new AIModel();row.setModelCode("qwen3-asr-flash-realtime");row.setStatus(1);row.setApiEndpoint("https://voice.example/api-ws/v1/realtime");row.setApiKey("private-test-key");return row;}
    @Test void selectionContainsNoSecretsAndConfigurationChangesApplyToNextConnection(){
        var config=new SystemConfig();config.setConfigValue("5");when(configs.findByConfigKey("agent.voice.model-id")).thenReturn(config);
        var row=voice();when(models.findById(5L)).thenReturn(row);
        assertEquals("wss://voice.example/api-ws/v1/realtime",settings.resolve().endpoint());
        row.setModelCode("qwen3-asr-flash-realtime-2026-02-10");row.setApiKey("next-private-key");assertEquals("qwen3-asr-flash-realtime-2026-02-10",settings.resolve().model());assertEquals("next-private-key",settings.resolve().apiKey());
        assertEquals(2,settings.selection().size());assertFalse(settings.selection().toString().contains("private"));
        row.setStatus(0);assertThrows(AgentException.class,settings::resolve);
    }
    @Test void rejectsChatEndpointAndDisabledModelsBeforeSaving(){
        var row=voice();row.setApiEndpoint("https://provider.example/compatible-mode/v1");when(models.findById(5L)).thenReturn(row);
        assertThrows(AgentException.class,()->settings.save(5));verifyNoInteractions(configs);
    }
    @Test void omniUsesSameHttpEndpointAndUnsupportedVoiceIsRejected(){
        var row=voice();row.setModelCode("qwen3.8-omni-flash");row.setApiEndpoint("https://provider.example/compatible-mode/v1");
        when(models.findById(5L)).thenReturn(row);
        var config=new SystemConfig();config.setConfigValue("5");when(configs.findByConfigKey("agent.voice.model-id")).thenReturn(config);
        assertEquals(row.getApiEndpoint(),settings.resolve().endpoint());
        row.setModelCode("deepseek-chat");assertThrows(AgentException.class,settings::resolve);
    }
    @Test void explicitDisableDoesNotFallBackToEnvironment(){
        var config=new SystemConfig();config.setConfigValue("0");when(configs.findByConfigKey("agent.voice.model-id")).thenReturn(config);
        assertThrows(AgentException.class,settings::resolve);verifyNoInteractions(models);
    }
}
