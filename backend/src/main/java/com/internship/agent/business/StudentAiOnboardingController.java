package com.internship.agent.business;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/agent/student-login-onboarding")
@RequiredArgsConstructor
public class StudentAiOnboardingController {
    private final AgentIdentityService identity;
    private final StudentAiOnboardingService onboarding;

    @PostMapping
    public Map<String, Object> claim(@RequestHeader("Authorization") String authorization) {
        return Map.of("code", 200, "data", Map.of("autoOpen", onboarding.claim(identity.resolve(authorization))));
    }
}
