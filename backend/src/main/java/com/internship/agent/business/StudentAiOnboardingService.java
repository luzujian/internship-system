package com.internship.agent.business;

import com.internship.entity.SystemConfig;
import com.internship.mapper.SystemConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** One login introduction per student for each administrator activation of AI. */
@Service
@RequiredArgsConstructor
public class StudentAiOnboardingService {
    static final String GENERATION_KEY = "AI_STUDENT_ONBOARDING_GENERATION";
    static final String RECEIPT_PREFIX = "AI_STUDENT_ONBOARDING_SEEN:";

    private final SystemConfigMapper configs;
    private final AgentFeatureGate featureGate;
    private final AgentCapabilityService capabilities;

    @Transactional
    public boolean claim(AgentTypes.Actor actor) {
        if (!"ROLE_STUDENT".equals(actor.role()) || actor.readOnly()
                || !featureGate.enabled() || !capabilities.has(actor, "agent_chat")) return false;

        // Serialize claims with switch changes and claims from other devices.
        SystemConfig toggle = configs.lockByConfigKey(AgentFeatureGate.CONFIG_KEY);
        if (toggle == null || (toggle.getStatus() != null && toggle.getStatus() != 1)
                || !Boolean.parseBoolean(toggle.getConfigValue())) return false;
        SystemConfig current = configs.lockByConfigKey(GENERATION_KEY);
        String generation = current == null ? null : current.getConfigValue();
        if (generation == null || generation.isBlank()) {
            // The switch may already be enabled when this version is first installed.
            generation = UUID.randomUUID().toString();
            configs.saveStudentAiOnboardingGeneration(generation);
        }
        String key = RECEIPT_PREFIX + actor.id();
        if (configs.insertStudentAiOnboardingReceipt(key, generation) == 1) return true;
        return configs.updateStudentAiOnboardingReceipt(key, generation) == 1;
    }
}
