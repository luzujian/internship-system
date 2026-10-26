package com.internship.agent.harness;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.agent.business.AgentModelSettings;
import com.internship.config.AiRuntimeProperties;
import com.internship.util.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class HarnessModelGatewayTest {
    final ObjectMapper json=new ObjectMapper();AiRuntimeProperties limits;AiCallGuard guard;HarnessModelGateway gateway;HarnessModelGateway.Binding binding;HttpServer provider;
    AtomicInteger calls;AtomicReference<String> credential;
    @BeforeEach void setup()throws Exception{
        calls=new AtomicInteger();credential=new AtomicReference<>();limits=new AiRuntimeProperties();guard=new AiCallGuard(limits);gateway=new HarnessModelGateway(json,guard,limits);
        provider=HttpServer.create(new InetSocketAddress("127.0.0.1",0),4);
        provider.createContext("/v1/chat/completions",exchange->{calls.incrementAndGet();credential.set(exchange.getRequestHeaders().getFirst("Authorization"));exchange.getRequestBody().readAllBytes();
            byte[] body="data: {\"choices\":[]}\n\ndata: [DONE]\n\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","text/event-stream");exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();});provider.start();
        binding=gateway.register("owner",new AgentModelSettings.Resolved("fixture-model",0.2,2200,"test"),new AiEndpointSupport.Connection("http://127.0.0.1:"+provider.getAddress().getPort()+"/v1","provider-secret"));binding.activate(()->{});
    }
    @AfterEach void close(){gateway.close();provider.stop(0);}
    HttpResponse<String> request(String key,String model)throws Exception{
        var builder=HttpRequest.newBuilder(URI.create(gateway.endpoint()+"/chat/completions")).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("model",model,"stream",true,"messages",List.of()))));if(key!=null)builder.header("Authorization","Bearer "+key);
        return HttpClient.newHttpClient().send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void forwardsProviderStreamWhileKeepingTheRealCredentialInBackend()throws Exception{
        var result=request(binding.key(),"fixture-model");assertEquals(200,result.statusCode());assertTrue(result.body().contains("[DONE]"));assertFalse(result.body().contains("provider-secret"));assertEquals("Bearer provider-secret",credential.get());
    }
    @Test void unknownLeaseDifferentModelAndInactiveTurnNeverReachProvider()throws Exception{
        assertEquals(403,request(null,"fixture-model").statusCode());assertEquals(403,request(binding.key(),"another-model").statusCode());binding.deactivate();assertEquals(409,request(binding.key(),"fixture-model").statusCode());assertEquals(0,calls.get());
    }
    @Test void eachOpenCodeModelCallUsesSharedProviderRateLimit()throws Exception{
        limits.setMaxRequestsPerMinute(1);assertEquals(200,request(binding.key(),"fixture-model").statusCode());assertEquals(429,request(binding.key(),"fixture-model").statusCode());assertEquals(1,calls.get());
    }
    @Test void otherApplicationModelCallsCompeteForTheSameSlots()throws Exception{
        var leases=new ArrayList<AiCallGuard.Lease>();for(int i=0;i<limits.getMaxConcurrentCalls();i++)leases.add(guard.acquire());
        try{assertEquals(429,request(binding.key(),"fixture-model").statusCode());assertEquals(0,calls.get());}finally{leases.forEach(AiCallGuard.Lease::close);}
    }
    @Test void cancellationBeforeProviderHeadersReleasesTheSharedSlot()throws Exception {
        var arrived=new CountDownLatch(1);var release=new CountDownLatch(1);
        provider.removeContext("/v1/chat/completions");
        provider.createContext("/v1/chat/completions",exchange->{
            exchange.getRequestBody().readAllBytes();arrived.countDown();
            try{release.await(10,TimeUnit.SECONDS);exchange.sendResponseHeaders(200,2);exchange.getResponseBody().write("{}".getBytes());}
            catch(Exception ignored){}finally{exchange.close();}
        });
        var held=new ArrayList<AiCallGuard.Lease>();
        for(int i=1;i<limits.getMaxConcurrentCalls();i++)held.add(guard.acquire());
        try {
            var pending=CompletableFuture.supplyAsync(()->{try{return request(binding.key(),"fixture-model");}catch(Exception e){throw new CompletionException(e);}});
            assertTrue(arrived.await(3,TimeUnit.SECONDS));
            binding.deactivate();
            assertNotEquals(200,pending.get(2,TimeUnit.SECONDS).statusCode());
            try(var freed=guard.acquire()){assertNotNull(freed);}
        }finally{release.countDown();held.forEach(AiCallGuard.Lease::close);}
    }
}
