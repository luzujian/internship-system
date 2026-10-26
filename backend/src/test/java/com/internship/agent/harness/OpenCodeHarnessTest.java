package com.internship.agent.harness;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.agent.business.AgentModelSettings;
import com.internship.agent.business.AgentReadTools.Spec;
import com.internship.service.AIModelService;
import com.internship.util.AiEndpointSupport;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OpenCodeHarnessTest {
    final ObjectMapper json=new ObjectMapper();HarnessToolBridge bridge;OpenCodeHarness harness;HarnessModelGateway gateway;MockEnvironment environment;
    @org.junit.jupiter.api.io.TempDir Path runtimeRoot;
    @BeforeEach void setup()throws Exception{bridge=new HarnessToolBridge(json);var limits=new com.internship.config.AiRuntimeProperties();gateway=new HarnessModelGateway(json,new com.internship.util.AiCallGuard(limits),limits);environment=new MockEnvironment().withProperty("agent.harness","opencode").withProperty("agent.opencode.work-dir",runtimeRoot.toString());harness=new OpenCodeHarness(json,environment,mock(AIModelService.class),bridge,gateway);}
    @AfterEach void close(){harness.close();bridge.close();gateway.close();}
    @Test void officialRuntimeUsesSystemModelAndOnlyQuestionAndBusinessPermissions(){
        var binding=bridge.register(List.of(new Spec("student_todos","待办","{}")));
        var settings=new AgentModelSettings.Resolved("test-model",0.2,2200,"test");var connection=new AiEndpointSupport.Connection("https://model.example/v1","secret");
        var config=json.valueToTree(harness.config(settings,connection,Path.of("bridge.mjs"),binding,gateway.register("owner",settings,connection)));
        assertEquals("deepintern/test-model",config.path("model").asText());assertEquals("@ai-sdk/openai-compatible",config.at("/provider/deepintern/npm").asText());assertEquals("deny",config.at("/permission/*").asText());assertEquals("allow",config.at("/permission/question").asText());assertEquals("allow",config.at("/permission/business_*").asText());assertEquals("disabled",config.path("share").asText());assertFalse(config.path("autoupdate").asBoolean());assertTrue(config.at("/agent/build/disable").asBoolean());
        assertEquals(gateway.endpoint(),config.at("/provider/deepintern/options/baseURL").asText());assertFalse(config.toString().contains("secret"));
        assertFalse(config.path("snapshot").asBoolean());assertEquals("deepintern",config.path("enabled_providers").get(0).asText());
    }
    @Test void questionsRemainNaturalConversationAndExposeFreeTextChoices()throws Exception{
        String text=OpenCodeHarness.questionText(json.readTree("{\"questions\":[{\"question\":\"缺少学号，请补充\",\"options\":[{\"label\":\"先保留\"},{\"label\":\"只导入通过行\"}]}]}"));assertTrue(text.contains("缺少学号"));assertTrue(text.contains("也可以直接说明你的要求"));
    }
    @Test void oldAssistantHistoryCannotCompleteANewTurn()throws Exception{
        var messages=json.readTree("[{\"info\":{\"id\":\"old\",\"role\":\"assistant\"}},{\"info\":{\"id\":\"human\",\"role\":\"user\"}},{\"info\":{\"id\":\"new\",\"role\":\"assistant\"}}]");assertEquals("new",OpenCodeHarness.lastAssistant(messages,Set.of("old")).path("info").path("id").asText());assertNull(OpenCodeHarness.lastAssistant(messages,Set.of("old","new")));
    }
    @Test void deletingAChatRemovesOnlyItsNativeRuntimeFiles()throws Exception {
        String id=UUID.randomUUID().toString();Path directory=Files.createDirectories(runtimeRoot.resolve(id).resolve("data"));
        Files.writeString(directory.resolve("opencode.db"),"owned dialogue");Path other=Files.writeString(runtimeRoot.resolve("preserve.txt"),"another chat");
        harness.deleteConversation(id,"owner");assertFalse(Files.exists(runtimeRoot.resolve(id)));assertTrue(Files.exists(other));
        assertThrows(IllegalArgumentException.class,()->harness.deleteConversation("../outside","owner"));
        harness.deleteConversation(id,"owner");
    }
}
