package com.internship.agent.harness;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.agent.business.AgentException;
import com.internship.agent.business.AgentModelSettings;
import com.internship.config.AiRuntimeProperties;
import com.internship.util.AiCallGuard;
import com.internship.util.AiEndpointSupport;
import com.sun.net.httpserver.HttpServer;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import java.io.InputStream;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** OpenCode's provider transport shares application rate limits without receiving provider credentials. */
@Component
public class HarnessModelGateway {
    private final HttpServer server;
    private final ObjectMapper json;
    private final AiCallGuard guard;
    private final AiRuntimeProperties limits;
    private final HttpClient http;
    private final String advertisedUrl;
    private final Map<String,Binding> bindings=new ConcurrentHashMap<>();
    private final ExecutorService workers=Executors.newCachedThreadPool(Thread.ofPlatform().daemon(true).name("agent-model-gateway-",0).factory());
    private final ScheduledExecutorService timers=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon(true).name("agent-model-timeout").factory());
    public HarnessModelGateway(ObjectMapper json,AiCallGuard guard,AiRuntimeProperties limits)throws java.io.IOException {
        this(json,guard,limits,new StandardEnvironment());
    }
    @Autowired
    public HarnessModelGateway(ObjectMapper json,AiCallGuard guard,AiRuntimeProperties limits,Environment environment)throws java.io.IOException {
        this.json=json;this.guard=guard;this.limits=limits;
        http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofMillis(limits.getConnectTimeoutMillis())).build();
        server=HttpServer.create(new InetSocketAddress(environment.getProperty("agent.bridge.bind-address","127.0.0.1"),environment.getProperty("agent.bridge.model-port",Integer.class,0)),16);
        String configuredUrl=environment.getProperty("agent.bridge.model-url","");
        advertisedUrl=configuredUrl.isBlank()?"http://127.0.0.1:"+server.getAddress().getPort()+"/model/v1":configuredUrl;
        server.createContext("/model/v1/chat/completions",exchange->{
            boolean headers=false;InputStream stream=null;ScheduledFuture<?> timeout=null;
            try {
                String authorization=exchange.getRequestHeaders().getFirst("Authorization");
                Binding binding=authorization!=null && authorization.startsWith("Bearer ")?bindings.get(authorization.substring(7)):null;
                if(binding==null || !"POST".equals(exchange.getRequestMethod()) || !exchange.getRequestURI().getPath().equals("/model/v1/chat/completions"))throw new AgentException("FORBIDDEN","模型连接无效。",403);
                try(var call=binding.begin()) {
                call.check();
                byte[] body=exchange.getRequestBody().readNBytes(2*1024*1024+1);
                if(body.length>2*1024*1024)throw new AgentException("INVALID_INPUT","模型上下文过长。",400);
                var requestBody=json.readTree(body);
                if(requestBody==null || !requestBody.isObject() || !binding.settings.modelCode().equals(requestBody.path("model").asText()))throw new AgentException("FORBIDDEN","模型配置已变化。",403);
                var bounded=(com.fasterxml.jackson.databind.node.ObjectNode)requestBody;
                bounded.put("temperature",binding.settings.temperature());
                String tokenField=bounded.has("max_completion_tokens")?"max_completion_tokens":"max_tokens";
                bounded.put(tokenField,Math.min(binding.settings.maxTokens(),Math.max(1,bounded.path(tokenField).asInt(binding.settings.maxTokens()))));
                body=json.writeValueAsBytes(bounded);
                try(var slot=guard.acquire()) {
                    var request=HttpRequest.newBuilder(URI.create(binding.connection.endpoint().replaceAll("/+$","")+"/chat/completions"))
                            .timeout(Duration.ofMillis(limits.getReadTimeoutMillis())).header("Content-Type","application/json")
                            .header("Authorization","Bearer "+binding.connection.apiKey()).POST(HttpRequest.BodyPublishers.ofByteArray(body));
                    AiEndpointSupport.extraHeaders(binding.connection.endpoint(),binding.settings.modelCode()).forEach(request::header);
                    var response=call.send(request.build());stream=response.body();
                    call.check();
                    if(response.statusCode()<200 || response.statusCode()>299)throw new AgentException("MODEL_UNAVAILABLE","模型服务暂时不可用。",response.statusCode()==429?429:502);
                    exchange.getResponseHeaders().set("Content-Type",response.headers().firstValue("Content-Type").orElse("application/json"));
                    exchange.sendResponseHeaders(200,0);headers=true;
                    InputStream observed=stream;
                    timeout=timers.schedule(()->{try{observed.close();}catch(Exception ignored){}},limits.getStreamTimeoutMillis(),TimeUnit.MILLISECONDS);
                    byte[] buffer=new byte[8192];int count;
                    while((count=stream.read(buffer))!=-1){call.check();exchange.getResponseBody().write(buffer,0,count);exchange.getResponseBody().flush();}
                }
                }
            } catch(Exception e) {
                if(!headers) {
                    int status=e instanceof AgentException a?a.getHttpStatus():503;
                    byte[] body=json.writeValueAsBytes(Map.of("error",Map.of("message","模型连接暂时不可用，请稍后重试。","type",e instanceof AgentException a?a.getCode():"MODEL_UNAVAILABLE")));
                    exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status,body.length);exchange.getResponseBody().write(body);
                }
            } finally {if(timeout!=null)timeout.cancel(false);if(stream!=null)try{stream.close();}catch(Exception ignored){}exchange.close();}
        });
        server.setExecutor(workers);server.start();
    }
    public Binding register(String owner,AgentModelSettings.Resolved settings,AiEndpointSupport.Connection connection){
        var binding=new Binding(owner,settings,connection);bindings.put(binding.key,binding);return binding;
    }
    public String endpoint(){return advertisedUrl;}
    public AiCallGuard.Lease acquireUser(String owner){return guard.acquireUser(owner);}
    public final class Binding implements AutoCloseable {
        private final String key=UUID.randomUUID().toString()+UUID.randomUUID(),owner;
        private final AgentModelSettings.Resolved settings;private final AiEndpointSupport.Connection connection;
        private volatile Runnable checkpoint;
        private volatile long generation;
        private final Set<Call> calls=ConcurrentHashMap.newKeySet();
        private Binding(String owner,AgentModelSettings.Resolved settings,AiEndpointSupport.Connection connection){this.owner=owner;this.settings=settings;this.connection=connection;}
        public String key(){return key;}
        public synchronized void activate(Runnable checkpoint){deactivate();this.checkpoint=Objects.requireNonNull(checkpoint);}
        private synchronized Call begin(){
            if(checkpoint==null)throw new AgentException("WAITING_INPUT","当前会话未在执行。",409);
            Call call=new Call(this,generation,checkpoint);calls.add(call);return call;
        }
        public synchronized void deactivate(){checkpoint=null;generation++;calls.forEach(Call::close);}
        @Override public void close(){deactivate();bindings.remove(key,this);}
    }
    /** A cancelled turn also owns requests that have not received response headers yet. */
    private final class Call implements AutoCloseable {
        private final Binding binding;private final long generation;private final Runnable checkpoint;
        private CompletableFuture<HttpResponse<InputStream>> pending;
        private InputStream stream;private volatile boolean closed;
        private Call(Binding binding,long generation,Runnable checkpoint){this.binding=binding;this.generation=generation;this.checkpoint=checkpoint;}
        private void check(){
            if(closed || binding.generation!=generation || binding.checkpoint==null)throw new AgentException("WAITING_INPUT","当前会话未在执行。",409);
            checkpoint.run();
        }
        private HttpResponse<InputStream> send(HttpRequest request)throws Exception {
            CompletableFuture<HttpResponse<InputStream>> response;
            synchronized(this){
                check();response=http.sendAsync(request,HttpResponse.BodyHandlers.ofInputStream());pending=response;
                response.thenAccept(value->{synchronized(this){stream=value.body();if(closed)closeStream();}});
            }
            try{return response.get();}catch(CancellationException e){check();throw e;}
        }
        private void closeStream(){if(stream!=null)try{stream.close();}catch(Exception ignored){}}
        @Override public synchronized void close(){closed=true;if(pending!=null)pending.cancel(true);closeStream();binding.calls.remove(this);}
    }
    @PreDestroy void close(){bindings.values().forEach(Binding::close);server.stop(0);workers.shutdownNow();timers.shutdownNow();http.close();}
}
