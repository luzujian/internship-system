package com.internship.agent.business;

import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class LegacyAiAccessPolicy {
    private final AgentFeatureGate gate;
    private final AgentCapabilityService capabilities;
    private final AgentIdentityService identities;
    public LegacyAiAccessPolicy(AgentFeatureGate gate, AgentCapabilityService capabilities, AgentIdentityService identities) {
        this.gate = gate; this.capabilities = capabilities; this.identities = identities;
    }
    public AgentTypes.Actor require() {
        gate.requireEnabled();
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            throw new AgentException("UNAUTHORIZED", "请先登录", 401);
        }
        var actor = identities.resolve(attributes.getRequest().getHeader("Authorization"));
        if (!capabilities.has(actor, "agent_chat") || actor.readOnly()) {
            throw new AgentException("CAPABILITY_DISABLED", "当前身份未开启 AI 对话能力", 403);
        }
        return actor;
    }
}
