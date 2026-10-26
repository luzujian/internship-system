package com.internship.service;

import com.internship.agent.business.AgentFeatureGate;
import com.internship.entity.SystemConfig;
import com.internship.mapper.SystemConfigMapper;
import com.internship.service.impl.SystemConfigServiceImpl;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SystemConfigAiOnboardingTest {
    private final SystemConfigMapper mapper = mock(SystemConfigMapper.class);
    private final SystemConfigServiceImpl service = new SystemConfigServiceImpl(mapper);

    private SystemConfig toggle(boolean enabled) {
        var value = new SystemConfig(); value.setId(1L); value.setConfigKey(AgentFeatureGate.CONFIG_KEY);
        value.setConfigValue(Boolean.toString(enabled)); value.setStatus(1); value.setConfigName("AI助手功能开关");
        return value;
    }

    @Test void onlyAnOffToOnTransitionRotatesTheGeneration() {
        var before = toggle(false);
        when(mapper.findById(1L)).thenReturn(before);
        when(mapper.lockByConfigKey(AgentFeatureGate.CONFIG_KEY)).thenReturn(before);
        when(mapper.update(any())).thenReturn(1);
        service.update(toggle(true));
        verify(mapper).saveStudentAiOnboardingGeneration(anyString());

        clearInvocations(mapper);
        before.setConfigValue("true");
        service.update(toggle(true)); service.update(toggle(false));
        verify(mapper, never()).saveStudentAiOnboardingGeneration(anyString());
    }

    @Test void creatingAnEnabledSwitchStartsTheFirstGeneration() {
        when(mapper.insert(any())).thenReturn(1);
        service.insert(toggle(true));
        verify(mapper).saveStudentAiOnboardingGeneration(anyString());
        clearInvocations(mapper);
        service.insert(toggle(false));
        verify(mapper, never()).saveStudentAiOnboardingGeneration(anyString());
    }

    @Test void unrelatedSettingsAndFailedUpdatesCannotResetStudentPrompts() {
        var other = toggle(false); other.setConfigKey("OTHER_SETTING");
        when(mapper.findById(1L)).thenReturn(other);
        when(mapper.update(any())).thenReturn(1);
        service.update(other);
        verify(mapper, never()).saveStudentAiOnboardingGeneration(anyString());

        var before = toggle(false);
        when(mapper.findById(1L)).thenReturn(before);
        when(mapper.lockByConfigKey(AgentFeatureGate.CONFIG_KEY)).thenReturn(before);
        when(mapper.update(any())).thenReturn(0);
        service.update(toggle(true));
        verify(mapper, never()).saveStudentAiOnboardingGeneration(anyString());
    }
}
