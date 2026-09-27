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
}
