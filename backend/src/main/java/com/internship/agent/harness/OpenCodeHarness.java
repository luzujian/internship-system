package com.internship.agent.harness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.agent.business.*;
import com.internship.entity.AIModel;
import com.internship.service.AIModelService;
import com.internship.util.AiEndpointSupport;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static com.internship.agent.business.AgentReadTools.Spec;

/** OpenCode owns the agent loop, message/tool history, questions and context compaction.
 * The application owns identity, tool implementations, execution limits and business receipts. */
@Service
public class OpenCodeHarness {
    @Data public static class Dialogue {
        private String sessionId,questionId,contextKey;
        private List<Map<String,Object>> questions=new ArrayList<>();
    }
    public record Result(String answer,boolean waiting,long modelCalls,long inputTokens,long outputTokens) {}
    private final ObjectMapper json;
    private final Environment environment;
    private final AIModelService models;
    private final HarnessToolBridge bridge;
    private final HarnessModelGateway gateway;
    private final Map<String,Instance> instances=new HashMap<>();
    private final HttpClient http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(3)).build();
    private final ScheduledExecutorService cleanup=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon(true).name("opencode-idle-cleanup").factory());
    public OpenCodeHarness(ObjectMapper json,Environment environment,AIModelService models,HarnessToolBridge bridge,HarnessModelGateway gateway) {
        this.json=json;this.environment=environment;this.models=models;this.bridge=bridge;this.gateway=gateway;
        cleanup.scheduleWithFixedDelay(this::expireIdle,60,60,TimeUnit.SECONDS);
    }
    public boolean enabled(){return "opencode".equals(environment.getProperty("agent.harness","legacy"));}
    public Result run(String namespace,String owner,Dialogue dialogue,String contextKey,String system,String message,
                      List<Spec> specs,HarnessToolBridge.Executor tools,Runnable checkpoint,Runnable persist) throws Exception {
        if(!enabled())throw unavailable("OpenCode Agent 尚未启用。");
        try(var admission=gateway.acquireUser(owner)) {
        checkpoint.run();
        Instance instance=instance(namespace,owner,specs);
        synchronized(instance) {
            try {
                instance.binding.activate(specs,tools);
                instance.modelBinding.activate(checkpoint);
                checkpoint.run();
                String signature=specs.toString();
                if(instance.toolSignature!=null && !instance.toolSignature.equals(signature)) {
                    instance.request("POST","/mcp/business/disconnect",Map.of());
                    instance.request("POST","/mcp/business/connect",Map.of());
                }
                instance.toolSignature=signature;
                if(dialogue.getSessionId()==null || !instance.exists(dialogue.getSessionId())) {
                    dialogue.setSessionId(instance.request("POST","/session",Map.of("title","DeepIntern")).path("id").asText());
                    dialogue.setQuestionId(null);dialogue.setQuestions(new ArrayList<>());persist.run();
                }
                String session=dialogue.getSessionId();
                Set<String> previous=messageIds(instance,session);
                JsonNode pending=question(instance,session);
                // Page/file changes start a new normal turn; an old question cannot govern a different workspace.
                if(pending!=null && !Objects.equals(dialogue.getContextKey(),contextKey)) {
                    instance.request("POST","/session/"+session+"/abort",Map.of());
                    // Abort does not remove a suspended question in this OpenCode version. Reject it explicitly.
                    try{instance.request("POST","/question/"+pending.path("id").asText()+"/reject",Map.of());}catch(AgentException ignored){}
                    long releaseDeadline=System.currentTimeMillis()+3000;
                    while(question(instance,session)!=null && System.currentTimeMillis()<releaseDeadline){checkpoint.run();Thread.sleep(50);}
                    if(question(instance,session)!=null)throw unavailable("上一轮追问尚未结束，附件已保留，请稍后继续。");
                    pending=null;dialogue.setQuestionId(null);dialogue.setQuestions(new ArrayList<>());
                }
                dialogue.setContextKey(contextKey);persist.run();
                if(pending!=null) {
                    List<List<String>> answers=new ArrayList<>();
                    pending.path("questions").forEach(q->answers.add(List.of(message)));
                    instance.request("POST","/question/"+pending.path("id").asText()+"/reply",Map.of("answers",answers));
                    dialogue.setQuestionId(null);dialogue.setQuestions(new ArrayList<>());persist.run();
                } else {
                    // A restarted harness may have lost a pending in-memory question. Its durable transcript and
                    // our saved question plus the user's answer provide context without replaying a business call.
                    String reply=dialogue.getQuestionId()==null?message:
                            "上一轮等待你回答："+json.writeValueAsString(dialogue.getQuestions())+"\n本轮用户回答："+message;
                    dialogue.setQuestionId(null);dialogue.setQuestions(new ArrayList<>());persist.run();
                    instance.request("POST","/session/"+session+"/prompt_async",Map.of("agent","deepintern","system",system,
                            "parts",List.of(Map.of("type","text","text",reply))));
                }
                long deadline=System.currentTimeMillis()+environment.getProperty("agent.opencode.turn-timeout-ms",Long.class,90000L);
                while(System.currentTimeMillis()<deadline) {
                    checkpoint.run();
                    if(instance.binding.fatal()!=null)throw instance.binding.fatal();
                    JsonNode question=question(instance,session);
                    if(question!=null) {
                        dialogue.setQuestionId(question.path("id").asText());
                        dialogue.setQuestions(json.convertValue(question.path("questions"),new com.fasterxml.jackson.core.type.TypeReference<>(){}));persist.run();
                        return result(instance,session,previous,questionText(question),true);
                    }
                    JsonNode messages=instance.request("GET","/session/"+session+"/message",null);
                    JsonNode last=lastAssistant(messages,previous);
                    if(last!=null && last.path("info").has("error"))throw unavailable("OpenCode 的模型请求未完成，请检查当前模型配置后继续。");
                    JsonNode status=instance.request("GET","/session/status",null).path(session).path("type");
                    if(last!=null && last.path("info").path("time").has("completed") && !"busy".equals(status.asText()) && !"retry".equals(status.asText())) {
                        String answer=answer(last);
                        if(answer.isBlank())throw unavailable("OpenCode 未返回完整回答，文件和聊天仍保留。");
                        return result(instance,session,previous,answer,false);
                    }
                    Thread.sleep(350);
                }
                throw new AgentException("BUDGET_EXCEEDED","本轮处理用时较长，已取得的结果保留，请继续回复。",422);
            } catch(Exception e) {
                // Revoke the tool lease before aborting: late model calls can no longer execute application tools.
                instance.binding.deactivate();
                instance.modelBinding.deactivate();
                try { if(dialogue.getSessionId()!=null)instance.request("POST","/session/"+dialogue.getSessionId()+"/abort",Map.of()); } catch(Exception ignored){}
                // An interrupted transport cannot prove that the native loop stopped. Recreate it from
                // durable history on the next turn, so delayed calls cannot enter a new tool lease.
                instance.close();
                throw e;
            } finally { instance.binding.deactivate();instance.modelBinding.deactivate();instance.active=false;instance.lastUsed=System.currentTimeMillis(); }
        }
        }
    }
    private synchronized Instance instance(String namespace,String owner,List<Spec> specs) throws Exception {
        UUID.fromString(namespace);
        AIModel row=models.findDefaultModel();
        if(row==null || !Integer.valueOf(1).equals(row.getStatus()))throw unavailable("请先启用并设置系统默认 AI 模型。");
        AgentModelSettings.Resolved settings=AgentModelSettings.resolve(row,environment.getProperty("agent.model"));
        var connection=AiEndpointSupport.resolveConnection(settings.endpoint(),settings.apiKey(),
                environment.getProperty("spring.ai.deepseek.base-url"),environment.getProperty("spring.ai.deepseek.api-key"));
        Instance old=instances.get(namespace);
        if(old!=null && !owner.equals(old.owner))throw AgentIdentityService.forbidden();
        if(old!=null && old.active)throw new AgentException("BUSY","该会话仍在执行，请稍后继续。",429);
        if(old!=null && old.process.isAlive() && old.settings.equals(settings) && old.connection.equals(connection)){
            old.active=true;old.lastUsed=System.currentTimeMillis();return old;
        }
        if(old!=null){old.close();instances.remove(namespace);}
        expireIdle();
        if(instances.size()>=environment.getProperty("agent.opencode.max-sessions",Integer.class,4)) {
            var idle=instances.entrySet().stream().filter(entry->!entry.getValue().active).min(Comparator.comparingLong(entry->entry.getValue().lastUsed));
            if(idle.isEmpty())throw new AgentException("BUSY","Agent 正在处理较多会话，请稍后重试。",429);
            idle.get().getValue().close();instances.remove(idle.get().getKey());
        }
        Path root=Path.of(environment.getProperty("agent.opencode.work-dir","uploads/agent-runtime")).toAbsolutePath().normalize();
        Path directory=root.resolve(namespace).normalize();
        if(!directory.startsWith(root))throw AgentIdentityService.forbidden();
        Files.createDirectories(directory);
        Path script=directory.resolve("mcp-bridge.mjs");
        try(var resource=Objects.requireNonNull(getClass().getResourceAsStream("/agent/harness/mcp-bridge.mjs"))){Files.copy(resource,script,StandardCopyOption.REPLACE_EXISTING);}
        var binding=bridge.register(specs);
        var modelBinding=gateway.register(owner,settings,connection);
        int port;try(var socket=new java.net.ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){port=socket.getLocalPort();}
        String password=UUID.randomUUID().toString()+UUID.randomUUID();
        Map<String,Object> configuration=config(settings,connection,script,binding,modelBinding);
        ProcessBuilder builder=new ProcessBuilder(environment.getProperty("agent.opencode.executable","opencode"),"serve","--hostname","127.0.0.1","--port",String.valueOf(port));
        builder.directory(directory.toFile()).redirectErrorStream(true).redirectOutput(directory.resolve("runtime.log").toFile());
        var env=builder.environment();
        env.remove("OPENCODE_CONFIG");env.remove("OPENCODE_PERMISSION");
        for(String name:List.of("CONFIG","DATA","CACHE","STATE"))env.put("XDG_"+name+"_HOME",directory.resolve(name.toLowerCase(Locale.ROOT)).toString());
        env.put("OPENCODE_CONFIG_DIR",directory.resolve("config/opencode").toString());
        env.put("OPENCODE_CONFIG_CONTENT",json.writeValueAsString(configuration));env.put("OPENCODE_SERVER_PASSWORD",password);env.put("OPENCODE_SERVER_USERNAME","opencode");
        for(String flag:List.of("AUTOUPDATE","SHARE","DEFAULT_PLUGINS","EXTERNAL_SKILLS","CLAUDE_CODE","PROJECT_CONFIG"))env.put("OPENCODE_DISABLE_"+flag,"1");
        Instance created=null;
        try {
            created=new Instance(owner,settings,connection,binding,modelBinding,builder.start(),"http://127.0.0.1:"+port,password);
            long deadline=System.currentTimeMillis()+30000;
            while(System.currentTimeMillis()<deadline) {
                if(!created.process.isAlive())throw unavailable("OpenCode 服务未启动，请检查安装路径和运行环境。");
                JsonNode health=null;
                try {health=created.request("GET","/global/health",null);}catch(Exception ignored){}
                if(health!=null && health.path("healthy").asBoolean()) {
                    if(!environment.getProperty("agent.opencode.version","1.18.35").equals(health.path("version").asText()))
                        throw unavailable("OpenCode 版本与已验证的版本不一致，请使用配置指定的版本。");
                    // Claim under the same lock used by idle eviction, before another caller can reclaim it.
                    created.active=true;created.lastUsed=System.currentTimeMillis();instances.put(namespace,created);return created;
                }
                Thread.sleep(250);
            }
            throw unavailable("OpenCode 服务启动超时，请检查运行环境。");
        } catch(Exception e) { if(created!=null)created.close();else {binding.close();modelBinding.close();}throw e; }
    }
    Map<String,Object> config(AgentModelSettings.Resolved settings,AiEndpointSupport.Connection connection,Path script,HarnessToolBridge.Binding binding,HarnessModelGateway.Binding modelBinding) {
        Map<String,Object> permission=new LinkedHashMap<>();permission.put("*","deny");permission.put("question","allow");permission.put("business_*","allow");
        Map<String,Object> options=new LinkedHashMap<>();options.put("baseURL",gateway.endpoint());options.put("apiKey",modelBinding.key());options.put("timeout",90000);
        Map<String,Object> model=new LinkedHashMap<>();model.put("name",settings.modelCode());model.put("tool_call",true);
        model.put("limit",Map.of("context",128000,"output",settings.maxTokens()));model.put("headers",AiEndpointSupport.extraHeaders(connection.endpoint(),settings.modelCode()));
        Map<String,Object> config=new LinkedHashMap<>();
        config.put("autoupdate",false);config.put("share","disabled");config.put("permission",permission);config.put("default_agent","deepintern");
        config.put("snapshot",false);config.put("enabled_providers",List.of("deepintern"));
        config.put("model","deepintern/"+settings.modelCode());
        config.put("provider",Map.of("deepintern",Map.of("npm","@ai-sdk/openai-compatible","name","DeepIntern 系统模型","options",options,"models",Map.of(settings.modelCode(),model))));
        // An explicit business prompt replaces OpenCode's default coding prompt; per-turn rules stay authoritative.
        String prompt="你是 DeepIntern 实习管理系统的业务助手。遵循本轮系统提供的身份、权限、页面上下文和业务规则；实际操作只能通过授权的业务工具完成。当前数据和操作结果以本轮工具回执为准，不得根据历史对话猜测。信息不完整或用户要求有歧义时，用 question 追问并等待答复，不得擅自补全或覆盖资料。用简明中文回答，仅报告已验证的结果。";
        config.put("agent",Map.of("deepintern",Map.of("mode","primary","description","DeepIntern 业务 Agent","prompt",prompt,"temperature",settings.temperature(),"steps",8,"permission",permission),
                "build",Map.of("disable",true),"plan",Map.of("disable",true),"general",Map.of("disable",true),"explore",Map.of("disable",true)));
        config.put("mcp",Map.of("business",Map.of("type","local","command",List.of(environment.getProperty("agent.opencode.node","node"),script.toString()),
                "environment",Map.of("DEEPINTERN_TOOL_ENDPOINT",bridge.endpoint(),"DEEPINTERN_TOOL_LEASE",binding.key()))));
        return config;
    }
    private JsonNode question(Instance instance,String session) throws Exception {
        for(JsonNode question:instance.request("GET","/question",null))if(session.equals(question.path("sessionID").asText()))return question;
        return null;
    }
    static String questionText(JsonNode request) {
        List<String> questions=new ArrayList<>();
        for(JsonNode question:request.path("questions")) {
            String text=question.path("question").asText();
            if(question.path("options").size()>1) {
                List<String> choices=new ArrayList<>();question.path("options").forEach(o->choices.add(o.path("label").asText()));
                text+="\n可选："+String.join("；",choices)+"。也可以直接说明你的要求。";
            }
            questions.add(text);
        }
        return String.join("\n\n",questions);
    }
    private Set<String> messageIds(Instance instance,String session) throws Exception {
        Set<String> ids=new HashSet<>();instance.request("GET","/session/"+session+"/message",null).forEach(m->ids.add(m.path("info").path("id").asText()));return ids;
    }
    static JsonNode lastAssistant(JsonNode messages,Set<String> previous) {
        JsonNode last=null;
        for(JsonNode message:messages)if("assistant".equals(message.path("info").path("role").asText()) && !previous.contains(message.path("info").path("id").asText()))last=message;
        return last;
    }
    private static String answer(JsonNode message) {
        List<String> parts=new ArrayList<>();message.path("parts").forEach(part->{if("text".equals(part.path("type").asText()) && !part.path("synthetic").asBoolean())parts.add(part.path("text").asText());});
        return String.join("\n",parts);
    }
    private Result result(Instance instance,String session,Set<String> previous,String answer,boolean waiting) throws Exception {
        long calls=0,input=0,output=0;
        for(JsonNode message:instance.request("GET","/session/"+session+"/message",null)) {
            JsonNode info=message.path("info");
            if(!"assistant".equals(info.path("role").asText()) || previous.contains(info.path("id").asText()))continue;
            calls++;input+=info.path("tokens").path("input").asLong();output+=info.path("tokens").path("output").asLong();
        }
        return new Result(answer,waiting,calls,input,output);
    }
    private final class Instance implements AutoCloseable {
        final String owner,baseUrl,basic;
        final AgentModelSettings.Resolved settings;
        final AiEndpointSupport.Connection connection;
        final HarnessToolBridge.Binding binding;
        final HarnessModelGateway.Binding modelBinding;
        final Process process;
        volatile boolean active;
        String toolSignature;
        volatile long lastUsed=System.currentTimeMillis();
        Instance(String owner,AgentModelSettings.Resolved settings,AiEndpointSupport.Connection connection,HarnessToolBridge.Binding binding,HarnessModelGateway.Binding modelBinding,Process process,String baseUrl,String password) {
            this.owner=owner;this.settings=settings;this.connection=connection;this.binding=binding;this.modelBinding=modelBinding;this.process=process;this.baseUrl=baseUrl;
            basic="Basic "+Base64.getEncoder().encodeToString(("opencode:"+password).getBytes(StandardCharsets.UTF_8));
        }
        JsonNode request(String method,String path,Object body) throws Exception {
            // Cold tool initialization can briefly block native reads on a CPU-limited host.
            // Thread interruption still cancels these reads; startup and abort keep their short timeout.
            long timeout=path.equals("/session") && "POST".equals(method)?45:
                    "GET".equals(method) && !"/global/health".equals(path)?15:4;
            var request=HttpRequest.newBuilder(URI.create(baseUrl+path)).header("Authorization",basic).header("Content-Type","application/json").timeout(Duration.ofSeconds(timeout));
            request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            HttpResponse<String> response;
            try {response=http.send(request.build(),HttpResponse.BodyHandlers.ofString());}
            catch(java.io.IOException e){throw unavailable("OpenCode 连接未完成，请检查运行环境后继续，文件和聊天仍保留。");}
            if(response.statusCode()==404 && path.startsWith("/session/"))throw new AgentException("HARNESS_SESSION_MISSING","会话运行记录需要恢复。",404);
            if(response.statusCode()<200 || response.statusCode()>299)throw unavailable("OpenCode 通信暂时不可用，已完成的业务以系统回执为准。");
            return response.body().isBlank()?json.createObjectNode():json.readTree(response.body());
        }
        boolean exists(String session)throws Exception {try{request("GET","/session/"+session,null);return true;}catch(AgentException e){if("HARNESS_SESSION_MISSING".equals(e.getCode()))return false;throw e;}}
        @Override public void close(){
            binding.close();modelBinding.close();process.descendants().forEach(ProcessHandle::destroy);process.destroy();
            try{if(!process.waitFor(3,TimeUnit.SECONDS))process.destroyForcibly();}catch(InterruptedException e){process.destroyForcibly();Thread.currentThread().interrupt();}
        }
    }
    /** Called only after the application verifies ownership and holds the session deletion lock. */
    public synchronized void deleteConversation(String namespace,String owner) {
        UUID.fromString(namespace);
        Instance instance=instances.get(namespace);
        if(instance!=null) {
            if(!owner.equals(instance.owner))throw AgentIdentityService.forbidden();
            if(instance.active)throw new AgentException("TASK_RUNNING","该会话仍在执行，请稍后再删除。",409);
            instance.close();instances.remove(namespace);
        }
        Path root=Path.of(environment.getProperty("agent.opencode.work-dir","uploads/agent-runtime")).toAbsolutePath().normalize();
        Path directory=root.resolve(namespace).normalize();
        if(!directory.startsWith(root)||directory.equals(root))throw AgentIdentityService.forbidden();
        if(!Files.exists(directory,LinkOption.NOFOLLOW_LINKS))return;
        try(var paths=Files.walk(directory)) {
            for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);
        }catch(java.io.IOException e){throw unavailable("OpenCode 会话文件暂时无法删除，请稍后重试。");}
    }
    private synchronized void expireIdle() {
        long cutoff=System.currentTimeMillis()-environment.getProperty("agent.opencode.idle-timeout-ms",Long.class,1800000L);
        var iterator=instances.entrySet().iterator();
        while(iterator.hasNext()){Instance instance=iterator.next().getValue();if(!instance.active && instance.lastUsed<cutoff){instance.close();iterator.remove();}}
    }
    private static AgentException unavailable(String message){return new AgentException("HARNESS_UNAVAILABLE",message,503);}
    @PreDestroy void close(){cleanup.shutdownNow();synchronized(this){instances.values().forEach(Instance::close);instances.clear();}}
}
