package com.internship.agent.business;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.Map;

@RestController
@RequestMapping("/api/agent/voice")
public class AgentVoiceController {
    private final AgentVoiceService voice;
    public AgentVoiceController(AgentVoiceService voice){this.voice=voice;}
    @PostMapping("/sessions")
    public Map<String,Object> create(@RequestHeader("Authorization") String authorization){return Map.of("code",200,"data",voice.create(authorization));}
    @ExceptionHandler(AgentException.class)
    public ResponseEntity<Map<String,Object>> error(AgentException e){return ResponseEntity.status(e.getHttpStatus()).body(Map.of("code",e.getHttpStatus(),"errorCode",e.getCode(),"message",e.getMessage()));}
}
