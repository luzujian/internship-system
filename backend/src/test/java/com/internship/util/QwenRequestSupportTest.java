package com.internship.util;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
class QwenRequestSupportTest {
    @Test void qwenUsesDirectAnswersWithoutChangingMessagesOrTools() throws Exception {
        var json=new ObjectMapper();
        var input=json.readTree("{\"model\":\"qwen3.8-omni-flash\",\"stream\":true,\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"tools\":[]}");
        var output=json.readTree(QwenRequestSupport.normalize(json.writeValueAsBytes(input)));
        assertEquals("none",output.path("reasoning_effort").asText());
        assertEquals(input.path("messages"),output.path("messages"));assertEquals(input.path("tools"),output.path("tools"));assertTrue(output.path("stream").asBoolean());
    }
    @Test void otherProvidersAndExplicitReasoningRemainUnchanged() throws Exception {
        for(String input:new String[]{"{\"model\":\"deepseek-flash\"}","{\"model\":\"qwen3.8-omni-flash\",\"reasoning_effort\":\"low\"}","{\"model\":\"qwen3.8-omni-flash\",\"thinking_budget\":128}"}) {
            var json=new ObjectMapper();assertEquals(json.readTree(input),json.readTree(QwenRequestSupport.normalize(input.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"kimi-k2.5","moonshotai/Kimi-K2.5","kimi-k2.6","kimi-k2.7-code-highspeed","kimi-k3","kimi-k2-thinking"})
    void kimiUsesProviderFixedSamplingDefaults(String model) throws Exception {
        var json=new ObjectMapper();
        var input=json.createObjectNode().put("model",model).put("temperature",0.2).put("top_p",0.7)
                .put("presence_penalty",1).put("frequency_penalty",1).put("max_tokens",2200);
        var output=json.readTree(QwenRequestSupport.normalize(json.writeValueAsBytes(input)));
        assertFalse(output.has("temperature"));assertFalse(output.has("top_p"));
        assertFalse(output.has("presence_penalty"));assertFalse(output.has("frequency_penalty"));
        assertEquals(2200,output.path("max_tokens").asInt());
        assertEquals(model,output.path("model").asText());
    }
}
