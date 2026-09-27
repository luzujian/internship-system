package com.internship.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.internship.agent.business.*;
import com.internship.agent.business.AgentTypes.Actor;
import com.internship.anno.Log;
import com.internship.entity.Result;
import com.internship.service.automation.AutomationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/teacher/auto-tasks/v2")
@PreAuthorize("hasAnyRole('TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR')")
public class TeacherAutomationV2Controller {
    private final AutomationService service;
    private final AgentIdentityService identities;
    public TeacherAutomationV2Controller(AutomationService service,AgentIdentityService identities){this.service=service;this.identities=identities;}
    private Actor actor(String auth){return identities.resolve(auth);}
    @GetMapping("/catalog") public Result catalog(@RequestHeader("Authorization") String auth){return Result.success(service.catalog(actor(auth)));}
    @PostMapping("/drafts/preview") public Result draftPreview(@RequestHeader("Authorization") String auth,@RequestBody Map<String,Object> body){return Result.success(service.previewDraft(actor(auth),body));}
    @GetMapping("/drafts") public Result drafts(@RequestHeader("Authorization") String auth,@RequestParam(defaultValue="manager") String sessionId){return Result.success(service.drafts(actor(auth),sessionId));}
    @DeleteMapping("/drafts/{id}") @Log(module="TEACHING_MANAGEMENT",operationType="DELETE",description="关闭自动化草稿")
    public Result dismiss(@RequestHeader("Authorization") String auth,@PathVariable String id){service.dismissDraft(actor(auth),id);return Result.success();}
    @GetMapping("/actions/{id}") public Result receipt(@RequestHeader("Authorization") String auth,@PathVariable String id){return Result.success(service.receipt(actor(auth),id));}
    @PostMapping("/preview") public Result preview(@RequestHeader("Authorization") String auth,@RequestBody Map<String,Object> body){
        Actor actor=actor(auth);Object taskId=body.get("taskId");String operation=Objects.toString(body.get("operation"),"CREATE");
        JsonNode def;
        if(Set.of("RUN","RETRY").contains(operation)){
            if(!(taskId instanceof Number n))throw new AgentException("INVALID_INPUT","请先选择任务。",400);
            def=service.definition(service.owned(actor,n.longValue()));
        }else if(body.get("definition")!=null)def=service.decode(service.encode(body.get("definition")));
        else if(taskId instanceof Number n)def=service.definition(service.owned(actor,n.longValue()));
        else def=service.decode(service.encode(body.get("definition")));
        return Result.success(service.preview(actor,def,taskId,operation,Boolean.TRUE.equals(body.get("saveOnly")),"RUN".equals(operation)&&Boolean.TRUE.equals(body.get("force"))));
    }
    @PostMapping("/confirm")
    @Log(module="TEACHING_MANAGEMENT",operationType="UPDATE",description="确认自动化任务变更或执行")
    public Result confirm(@RequestHeader("Authorization") String auth,@RequestBody Map<String,Object> body){
        if(!Boolean.TRUE.equals(body.get("confirmed")))throw new AgentException("CONFIRMATION_REQUIRED","请核对任务后确认。",400);
        return Result.success(service.confirm(actor(auth),Objects.toString(body.get("actionId"),"")));
    }
    @GetMapping("/tasks") public Result tasks(@RequestHeader("Authorization") String auth){return Result.success(service.list(actor(auth)));}
    @GetMapping("/tasks/{id}") public Result task(@RequestHeader("Authorization") String auth,@PathVariable long id){return Result.success(service.get(actor(auth),id));}
    @PostMapping("/tasks/{id}/pause")
    @Log(module="TEACHING_MANAGEMENT",operationType="UPDATE",description="暂停自动化任务")
    public Result pause(@RequestHeader("Authorization") String auth,@PathVariable long id){return Result.success(service.pause(actor(auth),id,false));}
    @DeleteMapping("/tasks/{id}")
    @Log(module="TEACHING_MANAGEMENT",operationType="DELETE",description="停止并删除自动化任务")
    public Result delete(@RequestHeader("Authorization") String auth,@PathVariable long id){return Result.success(service.pause(actor(auth),id,true));}
    @GetMapping("/tasks/{id}/runs") public Result runs(@RequestHeader("Authorization") String auth,@PathVariable long id,@RequestParam(defaultValue="0") int offset){return Result.success(service.runs(actor(auth),id,offset));}
    @GetMapping("/tasks/{id}/deliveries") public Result deliveries(@RequestHeader("Authorization") String auth,@PathVariable long id,@RequestParam(defaultValue="0") int offset){return Result.success(service.deliveries(actor(auth),id,offset));}
    @PostMapping("/matches") public Result matches(@RequestHeader("Authorization") String auth,@RequestBody Map<String,Object> body){
        Actor actor=actor(auth);var teacher=service.require(actor,false);JsonNode def=service.decode(service.encode(body.get("definition")));
        com.internship.service.automation.AutomationRules.validate(def.path("condition"));
        int offset=body.get("offset") instanceof Number n?Math.max(0,n.intValue()):0;
        var all=service.matching(teacher,def);return Result.success(Map.of("total",all.size(),"items",all.stream().skip(offset).limit(50).map(f->Map.of("name",f.get("name"),"username",f.get("student.username"))).toList()));
    }
}
