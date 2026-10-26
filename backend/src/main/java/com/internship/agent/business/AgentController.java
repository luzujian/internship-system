package com.internship.agent.business;

import com.internship.anno.Log;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.*;
import java.util.concurrent.*;
import static com.internship.agent.business.AgentTypes.*;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {
    private final AgentIdentityService identity;
    private final AgentTaskService tasks;
    private final AgentStore store;
    private final FillIntentService fillIntents;
    private final AgentUiIntentService uiIntents;
    private final JobApplyExecuteService executes;
    private final TeacherReminderExecuteService reminders;
    private final AgentActionService actions;
    private final com.internship.service.SysDictService dictService;
    private final ExecutorService streams=Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore connections=new Semaphore(64);

    @GetMapping("/capabilities")
    public Map<String,Object> capabilities(@RequestHeader("Authorization")String auth){return success(tasks.capabilities(identity.resolve(auth)));}
    /** 字典标签：页面标题/对象类型/筛选名/功能名/路由映射等动态标签。 */
    @GetMapping("/dicts")
    public Map<String,Object> dicts(){
        return success(Map.of(
            "pageTitle", dictService.labelsByType("page_title"),
            "objectType", dictService.labelsByType("object_type"),
            "filterName", dictService.labelsByType("filter_name"),
            "featureLabel", dictService.labelsByType("feature_label"),
            "actionRoute", dictService.labelsByType("action_route"),
            "actionRouteQueryKey", dictService.byType("action_route").stream().collect(
                java.util.stream.Collectors.toMap(d -> d.getDictCode(), d -> d.getDictValue() == null ? "" : d.getDictValue()))));
    }
    @PostMapping("/tasks")
    public Map<String,Object> create(@RequestHeader("Authorization")String auth,@RequestBody CreateTask input){return success(tasks.create(auth,input));}
    @PostMapping("/tasks/{id}/resend")
    public Map<String,Object> resend(@RequestHeader("Authorization")String auth,@PathVariable String id,@RequestBody ResendTask input){return success(tasks.resend(auth,id,input));}
    @PostMapping("/tasks/{id}/ui-feedback")
    public Map<String,Object> uiFeedback(@RequestHeader("Authorization")String auth,@PathVariable String id,@RequestBody UiFeedback input){return success(tasks.continueUi(auth,id,input));}
    /** 智能填表：模型只产出字段赋值意图，真实写入由前端完成；上行不含用户已填值。 */
    @PostMapping("/forms/fill-intent")
    public Map<String,Object> fillIntent(@RequestHeader("Authorization")String auth,@RequestBody FillIntentService.FillRequest input){
        Actor actor=identity.resolve(auth);Map<String,Object> result=fillIntents.fill(actor,input);
        // Model latency must not allow a disabled account or changed role to receive writable fields.
        Actor current=identity.resolve(auth);
        if(!actor.ownerKey().equals(current.ownerKey()))throw new AgentException("SCOPE_CHANGED","当前身份已变化，请重新打开表单后再试。",403);
        return success(result);
    }
    @PostMapping("/ui/intent")
    public Map<String,Object> uiIntent(@RequestHeader("Authorization")String auth,@RequestBody AgentUiIntentService.Request input){
        Actor actor=identity.resolve(auth);
        Map<String,Object> result=uiIntents.resolve(actor,input);
        if(!actor.ownerKey().equals(identity.resolve(auth).ownerKey()))throw AgentIdentityService.forbidden();
        return success(result);
    }
    /** 投递执行：唯一写入通道。模型不参与决策；前端确认弹窗回显岗位后调用，需 confirmed=true。 */
    @PostMapping("/job-applications/apply")
    @Log(module = "APPLICATION_MANAGEMENT", operationType = "ADD", description = "AI助手确认后代投职位")
    public Map<String,Object> apply(@RequestHeader("Authorization")String auth,@RequestBody JobApplyExecuteService.ApplyRequest input){return success(executes.apply(identity.resolve(auth),input));}
    @PostMapping("/job-applications/preview")
    public Map<String,Object> previewAction(@RequestHeader("Authorization")String auth,@RequestBody JobApplyExecuteService.PreviewRequest input){return success(executes.preview(identity.resolve(auth),input));}
    /** 教师单次提醒：预览只创建确认卡；模型不参与发送决策，确认后才真正发送。 */
    @PostMapping("/reminders/preview")
    public Map<String,Object> previewReminder(@RequestHeader("Authorization")String auth,@RequestBody TeacherReminderExecuteService.PreviewRequest input){return success(reminders.preview(identity.resolve(auth),input));}
    @PostMapping("/reminders/send")
    @Log(operationType = "CREATE", module = "STUDENT_INTERNSHIP", description = "AI助手确认后代发学生提醒", riskLevel = "MEDIUM")
    public Map<String,Object> sendReminder(@RequestHeader("Authorization")String auth,@RequestBody TeacherReminderExecuteService.SendRequest input){return success(reminders.send(identity.resolve(auth),input));}
    @GetMapping("/actions/{id}")
    public Map<String,Object> actionReceipt(@RequestHeader("Authorization")String auth,@PathVariable String id){return success(actions.get(identity.resolve(auth),id));}
    /** 撤回执行：仅限本人申请，复用原归属校验与 withdrawn 同步。 */
    @PostMapping("/job-applications/withdraw")
    @Log(module = "APPLICATION_MANAGEMENT", operationType = "DELETE", description = "AI助手确认后撤回职位申请")
    public Map<String,Object> withdraw(@RequestHeader("Authorization")String auth,@RequestBody JobApplyExecuteService.WithdrawRequest input){return success(executes.withdraw(identity.resolve(auth),input));}
    @GetMapping("/tasks/{id}")
    public Map<String,Object> task(@RequestHeader("Authorization")String auth,@PathVariable String id){return success(tasks.get(identity.resolve(auth),id));}
    @PostMapping("/tasks/{id}/cancel")
    public Map<String,Object> cancel(@RequestHeader("Authorization")String auth,@PathVariable String id){return success(tasks.cancel(identity.resolve(auth),id));}
    /** 会话历史列表（仅本人）。 */
    @GetMapping("/sessions")
    public Map<String,Object> sessions(@RequestHeader("Authorization")String auth,@RequestParam(required=false)Integer page,@RequestParam(required=false)Integer size){Actor actor=identity.resolve(auth);return success(page==null&&size==null?tasks.sessions(actor):tasks.sessionPage(actor,page==null?1:page,size==null?20:size));}
    @PostMapping("/sessions")
    public Map<String,Object> newSession(@RequestHeader("Authorization")String auth,@RequestBody(required=false)Map<String,Object> body){return success(tasks.createSession(identity.resolve(auth),body==null?null:Objects.toString(body.get("title"),null)));}
    @GetMapping("/requests/{id}")
    public Map<String,Object> requestStatus(@RequestHeader("Authorization")String auth,@PathVariable String id){return success(tasks.requestStatus(identity.resolve(auth),id));}
    @GetMapping("/draft")
    public Map<String,Object> draft(@RequestHeader("Authorization")String auth,@RequestParam String draftKey){return success(tasks.draft(identity.resolve(auth),draftKey));}
    @PutMapping("/draft")
    public Map<String,Object> draftSave(@RequestHeader("Authorization")String auth,@RequestBody DraftRequest input){return success(tasks.saveDraft(identity.resolve(auth),input));}
    @GetMapping("/sessions/{id}/events")
    public Map<String,Object> sessionEvents(@RequestHeader("Authorization")String auth,@PathVariable String id){return success(tasks.events(identity.resolve(auth),id));}
    @PostMapping("/sessions/{id}/events")
    public Map<String,Object> sessionEvent(@RequestHeader("Authorization")String auth,@PathVariable String id,@RequestBody TimelineEvent input){return success(tasks.event(identity.resolve(auth),id,input));}
    /** 重命名会话：只改标题。 */
    @PatchMapping("/sessions/{id}")
    public Map<String,Object> renameSession(@RequestHeader("Authorization")String auth,@PathVariable String id,@RequestBody Map<String,Object> body){
        return success(tasks.renameSession(identity.resolve(auth),id,Objects.toString(body.get("title"),null)));
    }
    /** 归档/恢复会话：只影响历史列表展示。 */
    @PostMapping("/sessions/{id}/archive")
    public Map<String,Object> archiveSession(@RequestHeader("Authorization")String auth,@PathVariable String id,@RequestBody Map<String,Object> body){
        Object archived=body.get("archived");
        return success(tasks.archiveSession(identity.resolve(auth),id,archived==null||Boolean.parseBoolean(String.valueOf(archived))));
    }
    /** 删除会话：只删除会话记录，不级联删除业务记录或审计。 */
    @DeleteMapping("/sessions/{id}")
    public Map<String,Object> deleteSession(@RequestHeader("Authorization")String auth,@PathVariable String id){return success(tasks.deleteSession(identity.resolve(auth),id));}
    @GetMapping("/sessions/{id}/tasks")
    public Map<String,Object> history(@RequestHeader("Authorization")String auth,@PathVariable String id,@RequestParam(required=false)String before,@RequestParam(required=false)Integer size){Actor actor=identity.resolve(auth);return success(before==null&&size==null?tasks.history(actor,id):tasks.historyPage(actor,id,before,size==null?30:size));}

    /** Full versioned snapshots are resumable without keeping a model connection alive. */
    @GetMapping(value="/tasks/{id}/events",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@RequestHeader("Authorization")String auth,@PathVariable String id,
                             @RequestParam(defaultValue="0")long after){
        Actor actor=identity.resolve(auth);tasks.get(actor,id);
        if(!connections.tryAcquire())throw new AgentException("BUSY","连接较多，请稍后重连。",429);
        SseEmitter emitter=new SseEmitter(110_000L);
        java.util.concurrent.atomic.AtomicBoolean closed=new java.util.concurrent.atomic.AtomicBoolean();
        emitter.onCompletion(()->closed.set(true));emitter.onTimeout(()->closed.set(true));emitter.onError(e->closed.set(true));
        streams.execute(()->{
            long version=after,lastAuth=0;Actor current=actor;
            try{
                while(!closed.get()){
                    long now=System.currentTimeMillis();
                    if(now-lastAuth>5000){current=identity.resolve(auth);tasks.get(current,id);lastAuth=now;}
                    Task task=store.task(id);
                    if(task.getVersion()>version){task=tasks.get(current,id);emitter.send(SseEmitter.event().id(Long.toString(task.getVersion())).name("snapshot").data(task));version=task.getVersion();}
                    if(task.terminal()){emitter.send(SseEmitter.event().name("end").data(Map.of("taskId",id,"version",version)));break;}
                    Thread.sleep(400);
                }
            }catch(AgentException e){try{emitter.send(SseEmitter.event().name("agent-error").data(Map.of("code",e.getCode(),"message",e.getMessage())));}catch(Exception ignored){}}
            catch(Exception ignored){/* Client disconnection never cancels the underlying read task. */}
            finally{closed.set(true);connections.release();emitter.complete();}
        });
        return emitter;
    }
    @ExceptionHandler(AgentException.class)
    public ResponseEntity<Map<String,Object>> error(AgentException e){return ResponseEntity.status(e.getHttpStatus()).body(Map.of("code",e.getHttpStatus(),"errorCode",e.getCode(),"message",e.getMessage()));}
    private Map<String,Object> success(Object value){return Map.of("code",200,"data",value);}
    @PreDestroy public void close(){streams.shutdownNow();}
}
