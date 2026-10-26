package com.internship.agent.harness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.agent.business.AgentException;
import com.internship.agent.business.AgentReadTools.Spec;
import com.sun.net.httpserver.HttpServer;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;

/** Private loopback bridge for a harness's MCP process. No browser or model receives its lease key. */
@Component
public class HarnessToolBridge {
    @FunctionalInterface public interface Executor { Object execute(String name, JsonNode arguments); }
    private final ObjectMapper json;
    private final HttpServer server;
    private final ExecutorService workers = Executors.newCachedThreadPool(Thread.ofPlatform().daemon(true).name("agent-tool-bridge-",0).factory());
    private final Map<String,Binding> bindings = new ConcurrentHashMap<>();
    private final String advertisedUrl;

    public HarnessToolBridge(ObjectMapper json) throws java.io.IOException {
        this(json,new StandardEnvironment());
    }
    @Autowired
    public HarnessToolBridge(ObjectMapper json,Environment environment) throws java.io.IOException {
        this.json=json;
        server=HttpServer.create(new InetSocketAddress(environment.getProperty("agent.bridge.bind-address","127.0.0.1"),environment.getProperty("agent.bridge.tool-port",Integer.class,0)),16);
        String configuredUrl=environment.getProperty("agent.bridge.tool-url","");
        advertisedUrl=configuredUrl.isBlank()?"http://127.0.0.1:"+server.getAddress().getPort()+"/tools":configuredUrl;
        server.createContext("/tools", exchange -> {
            String key=exchange.getRequestHeaders().getFirst("X-Tool-Lease");
            Binding binding=key==null?null:bindings.get(key);
            int status=200; Object response;
            try {
                if(binding==null || !"POST".equals(exchange.getRequestMethod())) throw new AgentException("FORBIDDEN","工具连接无效。",403);
                byte[] body=exchange.getRequestBody().readNBytes(32769);
                if(body.length>32768) throw new AgentException("INVALID_INPUT","工具参数过长。",400);
                JsonNode request=json.readTree(body);
                response=binding.handle(request);
            } catch(AgentException e) { status=e.getHttpStatus(); response=Map.of("error",e.getCode(),"message",e.getMessage()); }
            catch(Exception e) { status=503; response=Map.of("error","TOOL_UNAVAILABLE","message","工具暂时不可用，请稍后重试。"); }
            byte[] bytes=json.writeValueAsBytes(response);
            exchange.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");
            exchange.sendResponseHeaders(status,bytes.length);
            try(var out=exchange.getResponseBody()){out.write(bytes);}
            exchange.close();
        });
        server.setExecutor(workers); server.start();
    }
    public Binding register(List<Spec> specs) {
        byte[] key=new byte[32];new SecureRandom().nextBytes(key);
        Binding binding=new Binding(Base64.getUrlEncoder().withoutPadding().encodeToString(key),specs);
        bindings.put(binding.key,binding);return binding;
    }
    public String endpoint(){return advertisedUrl;}
    public final class Binding implements AutoCloseable {
        private final String key;
        private List<Spec> specs;
        private Executor executor;
        private AgentException fatal;
        private int calls;
        private long generation;
        private final java.util.concurrent.locks.ReentrantLock invocation=new java.util.concurrent.locks.ReentrantLock();
        private Binding(String key,List<Spec> specs){this.key=key;this.specs=List.copyOf(specs);}
        public String key(){return key;}
        public synchronized void activate(List<Spec> specs,Executor executor){this.specs=List.copyOf(specs);this.executor=executor;this.fatal=null;this.calls=0;generation++;}
        public synchronized void deactivate(){executor=null;generation++;}
        public synchronized AgentException fatal(){return fatal;}
        private Object handle(JsonNode request) {
            invocation.lock();
            try {
            if(request==null || !request.isObject()) throw new AgentException("INVALID_INPUT","工具请求无效。",400);
            Executor current;long epoch;
            synchronized(this){
            if("list".equals(request.path("operation").asText())) return Map.of("tools",specs.stream().map(spec -> {
                try{return Map.of("name",spec.name(),"description",spec.description(),"inputSchema",json.readTree(spec.schema()));}
                catch(Exception e){throw new IllegalArgumentException("Invalid tool schema",e);}
            }).toList());
            String name=request.path("name").asText();JsonNode arguments=request.path("arguments");
            if(!"call".equals(request.path("operation").asText()) || !arguments.isObject() || specs.stream().noneMatch(s->s.name().equals(name)))
                throw new AgentException("TOOL_UNAVAILABLE","当前会话没有此工具。",403);
            if(executor==null) throw new AgentException("WAITING_INPUT","当前会话正在等待用户回复，不能执行工具。",409);
            if(++calls>12) throw new AgentException("BUDGET_EXCEEDED","本轮处理已达到上限，请根据已有结果回复用户。",422);
            current=executor;epoch=generation;
            }
            String name=request.path("name").asText();JsonNode arguments=request.path("arguments");
            try{
                Object result=current.execute(name,arguments);
                synchronized(this){if(epoch!=generation||executor!=current)throw new AgentException("CANCELLED","本轮工具连接已停止，已完成操作以回执为准。",409);}
                return Map.of("result",result);
            }
            catch(AgentException e){
                synchronized(this){if(epoch==generation && Set.of("FORBIDDEN","AUTH_REQUIRED","CAPABILITY_DISABLED","IMPORT_FORBIDDEN","IMPORT_CANCELLED","IMPORT_STATE_CONFLICT","IMPORT_SOURCE_CORRUPTED","IMPORT_STORAGE_UNAVAILABLE","ACTION_UNKNOWN","CANCELLED","SCOPE_CHANGED","AI_DISABLED").contains(e.getCode())) fatal=e;}
                throw e;
            }
            }finally{invocation.unlock();}
        }
        @Override public synchronized void close(){executor=null;generation++;bindings.remove(key,this);}
    }
    @PreDestroy void close(){server.stop(0);workers.shutdownNow();bindings.values().forEach(Binding::close);}
}
