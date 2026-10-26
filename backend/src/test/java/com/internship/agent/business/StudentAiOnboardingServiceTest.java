package com.internship.agent.business;

import com.internship.entity.SystemConfig;
import com.internship.mapper.SystemConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StudentAiOnboardingServiceTest {
    private final SystemConfigMapper configs = mock(SystemConfigMapper.class);
    private final AgentFeatureGate gate = mock(AgentFeatureGate.class);
    private final AgentCapabilityService caps = mock(AgentCapabilityService.class);
    private final StudentAiOnboardingService service = new StudentAiOnboardingService(configs, gate, caps);
    private final Map<String, String> receipts = new HashMap<>();
    private final SystemConfig toggle = new SystemConfig(), generation = new SystemConfig();
    private final AgentTypes.Actor student = actor(8L, "ROLE_STUDENT", false);

    private static AgentTypes.Actor actor(long id, String role, boolean readOnly) {
        return new AgentTypes.Actor(role + ":" + id, id, role, "", null, null, null, readOnly, Set.of());
    }

    @BeforeEach void enabled() {
        when(gate.enabled()).thenReturn(true);
        when(caps.has(any(), eq("agent_chat"))).thenReturn(true);
        toggle.setConfigValue("true"); toggle.setStatus(1);
        generation.setConfigValue("activation-one");
        when(configs.lockByConfigKey(AgentFeatureGate.CONFIG_KEY)).thenReturn(toggle);
        when(configs.lockByConfigKey(StudentAiOnboardingService.GENERATION_KEY)).thenReturn(generation);
        when(configs.insertStudentAiOnboardingReceipt(anyString(), anyString())).thenAnswer(call ->
                receipts.putIfAbsent(call.getArgument(0), call.getArgument(1)) == null ? 1 : 0);
        when(configs.updateStudentAiOnboardingReceipt(anyString(), anyString())).thenAnswer(call -> {
            String key = call.getArgument(0), value = call.getArgument(1);
            if (value.equals(receipts.get(key))) return 0;
            receipts.put(key, value); return 1;
        });
    }

    @Test void loginOnlyPromptsOncePerStudentAndSurvivesServiceRecreation() {
        assertTrue(service.claim(student));
        assertFalse(service.claim(student));
        assertFalse(new StudentAiOnboardingService(configs, gate, caps).claim(student));
        assertTrue(service.claim(actor(9L, "ROLE_STUDENT", false)));
        assertEquals(2, receipts.size());
    }

    @Test void reopeningAiCreatesAnotherChanceForEveryStudent() {
        assertTrue(service.claim(student));
        toggle.setConfigValue("false");
        assertFalse(service.claim(student));
        toggle.setConfigValue("true"); generation.setConfigValue("activation-two");
        assertTrue(service.claim(student));
        assertFalse(service.claim(student));
        assertEquals("activation-two", receipts.get(StudentAiOnboardingService.RECEIPT_PREFIX + "8"));
    }

    @Test void disabledGateDoesNotConsumeThePrompt() {
        when(gate.enabled()).thenReturn(false);
        assertFalse(service.claim(student));
        assertTrue(receipts.isEmpty()); verifyNoInteractions(configs);
        when(gate.enabled()).thenReturn(true);
        assertTrue(service.claim(student));
    }

    @Test void administratorsTeachersCompaniesAndImpersonationCannotConsumePrompts() {
        for (String role : Set.of("ROLE_ADMIN", "ROLE_TEACHER", "ROLE_COMPANY"))
            assertFalse(service.claim(actor(8L, role, false)));
        assertFalse(service.claim(actor(8L, "ROLE_STUDENT", true)));
        verifyNoInteractions(configs);
    }

    @Test void missingChatPermissionDoesNotConsumePrompt() {
        when(caps.has(student, "agent_chat")).thenReturn(false);
        assertFalse(service.claim(student));
        verifyNoInteractions(configs);
    }

    @Test void switchDisabledDuringLoginIsRecheckedUnderLock() {
        toggle.setConfigValue("false");
        assertFalse(service.claim(student));
        verify(configs, never()).insertStudentAiOnboardingReceipt(anyString(), anyString());
    }

    @Test void absentOrInactiveSwitchDoesNotCountAsAdministratorActivation() {
        when(configs.lockByConfigKey(AgentFeatureGate.CONFIG_KEY)).thenReturn(null);
        assertFalse(service.claim(student));
        when(configs.lockByConfigKey(AgentFeatureGate.CONFIG_KEY)).thenReturn(toggle);
        toggle.setStatus(0);
        assertFalse(service.claim(student));
        assertTrue(receipts.isEmpty());
    }

    @Test void anAlreadyEnabledInstallationGetsOneStableInitialGeneration() {
        when(configs.lockByConfigKey(StudentAiOnboardingService.GENERATION_KEY)).thenReturn(null);
        doAnswer(call -> {
            generation.setConfigValue(call.getArgument(0));
            when(configs.lockByConfigKey(StudentAiOnboardingService.GENERATION_KEY)).thenReturn(generation);
            return null;
        }).when(configs).saveStudentAiOnboardingGeneration(anyString());
        assertTrue(service.claim(student));
        assertFalse(service.claim(student));
        verify(configs, times(1)).saveStudentAiOnboardingGeneration(anyString());
    }
}
