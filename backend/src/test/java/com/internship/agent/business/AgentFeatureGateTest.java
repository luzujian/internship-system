package com.internship.agent.business;

import com.internship.entity.SystemConfig;
import com.internship.service.SystemConfigService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentFeatureGateTest {

    @Test void explicitOverrideIsDeterministic() {
        SystemConfigService config = mock(SystemConfigService.class);
        assertTrue(new AgentFeatureGate(config, "true").enabled());
        assertFalse(new AgentFeatureGate(config, "false").enabled());
        verifyNoInteractions(config);
    }

    @Test void systemModeReadsDatabaseSwitch() {
        SystemConfigService config = mock(SystemConfigService.class);
        SystemConfig disabled = new SystemConfig();
        disabled.setConfigValue("false");
        disabled.setStatus(1);
        when(config.findByConfigKey("AI_ASSISTANT_ENABLED")).thenReturn(disabled);
        AgentFeatureGate gate = new AgentFeatureGate(config, "system");
        assertFalse(gate.enabled());
        assertThrows(AgentException.class, gate::requireEnabled);
    }

    @Test void missingDatabaseValueDefaultsToEnabledCompatibility() {
        SystemConfigService config = mock(SystemConfigService.class);
        when(config.findByConfigKey("AI_ASSISTANT_ENABLED")).thenReturn(null);
        assertTrue(new AgentFeatureGate(config, "system").enabled());
    }

    @Test void lookupFailureClosesGate() {
        SystemConfigService config = mock(SystemConfigService.class);
        when(config.findByConfigKey("AI_ASSISTANT_ENABLED"))
                .thenThrow(new IllegalStateException("configuration unavailable"));
        AgentFeatureGate gate = new AgentFeatureGate(config, "system");

        assertFalse(gate.enabled());
        assertEquals("AI_DISABLED", assertThrows(AgentException.class, gate::requireEnabled).getCode());
    }
}
