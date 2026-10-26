package com.internship.agent.harness;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.agent.business.AgentException;
import com.internship.agent.business.AgentReadTools.Spec;
import org.junit.jupiter.api.*;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class HarnessToolBridgeTest {
    final ObjectMapper json=new ObjectMapper();
    HarnessToolBridge bridge;HarnessToolBridge.Binding binding;AtomicInteger calls;
    @BeforeEach void setup()throws Exception{bridge=new HarnessToolBridge(json);binding=bridge.register(List.of(new Spec("student_todos","本人待办","{\"type\":\"object\"}")));calls=new AtomicInteger();}
    @AfterEach void close(){bridge.close();}
    HttpResponse<String> post(String lease,Object body)throws Exception{
        var builder=HttpRequest.newBuilder(URI.create(bridge.endpoint())).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        if(lease!=null)builder.header("X-Tool-Lease",lease);
        return HttpClient.newHttpClient().send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }
    Map<String,Object> call(String name){return Map.of("operation","call","name",name,"arguments",Map.of());}
    @Test void missingOrAnotherLeaseCannotDiscoverOrExecuteTools()throws Exception{assertEquals(403,post(null,Map.of("operation","list")).statusCode());assertEquals(403,post("another-session",call("student_todos")).statusCode());}
    @Test void questionPauseAndClosedLeaseBlockLateToolCalls()throws Exception{
        binding.activate(List.of(new Spec("student_todos","本人待办","{}")),(name,args)->{calls.incrementAndGet();return Map.of("count",1);});
        assertEquals(200,post(binding.key(),call("student_todos")).statusCode());binding.deactivate();assertEquals(409,post(binding.key(),call("student_todos")).statusCode());binding.close();assertEquals(403,post(binding.key(),call("student_todos")).statusCode());assertEquals(1,calls.get());
    }
    @Test void toolSetIsSessionScopedAndRecheckedEveryTurn()throws Exception{
        binding.activate(List.of(new Spec("student_todos","本人待办","{}")),(name,args)->{calls.incrementAndGet();return Map.of();});
        assertEquals(403,post(binding.key(),call("confirm_import_plan")).statusCode());
        binding.activate(List.of(new Spec("knowledge_lookup","系统说明","{}")),(name,args)->Map.of());assertEquals(403,post(binding.key(),call("student_todos")).statusCode());assertEquals(0,calls.get());
    }
    @Test void liveRevocationBecomesFatalAndNeverLeaksExceptionDetails()throws Exception{
        binding.activate(List.of(new Spec("student_todos","待办","{}")),(name,args)->{throw new AgentException("FORBIDDEN","权限已变化",403);});assertEquals(403,post(binding.key(),call("student_todos")).statusCode());assertEquals("FORBIDDEN",binding.fatal().getCode());
    }
    @Test void toolLimitPreventsAnUnboundedLoop()throws Exception{
        binding.activate(List.of(new Spec("student_todos","待办","{}")),(name,args)->{calls.incrementAndGet();return Map.of();});for(int i=0;i<12;i++)assertEquals(200,post(binding.key(),call("student_todos")).statusCode());assertEquals(422,post(binding.key(),call("student_todos")).statusCode());assertEquals(12,calls.get());
    }
    @Test void revokingLeaseDoesNotWaitForASlowToolAndLateResponseIsDiscarded()throws Exception{
        var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        binding.activate(List.of(new Spec("student_todos","待办","{}")),(name,args)->{
            entered.countDown();try{release.await(3,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}return Map.of("completed",true);
        });
        try(var workers=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var pending=workers.submit(()->post(binding.key(),call("student_todos")));
            assertTrue(entered.await(2,java.util.concurrent.TimeUnit.SECONDS));
            var cancelled=workers.submit(()->binding.deactivate());
            try{cancelled.get(500,java.util.concurrent.TimeUnit.MILLISECONDS);}finally{release.countDown();}
            assertEquals(409,pending.get(2,java.util.concurrent.TimeUnit.SECONDS).statusCode());assertNull(binding.fatal());
        }finally{release.countDown();}
    }
}
