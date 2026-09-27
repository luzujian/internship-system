package com.internship.agent.business;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 无需再次调用模型即可核对本人最新计划；权限与对话读取使用同一服务。 */
@RestController
@RequestMapping("/api/agent/student-plan")
@RequiredArgsConstructor
public class AgentStudentPlanController {
    private final AgentIdentityService identity;
    private final AgentFeatureGate featureGate;
    private final AgentReadTools reads;

    @GetMapping
    public Map<String, Object> current(@RequestHeader("Authorization") String authorization) {
        var actor = identity.resolve(authorization);
        featureGate.requireEnabled();
        return Map.of("code", 200, "data", reads.execute(actor, "student_plan", "{}"));
    }

    @ExceptionHandler(AgentException.class)
    public ResponseEntity<Map<String, Object>> error(AgentException error) {
        return ResponseEntity.status(error.getHttpStatus()).body(Map.of("code", error.getHttpStatus(),
                "errorCode", error.getCode(), "message", error.getMessage()));
    }
}
