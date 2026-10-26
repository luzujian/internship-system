package com.internship.importing;

import com.internship.agent.business.AgentException;
import com.internship.anno.Log;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController
@RequestMapping("/api/admin/smart-import")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SmartImportController {
    private final SmartImportService service;
    private final SmartImportAgentTools conversation;
    @PostMapping("/conversation/attachments")
    public Map<String,Object> attach(@RequestHeader("Authorization") String auth,@RequestParam("files") List<MultipartFile> files,
                                    @RequestParam(value="sessionId",required=false) String sessionId) {
        return Map.of("code",200,"data",conversation.attach(auth,sessionId,files));
    }
    @GetMapping("/conversation/{id}")
    public Map<String,Object> conversation(@RequestHeader("Authorization") String auth,@PathVariable String id,@RequestParam(value="sessionId",required=false) String sessionId) {
        return Map.of("code",200,"data",conversation.get(auth,id,sessionId));
    }
    @DeleteMapping("/conversation/attachments")
    public Map<String,Object> detach(@RequestHeader("Authorization") String auth,@RequestParam String sessionId) {
        conversation.detach(auth,sessionId);return Map.of("code",200);
    }
    @PostMapping("/preview")
    public Map<String,Object> preview(@RequestHeader("Authorization") String auth,@RequestParam("files") List<MultipartFile> files,
                                      @RequestParam(value="options",required=false) String options) {
        return Map.of("code",200,"data",service.preview(auth,files,options));
    }
    @PostMapping("/confirm")
    @Log(operationType="IMPORT",module="SMART_IMPORT",description="管理员确认智能导入数据",riskLevel="MEDIUM")
    public Map<String,Object> confirm(@RequestHeader("Authorization") String auth,@RequestBody SmartImportService.ConfirmRequest request) {
        return Map.of("code",200,"data",service.confirm(auth,request));
    }
    @ExceptionHandler(AgentException.class)
    public ResponseEntity<Map<String,Object>> error(AgentException e) {
        return ResponseEntity.status(e.getHttpStatus()).body(Map.of("code",e.getHttpStatus(),"errorCode",e.getCode(),"message",e.getMessage()));
    }
}
