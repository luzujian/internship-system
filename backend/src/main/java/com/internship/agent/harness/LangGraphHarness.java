package com.internship.agent.harness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.agent.business.*;
import com.internship.agent.business.AgentReadTools.Spec;
import com.internship.agent.business.AgentTypes.Session;
import com.internship.agent.business.AgentTypes.Task;
import com.internship.service.AIModelService;
import com.internship.util.AiEndpointSupport;
import jakarta.annotation.PreDestroy;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Private LangGraph transport. Provider credentials stay in Java, leases expire after each turn. */
@Service
public class LangGraphHarness implements BusinessAgentRuntime {
    private final ObjectMapper json;
    private final Environment environment;
    private final AIModelService models;
    private final HarnessToolBridge bridge;
    private final HarnessModelGateway gateway;
    private final HttpClient http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(3)).build();
    private final Map<String,Runnable> revocations=new ConcurrentHashMap<>();
    public LangGraphHarness(ObjectMapper json,Environment environment,AIModelService models,HarnessToolBridge bridge,HarnessModelGateway gateway){
        this.json=json;this.environment=environment;this.models=models;this.bridge=bridge;this.gateway=gateway;
    }
    public String engine(){return "langgraph";}
    public boolean enabled(){return "langgraph".equals(environment.getProperty("agent.harness","legacy"));}
    public Result run(Session session,Task task,String contextKey,String system,List<Spec> specs,
                      HarnessToolBridge.Executor tools,Runnable checkpoint,Runnable persist)throws Exception {
        if(!enabled())throw unavailable();
        String key=serviceKey();
        checkpoint.run();
        var row=models.findDefaultModel();
        if(row==null || !Integer.valueOf(1).equals(row.getStatus()))throw new AgentException("MODEL_UNAVAILABLE","请先启用并设置系统默认 AI 模型。",503);
        var settings=AgentModelSettings.resolve(row,environment.getProperty("agent.model"));
        var connection=AiEndpointSupport.resolveConnection(settings.endpoint(),settings.apiKey(),environment.getProperty("spring.ai.deepseek.base-url"),environment.getProperty("spring.ai.deepseek.api-key"));
        boolean fresh=task.getEditOf()!=null || session.getRuntime()==null || !engine().equals(session.getRuntime().get("engine"));
        Map<String,Object> state=fresh?new LinkedHashMap<>():new LinkedHashMap<>(session.getRuntime());
        if(fresh){state.put("threadId",UUID.randomUUID().toString());state.put("schemaVersion",1);state.put("engine",engine());}
        long generation=((Number)state.getOrDefault("generation",0L)).longValue()+1;
        state.put("generation",generation);state.put("contextKey",contextKey);
        session.setRuntime(state);task.setRuntime(new LinkedHashMap<>(state));persist.run();
        try(var admission=gateway.acquireUser(task.getOwnerKey());var binding=bridge.register(specs);var modelBinding=gateway.register(task.getOwnerKey(),settings,connection)){
            binding.activate(specs,tools);modelBinding.activate(checkpoint);
            Runnable revoke=()->{binding.deactivate();modelBinding.deactivate();};revocations.put(task.getId(),revoke);
            try{
                Map<String,Object> input=new LinkedHashMap<>();
                input.put("runId",task.getId());input.put("sessionId",session.getId());input.put("ownerKey",task.getOwnerKey());
                input.put("threadId",state.get("threadId"));input.put("generation",generation);input.put("contextKey",contextKey);
                input.put("system",system);input.put("message",task.getMessage());input.put("fresh",fresh);input.put("runtime",safeState(json.valueToTree(state)));
                input.put("tools",specs.stream().map(spec->{try{return Map.of("name",spec.name(),"description",spec.description(),"schema",json.readTree(spec.schema()));}catch(Exception e){throw new IllegalArgumentException("Invalid tool schema",e);}}).toList());
                input.put("model",Map.of("modelCode",settings.modelCode(),"temperature",settings.temperature(),"maxTokens",settings.maxTokens(),"endpoint",gateway.endpoint(),"lease",modelBinding.key()));
                input.put("toolEndpoint",bridge.endpoint());input.put("toolLease",binding.key());
                // Never resubmit a turn after a lost POST response. Query its stable runId instead.
                try{request("POST","/v1/runs",input,key);}catch(java.io.IOException lost){checkpoint.run();}
                boolean recovered=false;
                long nextPersist=System.currentTimeMillis()+1500;
                while(true){
                    checkpoint.run();if(binding.fatal()!=null)throw binding.fatal();
                    JsonNode result=request("GET","/v1/runs/"+task.getId(),null,key);
                    String status=result.path("status").asText();
                    if(Set.of("SUCCEEDED","WAITING_INPUT").contains(status)){
                        if(result.has("runtime")){
                            Map<String,Object> safe=safeState(result.get("runtime"));
                            if(safe.containsKey("threadId")&&!Objects.equals(state.get("threadId"),safe.get("threadId")))throw unavailable();
                            state.putAll(safe);if(!safe.containsKey("pendingQuestion"))state.remove("pendingQuestion");
                            String intended=json.valueToTree(input.get("runtime")).path("pendingCancellation").path("runId").asText("");
                            String acknowledged=result.path("runtime").path("reconciledCancellationRunId").asText("");
                            if(!intended.isBlank()&&intended.equals(acknowledged)){
                                task.setReconciledCancellationRunId(intended);
                                if(intended.equals(json.valueToTree(state.get("pendingCancellation")).path("runId").asText("")))state.remove("pendingCancellation");
                            }
                            session.setRuntime(state);task.setRuntime(new LinkedHashMap<>(state));persist.run();
                        }
                        String answer=result.path("answer").asText("").trim();
                        boolean waiting="WAITING_INPUT".equals(status);
                        if(answer.isEmpty()&&waiting)answer=json.valueToTree(state.get("pendingQuestion")).path("question").asText();
                        if(answer.isBlank())throw unavailable();
                        return new Result(answer,waiting,result.path("modelCalls").asLong(),result.path("inputTokens").asLong(),result.path("outputTokens").asLong());
                    }
                    if("CANCELLED".equals(status))throw new AgentException("CANCELLED","本次处理已停止，已完成的业务以回执为准。",409);
                    if("FAILED".equals(status)){
                        String code=result.path("errorCode").asText("HARNESS_UNAVAILABLE");
                        if("RUNTIME_INTERRUPTED".equals(code)&&!recovered){
                            // Only an authoritative interrupted checkpoint permits replay of
                            // this exact request. Unknown business outcomes are never retried.
                            recovered=true;checkpoint.run();
                            try{request("POST","/v1/runs",input,key);}catch(java.io.IOException lost){checkpoint.run();}
                            continue;
                        }
                        if("RUNTIME_INTERRUPTED".equals(code))code="TASK_INTERRUPTED";
                        if(!Set.of("MODEL_UNAVAILABLE","MODEL_BALANCE_LOW","BUDGET_EXCEEDED","TASK_INTERRUPTED","ACTION_UNKNOWN","FORBIDDEN","CAPABILITY_DISABLED","SCOPE_CHANGED").contains(code))code="HARNESS_UNAVAILABLE";
                        throw new AgentException(code,"TASK_INTERRUPTED".equals(code)?"执行服务已重启，任务约束和已完成操作保留，请继续回复。":"这次处理未完成，文件和已完成操作保留，请稍后继续。",503);
                    }
                    if(!Set.of("QUEUED","RUNNING").contains(status))throw unavailable();
                    if(System.currentTimeMillis()>=nextPersist){persist.run();nextPersist=System.currentTimeMillis()+1500;}
                    Thread.sleep(250);
                }
            }finally{
                revoke.run();revocations.remove(task.getId(),revoke);
                // Transport failures and service restarts are not the user's
                // cancellation decision. Leave durable checkpoints recoverable.
            }
        }
    }
    public void cancel(Session session,Task task){
        Runnable revoke=revocations.get(task.getId());if(revoke!=null)revoke.run();
        Map<String,Object> state=session.getRuntime();if(state==null || !engine().equals(state.get("engine")))return;
        Map<String,Object> body=new LinkedHashMap<>();body.put("threadId",state.get("threadId"));
        body.put("generation",state.getOrDefault("generation",0));
        if(task.getRuntime()!=null){
            if(!Objects.equals(state.get("threadId"),task.getRuntime().get("threadId")))throw new AgentException("QUESTION_CHANGED","旧追问的会话已变化，当前会话不会被取消。",409);
            Object question=task.getRuntime().get("pendingQuestion");
            if(question!=null){JsonNode original=json.valueToTree(question);body.put("questionId",original.path("id").asText());body.put("workflowId",original.path("workflowId").asText());body.put("revision",original.path("revision").asLong());}
        }
        try{request("POST","/v1/runs/"+task.getId()+"/cancel",body,serviceKey());}catch(Exception e){throw unavailable();}
    }
    public void deleteConversation(Session session){
        if(session.getRuntime()==null || !engine().equals(session.getRuntime().get("engine")))return;
        Object id=session.getRuntime().get("threadId");if(id==null)return;
        try{request("DELETE","/v1/threads/"+id,null,serviceKey());}catch(Exception e){throw unavailable();}
    }
    private Map<String,Object> safeState(JsonNode state){
        Map<String,Object> result=new LinkedHashMap<>();
        for(String name:List.of("threadId","conversationThreadId","schemaVersion","generation","workflowId","revision","checkpointId","pendingQuestion","pendingCancellation"))if(state.has(name)&&!state.get(name).isNull())result.put(name,json.convertValue(state.get(name),Object.class));
        if(json.valueToTree(result).toString().length()>6000)throw unavailable();
        return result;
    }
    private JsonNode request(String method,String path,Object body,String key)throws Exception {
        String root=environment.getProperty("agent.langgraph.url","http://127.0.0.1:8791").replaceAll("/+$","");
        URI uri=URI.create(root+path);
        if(!Set.of("http","https").contains(uri.getScheme())||uri.getUserInfo()!=null)throw unavailable();
        var builder=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).header("Authorization","Bearer "+key).header("Content-Type","application/json");
        builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body),StandardCharsets.UTF_8));
        var response=http.send(builder.build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if(response.statusCode()==429)throw new AgentException("BUSY","助手正在处理较多任务，请稍后重试。",429);
        if(response.statusCode()<200||response.statusCode()>299)throw unavailable();
        return response.body().isBlank()?json.createObjectNode():json.readTree(response.body());
    }
    private String serviceKey(){String key=environment.getProperty("agent.langgraph.service-key","");if(key.length()<32)throw unavailable();return key;}
    private static AgentException unavailable(){return new AgentException("HARNESS_UNAVAILABLE","对话执行服务暂时不可用，文件和聊天记录已保留。",503);}
    @PreDestroy public void close(){revocations.values().forEach(Runnable::run);http.close();}
}
