package com.internship.agent.harness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.agent.business.*;
import com.internship.agent.business.AgentTypes.*;
import com.internship.config.AiRuntimeProperties;
import com.internship.entity.AIModel;
import com.internship.service.AIModelService;
import com.internship.util.AiCallGuard;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LangGraphHarnessTest {
    final ObjectMapper json=new ObjectMapper();
    final String serviceKey="test-service-key-012345678901234567890123";
    HttpServer runtime;HarnessToolBridge tools;HarnessModelGateway models;LangGraphHarness harness;
    AtomicReference<JsonNode> submitted=new AtomicReference<>();AtomicReference<JsonNode> cancelled=new AtomicReference<>();
    List<JsonNode> submissions=new ArrayList<>();AtomicInteger interruptedPolls=new AtomicInteger();
    volatile String failedCode;volatile boolean waiting=true;boolean acknowledgeCancellation=true;
    AtomicInteger starts=new AtomicInteger();Session session;Task task;
    @BeforeEach void setup()throws Exception{
        tools=new HarnessToolBridge(json);var limits=new AiRuntimeProperties();models=new HarnessModelGateway(json,new AiCallGuard(limits),limits);
        runtime=HttpServer.create(new InetSocketAddress("127.0.0.1",0),8);
        runtime.createContext("/v1/runs",exchange->{
            try{
                assertEquals("Bearer "+serviceKey,exchange.getRequestHeaders().getFirst("Authorization"));
                if("POST".equals(exchange.getRequestMethod())&&!exchange.getRequestURI().getPath().endsWith("/cancel")){
                    submitted.set(json.readTree(exchange.getRequestBody().readAllBytes()));starts.incrementAndGet();
                    submissions.add(submitted.get().deepCopy());
                    byte[] body="{\"status\":\"QUEUED\"}".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);
                }else if(exchange.getRequestURI().getPath().endsWith("/cancel")){
                    cancelled.set(json.readTree(exchange.getRequestBody().readAllBytes()));exchange.sendResponseHeaders(200,2);exchange.getResponseBody().write("{}".getBytes(StandardCharsets.UTF_8));
                }else{
                    String failure=failedCode;
                    if(interruptedPolls.getAndUpdate(value->Math.max(0,value-1))>0)failure="RUNTIME_INTERRUPTED";
                    if(failure!=null){byte[] body=json.writeValueAsBytes(Map.of("status","FAILED","errorCode",failure));exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);return;}
                    JsonNode input=submitted.get();Map<String,Object> state=new LinkedHashMap<>();state.put("threadId",input.path("threadId").asText());state.put("generation",input.path("generation").asLong());
                    if(acknowledgeCancellation&&input.path("runtime").path("pendingCancellation").hasNonNull("runId"))state.put("reconciledCancellationRunId",input.path("runtime").path("pendingCancellation").path("runId").asText());
                    if(waiting)state.put("pendingQuestion",Map.of("id","question-1","question","请补充班级。","workflowId","work-1","revision",1));state.put("toolLease","must-not-be-persisted");
                    byte[] body=json.writeValueAsBytes(Map.of("status",waiting?"WAITING_INPUT":"SUCCEEDED","answer",waiting?"请补充班级。":"咨询完成。","runtime",state,"modelCalls",1,"inputTokens",20,"outputTokens",10));
                    exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);
                }
            }finally{exchange.close();}
        });runtime.start();
        var environment=new MockEnvironment().withProperty("agent.harness","langgraph").withProperty("agent.langgraph.url","http://127.0.0.1:"+runtime.getAddress().getPort()).withProperty("agent.langgraph.service-key",serviceKey).withProperty("spring.ai.deepseek.base-url","http://127.0.0.1:9/v1").withProperty("spring.ai.deepseek.api-key","provider-secret-stays-in-java");
        var service=mock(AIModelService.class);var model=new AIModel();model.setModelCode("fixture-model");model.setStatus(1);when(service.findDefaultModel()).thenReturn(model);
        harness=new LangGraphHarness(json,environment,service,tools,models);
        session=new Session();session.setId(UUID.randomUUID().toString());session.setOwnerKey("student:1");
        task=new Task();task.setId(UUID.randomUUID().toString());task.setOwnerKey(session.getOwnerKey());task.setMessage("帮我导入");
    }
    @AfterEach void close(){harness.close();runtime.stop(0);tools.close();models.close();}
    @Test void usesFrozenModelAndGuardedLeasesThenPersistsOnlyPublicContinuation()throws Exception{
        AtomicInteger saves=new AtomicInteger();var result=harness.run(session,task,"attachment:scope","business rules",List.of(new AgentReadTools.Spec("my_permissions","权限","{\"type\":\"object\"}")),(name,args)->Map.of(),()->{},saves::incrementAndGet);
        assertTrue(result.waiting());assertEquals(1,result.modelCalls());assertEquals(20,result.inputTokens());
        JsonNode body=submitted.get();assertEquals("fixture-model",body.path("model").path("modelCode").asText());assertEquals(0.2,body.path("model").path("temperature").asDouble());assertEquals(2200,body.path("model").path("maxTokens").asInt());
        assertFalse(body.toString().contains("provider-secret"));assertFalse(body.path("runtime").has("engine"));assertFalse(body.path("runtime").has("contextKey"));
        assertTrue(saves.get()>=2);assertEquals("langgraph",session.getRuntime().get("engine"));assertFalse(json.writeValueAsString(session).contains("must-not-be-persisted"));
        var request=HttpRequest.newBuilder(URI.create(body.path("toolEndpoint").asText())).header("X-Tool-Lease",body.path("toolLease").asText()).POST(HttpRequest.BodyPublishers.ofString("{\"operation\":\"call\",\"name\":\"my_permissions\",\"arguments\":{}}"));
        assertEquals(403,HttpClient.newHttpClient().send(request.build(),HttpResponse.BodyHandlers.ofString()).statusCode());
    }
    @Test void newTurnContinuesSameThreadAcrossPageChangesButEditCreatesNewThread()throws Exception{
        harness.run(session,task,"attachment:scope","first page",List.of(),(name,args)->null,()->{},()->{});String thread=String.valueOf(session.getRuntime().get("threadId"));
        Task next=new Task();next.setId(UUID.randomUUID().toString());next.setOwnerKey(task.getOwnerKey());next.setMessage("先解释这个问题");
        harness.run(session,next,"attachment:scope","different page",List.of(),(name,args)->null,()->{},()->{});assertEquals(thread,session.getRuntime().get("threadId"));assertEquals(2L,((Number)session.getRuntime().get("generation")).longValue());assertFalse(submitted.get().path("fresh").asBoolean());
        next.setId(UUID.randomUUID().toString());next.setEditOf(task.getId());harness.run(session,next,"attachment:scope","edited",List.of(),(name,args)->null,()->{},()->{});assertNotEquals(thread,session.getRuntime().get("threadId"));assertTrue(submitted.get().path("fresh").asBoolean());
    }
    @Test void authorizationFailureStopsBeforeSendingAnything()throws Exception{
        assertThrows(AgentException.class,()->harness.run(session,task,"scope","rules",List.of(),(name,args)->null,()->{throw AgentIdentityService.forbidden();},()->{}));assertEquals(0,starts.get());
    }
    @Test void cancellingWaitingRunCarriesOriginalQuestionAndGeneration()throws Exception{
        harness.run(session,task,"scope","rules",List.of(),(name,args)->null,()->{},()->{});harness.cancel(session,task);
        assertEquals("question-1",cancelled.get().path("questionId").asText());assertEquals(1,cancelled.get().path("generation").asInt());
    }
    @Test void interruptedCheckpointResubmitsIdenticalRequestOnceWithoutCancellation()throws Exception{
        interruptedPolls.set(1);
        assertTrue(harness.run(session,task,"scope","rules",List.of(),(name,args)->null,()->{},()->{}).waiting());
        assertEquals(2,starts.get());assertEquals(submissions.get(0),submissions.get(1));assertNull(cancelled.get());
    }
    @Test void repeatedlyInterruptedCheckpointStopsAfterOneRecoveryAndRetainsDurableRun()throws Exception{
        interruptedPolls.set(3);
        AgentException failure=assertThrows(AgentException.class,()->harness.run(session,task,"scope","rules",List.of(),(name,args)->null,()->{},()->{}));
        assertEquals("TASK_INTERRUPTED",failure.getCode());assertEquals(2,starts.get());assertNull(cancelled.get());
    }
    @Test void unknownBusinessOutcomeIsNeverRetriedOrCancelledAndLeaseIsRevoked()throws Exception{
        failedCode="ACTION_UNKNOWN";
        AgentException failure=assertThrows(AgentException.class,()->harness.run(session,task,"scope","rules",List.of(new AgentReadTools.Spec("my_permissions","权限","{\"type\":\"object\"}")),(name,args)->Map.of(),()->{},()->{}));
        assertEquals("ACTION_UNKNOWN",failure.getCode());assertEquals(1,starts.get());assertNull(cancelled.get());
        JsonNode input=submitted.get();var request=HttpRequest.newBuilder(URI.create(input.path("toolEndpoint").asText())).header("X-Tool-Lease",input.path("toolLease").asText()).POST(HttpRequest.BodyPublishers.ofString("{\"operation\":\"call\",\"name\":\"my_permissions\",\"arguments\":{}}"));
        assertEquals(403,HttpClient.newHttpClient().send(request.build(),HttpResponse.BodyHandlers.ofString()).statusCode());
    }
    @Test void waitingCancellationUsesCurrentGenerationAndOriginalQuestionAfterCounterquery()throws Exception{
        harness.run(session,task,"scope","rules",List.of(),(name,args)->null,()->{},()->{});
        waiting=false;Task next=new Task();next.setId(UUID.randomUUID().toString());next.setOwnerKey(task.getOwnerKey());next.setMessage("先解释一下");
        harness.run(session,next,"scope","rules",List.of(),(name,args)->null,()->{},()->{});
        // Java has already persisted the local stop decision and cleared pendingQuestion.
        session.getRuntime().remove("pendingQuestion");harness.cancel(session,task);
        assertEquals(2,cancelled.get().path("generation").asInt());assertEquals("question-1",cancelled.get().path("questionId").asText());
        assertEquals("work-1",cancelled.get().path("workflowId").asText());assertEquals(1,cancelled.get().path("revision").asInt());
    }
    @Test void cancellationIntentPersistsAcrossFailureThenClearsAfterReconciliation()throws Exception{
        harness.run(session,task,"scope","rules",List.of(),(name,args)->null,()->{},()->{});
        Map<String,Object> intent=Map.of("runId",task.getId(),"questionId","question-1","workflowId","work-1","revision",1);
        session.getRuntime().put("pendingCancellation",intent);session.getRuntime().remove("pendingQuestion");failedCode="MODEL_UNAVAILABLE";
        Task next=new Task();next.setId(UUID.randomUUID().toString());next.setOwnerKey(task.getOwnerKey());next.setMessage("问另一件事");
        assertThrows(AgentException.class,()->harness.run(session,next,"scope","rules",List.of(),(name,args)->null,()->{},()->{}));
        assertEquals(intent,session.getRuntime().get("pendingCancellation"));assertEquals(json.valueToTree(intent),submitted.get().path("runtime").path("pendingCancellation"));
        next.setId(UUID.randomUUID().toString());failedCode=null;waiting=false;
        harness.run(session,next,"scope","rules",List.of(),(name,args)->null,()->{},()->{});
        assertFalse(session.getRuntime().containsKey("pendingCancellation"));assertEquals(task.getId(),next.getReconciledCancellationRunId());assertNull(cancelled.get());
    }
    @Test void terminalResultWithoutExplicitAcknowledgementPreservesCancellationIntent()throws Exception{
        harness.run(session,task,"scope","rules",List.of(),(name,args)->null,()->{},()->{});
        Map<String,Object> intent=Map.of("runId",task.getId(),"questionId","question-1","workflowId","work-1","revision",1);
        session.getRuntime().put("pendingCancellation",intent);session.getRuntime().remove("pendingQuestion");waiting=false;acknowledgeCancellation=false;
        Task next=new Task();next.setId(UUID.randomUUID().toString());next.setOwnerKey(task.getOwnerKey());next.setMessage("其它查询");
        harness.run(session,next,"scope","rules",List.of(),(name,args)->null,()->{},()->{});
        assertEquals(intent,session.getRuntime().get("pendingCancellation"));assertNull(next.getReconciledCancellationRunId());
    }
    @Test void acknowledgementUsesSubmittedSnapshotAndCannotClearLaterCancellation()throws Exception{
        harness.run(session,task,"scope","rules",List.of(),(name,args)->null,()->{},()->{});
        Map<String,Object> original=Map.of("runId",task.getId(),"questionId","question-1","workflowId","work-1","revision",1);
        Map<String,Object> later=Map.of("runId",UUID.randomUUID().toString(),"questionId","question-2","workflowId","work-2","revision",2);
        session.getRuntime().put("pendingCancellation",original);session.getRuntime().remove("pendingQuestion");waiting=false;
        Task next=new Task();next.setId(UUID.randomUUID().toString());next.setOwnerKey(task.getOwnerKey());next.setMessage("另一件事");AtomicInteger checkpoints=new AtomicInteger();
        harness.run(session,next,"scope","rules",List.of(),(name,args)->null,()->{if(checkpoints.incrementAndGet()==2)session.getRuntime().put("pendingCancellation",later);},()->{});
        assertEquals(task.getId(),next.getReconciledCancellationRunId());assertEquals(later,session.getRuntime().get("pendingCancellation"));
        assertEquals(json.valueToTree(original),submitted.get().path("runtime").path("pendingCancellation"));
    }
}
