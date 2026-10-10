package com.internship.agent.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.entity.AIModel;
import com.internship.service.AIModelService;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeepSeekAgentReviewTest {
    private final ObjectMapper json=new ObjectMapper();
    private final List<JsonNode> requests=new CopyOnWriteArrayList<>();
    private HttpServer server;

    @AfterEach void close(){if(server!=null)server.stop(0);}

    private DeepSeekAgentModel model(String code) throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/chat/completions",exchange->{
            requests.add(json.readTree(exchange.getRequestBody()));
            byte[] body=("data: {\"id\":\"test\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\""+code
                    +"\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"{\\\"keep\\\":[0]}\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","text/event-stream");
            exchange.sendResponseHeaders(200,body.length);
            try(var out=exchange.getResponseBody()){out.write(body);}
        });
        server.start();
        var models=mock(AIModelService.class);
        var row=new AIModel();row.setModelCode(code);row.setStatus(1);row.setMaxTokens(2200);
        when(models.findDefaultModel()).thenReturn(row);
        return new DeepSeekAgentModel("test-key","http://127.0.0.1:"+server.getAddress().getPort(),code,models);
    }

    @Test void reviewUsesIndependentWireOptionsWithoutChangingMainRequestsOrToolAvailability() throws Exception {
        var model=model("deepseek-flash");
        var messages=List.<org.springframework.ai.chat.messages.Message>of(new UserMessage("检查 JSON"));
        var tools=List.of(new AgentReadTools.Spec("jobs_details","查询岗位","{\"type\":\"object\",\"properties\":{}}"));
        model.stream(messages,tools).blockLast(Duration.ofSeconds(10));
        model.review(messages).blockLast(Duration.ofSeconds(10));
        model.stream(messages,tools).blockLast(Duration.ofSeconds(10));
        assertEquals(3,requests.size());
        var review=requests.get(1);
        assertEquals("enabled",review.path("thinking").path("type").asText());
        assertEquals("low",review.path("reasoning_effort").asText());
        assertEquals("json_object",review.path("response_format").path("type").asText());
        assertEquals(4096,review.path("max_tokens").asInt());
        assertTrue(review.path("tools").isMissingNode()||review.path("tools").isEmpty());
        for(int index:List.of(0,2)) {
            var main=requests.get(index);
            assertEquals(2200,main.path("max_tokens").asInt());
            assertFalse(main.hasNonNull("thinking"));assertFalse(main.hasNonNull("response_format"));
            assertFalse(main.hasNonNull("reasoning_effort"));
            assertEquals(1,main.path("tools").size());
            assertEquals(main.path("messages"),review.path("messages"));
        }
        assertEquals(requests.get(0),requests.get(2),"Cached review options must not leak into later main calls");
    }

    @Test void unknownProviderKeepsItsConfiguredBudgetAndDoesNotReceiveDeepSeekOptions() throws Exception {
        var model=model("custom-reasoning-model");
        model.review(List.of(new UserMessage("检查 JSON"))).blockLast(Duration.ofSeconds(10));
        var review=requests.getFirst();
        assertEquals(2200,review.path("max_tokens").asInt());
        assertFalse(review.hasNonNull("thinking"));assertFalse(review.hasNonNull("response_format"));
        assertFalse(review.hasNonNull("reasoning_effort"));
    }
}
