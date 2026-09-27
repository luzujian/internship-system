package com.internship.controller;

import com.internship.agent.business.AgentIdentityService;
import com.internship.anno.Log;
import com.internship.entity.Result;
import com.internship.service.automation.AutomationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/admin/automation-deliveries")
@PreAuthorize("hasRole('ADMIN')")
public class AutomationVerificationController {
    private final AutomationService service;
    private final AgentIdentityService identities;
    public AutomationVerificationController(AutomationService service,AgentIdentityService identities){this.service=service;this.identities=identities;}
    @GetMapping public Result list(@RequestHeader("Authorization") String auth,@RequestParam(defaultValue="0") int offset){return Result.success(service.uncertain(identities.resolve(auth),offset));}
    @PostMapping("/{id}/preview") public Result preview(@RequestHeader("Authorization") String auth,@PathVariable String id,@RequestBody Map<String,String> body){return Result.success(service.verifyPreview(identities.resolve(auth),id,body.get("outcome"),body.get("evidence")));}
    @PostMapping("/confirm") @Log(module="SYSTEM_MANAGEMENT",operationType="UPDATE",description="核实自动化邮件服务受理结果")
    public Result confirm(@RequestHeader("Authorization") String auth,@RequestBody Map<String,Object> body){
        if(!Boolean.TRUE.equals(body.get("confirmed")))throw new com.internship.exception.BusinessException("请先核对邮件服务记录。");
        return Result.success(service.verifyConfirm(identities.resolve(auth),Objects.toString(body.get("actionId"),"")));
    }
}
