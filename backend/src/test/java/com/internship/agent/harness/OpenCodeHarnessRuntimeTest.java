package com.internship.agent.harness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.agent.business.AgentException;
import com.internship.agent.business.AgentReadTools.Spec;
import com.internship.config.AiRuntimeProperties;
import com.internship.entity.AIModel;
import com.internship.service.AIModelService;
import com.internship.util.AiCallGuard;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real pinned OpenCode and MCP processes with a deterministic local provider; no application DB. */
@EnabledIfEnvironmentVariable(named="AGENT_OPENCODE_TEST_EXECUTABLE",matches=".+")
@Timeout(180)
class OpenCodeHarnessRuntimeTest {
    @TempDir Path directory;
    final ObjectMapper json=new ObjectMapper();
    final List<Spec> specs=List.of(new Spec("scope_info","Read the current authorized scope","{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}"));
    final ExecutorService workers=Executors.newVirtualThreadPerTaskExecutor();
    final AtomicInteger toolCalls=new AtomicInteger();
    final int capacity=Integer.parseInt(System.getenv().getOrDefault("AGENT_OPENCODE_TEST_MAX_SESSIONS","4"));
    HttpServer provider;HarnessToolBridge bridge;HarnessModelGateway gateway;OpenCodeHarness harness;
    AIModelService models;MockEnvironment environment;
    volatile Reply reply;
    @FunctionalInterface interface Reply {void send(HttpExchange exchange,JsonNode request)throws Exception;}

    @BeforeEach void start()throws Exception {
        provider=HttpServer.create(new InetSocketAddress("127.0.0.1",0),16);
        reply=this::scopeReply;
        provider.createContext("/v1/chat/completions",exchange->{
            try{reply.send(exchange,json.readTree(exchange.getRequestBody().readAllBytes()));}
            catch(Exception ignored){}finally{exchange.close();}
        });
        provider.setExecutor(workers);provider.start();
        AIModel model=new AIModel();model.setModelCode("fixture-model");model.setStatus(1);model.setMaxTokens(2200);
        model.setApiEndpoint("http://127.0.0.1:"+provider.getAddress().getPort()+"/v1");model.setApiKey("local-fixture-key");
        models=mock(AIModelService.class);when(models.findDefaultModel()).thenReturn(model);
        var limits=new AiRuntimeProperties();limits.setMaxUserRequestsPerMinute(100);limits.setMaxRequestsPerMinute(1000);
        bridge=new HarnessToolBridge(json);gateway=new HarnessModelGateway(json,new AiCallGuard(limits),limits);
        environment=new MockEnvironment().withProperty("agent.harness","opencode")
                .withProperty("agent.opencode.executable",System.getenv("AGENT_OPENCODE_TEST_EXECUTABLE"))
                .withProperty("agent.opencode.node",Objects.toString(System.getenv("AGENT_OPENCODE_TEST_NODE"),"node"))
                .withProperty("agent.opencode.max-sessions",String.valueOf(capacity))
                .withProperty("agent.opencode.work-dir",directory.toString());
        harness=new OpenCodeHarness(json,environment,models,bridge,gateway);
    }
    @AfterEach void close(){
        if(harness!=null)harness.close();if(bridge!=null)bridge.close();if(gateway!=null)gateway.close();
        if(provider!=null)provider.stop(0);workers.shutdownNow();
    }
    OpenCodeHarness.Result run(String namespace,String owner,OpenCodeHarness.Dialogue dialogue,String message)throws Exception {
        return harness.run(namespace,owner,dialogue,"same-page","Use the supplied tools to answer. Ask missing details with question.",message,specs,
                (name,args)->{toolCalls.incrementAndGet();return Map.of("scope",owner,"message",message);},()->{},()->{});
    }
    String lastUser(JsonNode request){
        String value="";for(JsonNode message:request.path("messages"))if("user".equals(message.path("role").asText()))value=content(message);
        return value;
    }
    String content(JsonNode message){
        JsonNode value=message.path("content");if(value.isTextual())return value.asText();
        StringBuilder text=new StringBuilder();value.forEach(part->text.append(part.path("text").asText()));return text.toString();
    }
    JsonNode last(JsonNode request){JsonNode messages=request.path("messages");return messages.get(messages.size()-1);}
    void scopeReply(HttpExchange exchange,JsonNode request)throws Exception {
        if("tool".equals(last(request).path("role").asText()))text(exchange,content(last(request)));
        else scopeTool(exchange,request);
    }
    void scopeTool(HttpExchange exchange,JsonNode request)throws Exception {
        String name=null;
        for(JsonNode tool:request.path("tools"))if(tool.at("/function/name").asText().endsWith("scope_info"))name=tool.at("/function/name").asText();
        if(name==null)throw new IllegalStateException("Business MCP tool missing from provider request");
        tool(exchange,name,Map.of());
    }
    void text(HttpExchange exchange,String text)throws Exception {sse(exchange,Map.of("role","assistant","content",text),"stop");}
    void tool(HttpExchange exchange,String name,Object arguments)throws Exception {
        sse(exchange,Map.of("role","assistant","tool_calls",List.of(Map.of("index",0,"id","call_"+UUID.randomUUID(),"type","function",
                "function",Map.of("name",name,"arguments",json.writeValueAsString(arguments))))),"tool_calls");
    }
    void sse(HttpExchange exchange,Map<String,Object> delta,String finish)throws Exception {
        String id="chatcmpl-"+UUID.randomUUID();
        String chunk=json.writeValueAsString(Map.of("id",id,"object","chat.completion.chunk","created",System.currentTimeMillis()/1000,"model","fixture-model",
                "choices",List.of(Map.of("index",0,"delta",delta))));
        String done=json.writeValueAsString(Map.of("id",id,"object","chat.completion.chunk","created",System.currentTimeMillis()/1000,"model","fixture-model",
                "choices",List.of(Map.of("index",0,"delta",Map.of(),"finish_reason",finish)),"usage",Map.of("prompt_tokens",10,"completion_tokens",10,"total_tokens",20)));
        byte[] bytes=("data: "+chunk+"\n\ndata: "+done+"\n\ndata: [DONE]\n\n").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type","text/event-stream");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);
    }

    @Test void configuredActiveSessionsStayIsolatedAndCapacityRecoversAfterCompletion()throws Exception {
        assertTrue(capacity>=2 && capacity<=4,"Runtime fixture supports two to four concurrent sessions");
        var arrived=new CountDownLatch(capacity);var release=new CountDownLatch(1);
        reply=(exchange,request)->{
            if(!"tool".equals(last(request).path("role").asText())){arrived.countDown();if(!release.await(75,TimeUnit.SECONDS))throw new TimeoutException();}
            scopeReply(exchange,request);
        };
        var ids=new ArrayList<String>();var owners=List.of("administrator-scope","student-scope","teacher-scope","company-scope").subList(0,capacity);
        var dialogues=new ArrayList<OpenCodeHarness.Dialogue>();var futures=new ArrayList<Future<OpenCodeHarness.Result>>();
        for(String owner:owners){String id=UUID.randomUUID().toString();var dialogue=new OpenCodeHarness.Dialogue();ids.add(id);dialogues.add(dialogue);futures.add(workers.submit(()->run(id,owner,dialogue,"query "+owner)));}
        try {
            boolean allArrived=arrived.await(70,TimeUnit.SECONDS);
            if(!allArrived)for(var future:futures)if(future.isDone())future.get();
            assertTrue(allArrived,"All configured native processes must reach the provider");
            AgentException foreign=assertThrows(AgentException.class,()->run(ids.getFirst(),"another-owner",new OpenCodeHarness.Dialogue(),"query"));
            assertEquals("FORBIDDEN",foreign.getCode());
            AgentException busy=assertThrows(AgentException.class,()->run(UUID.randomUUID().toString(),"fifth-owner",new OpenCodeHarness.Dialogue(),"query"));
            assertEquals("BUSY",busy.getCode());
        }finally{release.countDown();}
        for(int i=0;i<capacity;i++){
            String answer=futures.get(i).get(25,TimeUnit.SECONDS).answer();assertTrue(answer.contains(owners.get(i)));
            for(int other=0;other<capacity;other++)if(i!=other)assertFalse(answer.contains(owners.get(other)),"Another scope leaked");
        }
        assertEquals(capacity,toolCalls.get());
        assertTrue(run(UUID.randomUUID().toString(),"fifth-owner",new OpenCodeHarness.Dialogue(),"query fifth").answer().contains("fifth-owner"));
        String original=dialogues.getFirst().getSessionId();
        assertTrue(run(ids.getFirst(),owners.getFirst(),dialogues.getFirst(),"query again").answer().contains(owners.getFirst()));
        assertEquals(original,dialogues.getFirst().getSessionId(),"Eviction must preserve the native dialogue");
    }
    @Test void businessPromptReachesTheProviderAlongsideTheCurrentTurnRules()throws Exception {
        var firstRequest=new java.util.concurrent.atomic.AtomicReference<JsonNode>();
        reply=(exchange,request)->{firstRequest.compareAndSet(null,request);scopeReply(exchange,request);};
        var result=run(UUID.randomUUID().toString(),"student-scope",new OpenCodeHarness.Dialogue(),"query current scope");
        assertTrue(result.answer().contains("student-scope"));
        String system="";
        for(JsonNode message:firstRequest.get().path("messages"))
            if("system".equals(message.path("role").asText()))system+=content(message);
        assertTrue(system.contains("DeepIntern 实习管理系统的业务助手"));
        assertTrue(system.contains("Use the supplied tools to answer."),"Per-turn business rules must remain present");
        assertTrue(system.contains("本轮工具回执"),"Current evidence must govern the answer");
        assertFalse(system.contains("You are opencode"),"The business agent must not inherit the coding prompt");
    }
    @Test void pendingQuestionResumesAfterTheNativeServiceRestarts()throws Exception {
        reply=(exchange,request)->{
            if("tool".equals(last(request).path("role").asText()))text(exchange,content(last(request)));
            else if(lastUser(request).contains("CONTINUE-S001"))scopeTool(exchange,request);
            else tool(exchange,"question",Map.of("questions",List.of(Map.of("header","学号","question","缺少学号，请补充。","options",List.of(Map.of("label","补充学号","description","提供缺少的信息"),Map.of("label","暂时跳过","description","保留问题行"))))));
        };
        String id=UUID.randomUUID().toString();var dialogue=new OpenCodeHarness.Dialogue();
        assertTrue(run(id,"administrator-scope",dialogue,"请核对名单").waiting());
        assertNotNull(dialogue.getQuestionId());String nativeId=dialogue.getSessionId();
        String saved=json.writeValueAsString(dialogue);harness.close();harness=new OpenCodeHarness(json,environment,models,bridge,gateway);
        var restored=json.readValue(saved,OpenCodeHarness.Dialogue.class);
        var result=run(id,"administrator-scope",restored,"CONTINUE-S001");
        assertFalse(result.waiting());assertTrue(result.answer().contains("CONTINUE-S001"));assertEquals(nativeId,restored.getSessionId());assertEquals(1,toolCalls.get());
    }
    @Test void cancelledNativeTurnCannotRunToolsInTheFollowingTurn()throws Exception {
        var arrived=new CountDownLatch(1);var release=new CountDownLatch(1);var finished=new CountDownLatch(1);
        reply=(exchange,request)->{if(lastUser(request).contains("BLOCK-OLD")){arrived.countDown();release.await(20,TimeUnit.SECONDS);}scopeReply(exchange,request);};
        String id=UUID.randomUUID().toString();var dialogue=new OpenCodeHarness.Dialogue();
        Future<?> pending=workers.submit(()->{try{return run(id,"student-scope",dialogue,"BLOCK-OLD");}finally{finished.countDown();}});
        try {
            assertTrue(arrived.await(35,TimeUnit.SECONDS));pending.cancel(true);
            assertTrue(finished.await(8,TimeUnit.SECONDS),"Cancellation must finish native cleanup promptly");
            assertEquals(0,toolCalls.get());
        }finally{release.countDown();}
        reply=this::scopeReply;
        var result=run(id,"student-scope",dialogue,"NEW-TURN");
        assertTrue(result.answer().contains("NEW-TURN"));assertFalse(result.answer().contains("BLOCK-OLD"));assertEquals(1,toolCalls.get());
    }
    @Test void timedOutTurnReleasesTheRuntimeForTheNextRequest()throws Exception {
        environment.withProperty("agent.opencode.turn-timeout-ms","700");
        var release=new CountDownLatch(1);
        reply=(exchange,request)->{release.await(15,TimeUnit.SECONDS);scopeReply(exchange,request);};
        String id=UUID.randomUUID().toString();var dialogue=new OpenCodeHarness.Dialogue();
        try {
            AgentException failure=assertThrows(AgentException.class,()->run(id,"teacher-scope",dialogue,"SLOW-TURN"));
            assertEquals("BUDGET_EXCEEDED",failure.getCode());assertEquals(0,toolCalls.get());
        }finally{release.countDown();}
        environment.withProperty("agent.opencode.turn-timeout-ms","90000");reply=this::scopeReply;
        assertTrue(run(id,"teacher-scope",dialogue,"RECOVERED-TURN").answer().contains("RECOVERED-TURN"));assertEquals(1,toolCalls.get());
    }
}
