package com.internship.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.agent.business.AgentException;
import com.internship.agent.business.AgentReadTools;
import com.internship.agent.business.DeepSeekAgentModel;
import com.internship.entity.AIModel;
import com.internship.service.AIModelService;
import com.internship.service.impl.AIConfigRefreshServiceImpl;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.deepseek.api.DeepSeekApi;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real loopback HTTP: verify the wire contract without keys, databases or paid providers. */
class GatewayModelRuntimeTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private HttpServer server;
    private String endpoint;
    private final List<Captured> requests = new CopyOnWriteArrayList<>();
    private boolean returnToolCall;
    private AIModel row;
    private AIModelService service;

    private record Captured(String path, String authorization, JsonNode body) {}

    @BeforeEach void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/", exchange -> {
            try {
                JsonNode request=JSON.readTree(exchange.getRequestBody());
                requests.add(new Captured(exchange.getRequestURI().getPath(),exchange.getRequestHeaders().getFirst("Authorization"),request));
                boolean stream=request.path("stream").asBoolean();
                Map<String,Object> message=returnToolCall ? Map.of("role","assistant","content","",
                        "reasoning_content",stream?"":"Need the tool result.","tool_calls",List.of(Map.of("index",0,"id","call-test","type","function",
                                "function",Map.of("name","lookup","arguments","{}"))))
                        : Map.of("role","assistant","content","网关回复正常");
                var choice=Map.of("index",0,stream?"delta":"message",message,"finish_reason",returnToolCall?"tool_calls":"stop");
                String body=JSON.writeValueAsString(Map.of("id","local-test","object",stream?"chat.completion.chunk":"chat.completion",
                        "created",1,"model",request.path("model").asText(),"choices",List.of(choice),
                        "usage",Map.of("prompt_tokens",1,"completion_tokens",1,"total_tokens",2)));
                String prefix="";
                if(stream && returnToolCall){
                    for(String part:List.of("Need ","the tool result.")){
                        prefix+="data: "+JSON.writeValueAsString(Map.of("id","local-test","object","chat.completion.chunk",
                                "created",1,"model",request.path("model").asText(),"choices",List.of(Map.of("index",0,
                                        "delta",Map.of("role","assistant","content","","reasoning_content",part)))))+"\n\n";
                    }
                }
                byte[] bytes=(stream?prefix+"data: "+body+"\n\ndata: [DONE]\n\n":body).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type",stream?"text/event-stream":"application/json");
                exchange.sendResponseHeaders(200,bytes.length);
                exchange.getResponseBody().write(bytes);
            } finally {exchange.close();}
        });
        server.start();
        endpoint="http://127.0.0.1:"+server.getAddress().getPort()+"/gateway/team/v1";
        row=new AIModel();row.setStatus(1);row.setModelCode("custom/Case-Sensitive-Model");
        row.setApiEndpoint(endpoint+"/chat/completions/");row.setApiKey("dedicated-test-key");
        row.setTemperature(new BigDecimal("0.2"));row.setMaxTokens(2200);
        service=mock(AIModelService.class);when(service.findDefaultModel()).thenReturn(row);
    }

    @AfterEach void stop() {server.stop(0);}

    private DeepSeekAgentModel agent() {
        return new DeepSeekAgentModel("global-key-must-not-leak","http://127.0.0.1:1","legacy",service);
    }

    @ParameterizedTest
    @ValueSource(strings={"deepseek-chat","Qwen/Qwen3-32B","kimi-k2.5","MiniMax-M2.5","ERNIE-4.5-turbo-128k",
            "doubao-seed-1-6","hunyuan-turbos-latest","4.0Ultra","ep-20261002-AbC123","internship-assistant"})
    void agentUsesConfiguredModelKeyAndFullGatewayPath(String code) {
        row.setModelCode(code);
        assertEquals("网关回复正常",agent().call(List.of(new UserMessage("你好")),List.of()).getResult().getOutput().getText());
        assertRequest(code,false);
        assertFalse(requests.getFirst().body().path("messages").get(0).has("prefix"));
        if(code.startsWith("kimi-k2.5")) assertFalse(requests.getFirst().body().has("temperature"));
    }

    @Test void previewNormalAndStreamingClientsUseTheSameGatewayContract() {
        var refresh=mock(AIConfigRefreshServiceImpl.class);when(refresh.getDefaultModelConfig()).thenReturn(row);
        var factory=new DynamicChatClientFactory(DeepSeekApi.builder().baseUrl("http://127.0.0.1:1").apiKey("global").build(),
                refresh,"http://127.0.0.1:1","global");
        assertEquals("网关回复正常",factory.createChatClient("ignored","system").prompt().user("hi").call().content());
        assertEquals("网关回复正常",factory.createPreviewChatClient("ignored","system").prompt().user("hi").call().content());
        assertEquals("网关回复正常",factory.createChatClient("ignored","system").prompt().user("hi").stream().content()
                .collectList().block(Duration.ofSeconds(5)).getFirst());
        for(var request:requests){
            assertEquals("/gateway/team/v1/chat/completions",request.path());
            assertEquals("Bearer dedicated-test-key",request.authorization());
            assertEquals(row.getModelCode(),request.body().path("model").asText());
        }
        assertTrue(requests.getLast().body().path("stream").asBoolean());
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void toolRoundTripPreservesReasoningAndKeepsExecutionInCoordinator(boolean streaming) {
        row.setModelCode("kimi-k2.5");returnToolCall=true;
        var agent=agent();
        List<Message> messages=new ArrayList<>(List.of(new UserMessage("查一下")));
        var specs=List.of(new AgentReadTools.Spec("lookup","Read status","{\"type\":\"object\",\"properties\":{}}"));
        ChatResponse first;
        if(streaming){
            var aggregate=new AtomicReference<ChatResponse>();
            new MessageAggregator().aggregate(agent.stream(messages,specs),aggregate::set).blockLast(Duration.ofSeconds(5));
            first=aggregate.get();
        } else first=agent.call(messages,specs);
        assertTrue(first.hasToolCalls());
        assertEquals(1,requests.size(),"The adapter must not run or replay tools itself");
        assertEquals("lookup",requests.getFirst().body().path("tools").get(0).path("function").path("name").asText());
        messages.add(first.getResult().getOutput());
        messages.add(new ToolResponseMessage(List.of(new ToolResponseMessage.ToolResponse("call-test","lookup","{\"ok\":true}"))));
        returnToolCall=false;
        if(streaming) agent.stream(messages,specs).blockLast(Duration.ofSeconds(5));
        else agent.call(messages,specs);
        var sent=requests.getLast().body().path("messages");
        assertEquals("Need the tool result.",sent.get(1).path("reasoning_content").asText());
        assertEquals("call-test",sent.get(2).path("tool_call_id").asText());
        assertFalse(requests.getLast().body().has("temperature"));
    }

    @Test void independentEndpointWithoutKeyFailsBeforeAnyHttpRequest() {
        row.setApiKey(null);
        assertThrows(AgentException.class,()->agent().call(List.of(new UserMessage("hi")),List.of()));
        var refresh=mock(AIConfigRefreshServiceImpl.class);when(refresh.getDefaultModelConfig()).thenReturn(row);
        var factory=new DynamicChatClientFactory(DeepSeekApi.builder().apiKey("global").build(),refresh,endpoint,"global");
        assertThrows(IllegalArgumentException.class,()->factory.createChatClient("ignored","sys"));
        assertThrows(IllegalArgumentException.class,()->factory.createPreviewChatClient("ignored","sys"));
        assertTrue(requests.isEmpty());
    }

    @Test void defaultKeyAndEndpointEditsApplyOnTheNextAgentCall() {
        var agent=agent();
        agent.call(List.of(new UserMessage("hi")),List.of());
        row.setApiEndpoint(endpoint.replace("/gateway/team/v1","/api/v3"));row.setApiKey("rotated-key");row.setModelCode("New/Model");
        agent.call(List.of(new UserMessage("hi")),List.of());
        var latest=requests.getLast();
        assertEquals("/api/v3/chat/completions",latest.path());
        assertEquals("Bearer rotated-key",latest.authorization());
        assertEquals("New/Model",latest.body().path("model").asText());
    }

    private void assertRequest(String code,boolean stream){
        var request=requests.getFirst();
        assertEquals("/gateway/team/v1/chat/completions",request.path());
        assertEquals("Bearer dedicated-test-key",request.authorization());
        assertEquals(code,request.body().path("model").asText());
        assertEquals(stream,request.body().path("stream").asBoolean());
    }
}
