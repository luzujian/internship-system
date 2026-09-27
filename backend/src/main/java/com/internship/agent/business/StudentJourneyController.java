package com.internship.agent.business;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/agent/student-plan/journey")
@RequiredArgsConstructor
public class StudentJourneyController {
    private final AgentIdentityService identity;
    private final StudentJourneyService journeys;

    @GetMapping
    public Map<String, Object> current(@RequestHeader("Authorization") String authorization) {
        return Map.of("code", 200, "data", journeys.current(identity.resolve(authorization)));
    }

    @PutMapping("/preference")
    @com.internship.anno.Log(module = "AI_ASSISTANT", description = "更新本人实习办理指引", targetType = "STUDENT")
    public Map<String, Object> save(@RequestHeader("Authorization") String authorization,
                                   @RequestBody StudentJourneyService.PreferenceRequest request) {
        return Map.of("code", 200, "data", journeys.save(identity.resolve(authorization), request));
    }

    @ExceptionHandler(AgentException.class)
    public ResponseEntity<Map<String, Object>> error(AgentException error) {
        return ResponseEntity.status(error.getHttpStatus()).body(Map.of("code", error.getHttpStatus(),
                "errorCode", error.getCode(), "message", error.getMessage()));
    }
}
