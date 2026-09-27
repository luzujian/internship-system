package com.internship.agent.business;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.internship.agent.business.AgentTypes.*;

class AgentUiIntentServiceTest {
    final AgentModel model=mock(AgentModel.class);
    final AgentCapabilityService caps=mock(AgentCapabilityService.class);
    final AgentTaskService tasks=mock(AgentTaskService.class);
    final Actor actor=new Actor("company:1",1L,"ROLE_COMPANY","企业",null,null,null,false,Set.of());
    final AgentUiIntentService service=new AgentUiIntentService(model,new ObjectMapper(),caps,tasks);
    final AgentUiIntentService.Request request=new AgentUiIntentService.Request("打开招聘管理","首页",List.of(new AgentUiIntentService.Target("page-1","招聘管理","page")));
    void answer(String answer) {
        when(tasks.enabled()).thenReturn(true);when(caps.has(actor,"agent_chat")).thenReturn(true);
        when(model.call(anyList(),anyList())).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(answer)))));
    }
    @Test void onlyAdvertisedTargetIsAccepted() {
        answer("{\"intent\":\"open\",\"targetId\":\"page-1\",\"fill\":false}");
        assertEquals("page-1",service.resolve(actor,request).get("targetId"));
    }
    @Test void inventedTargetIsRejected() {
        answer("{\"intent\":\"open\",\"targetId\":\"admin-delete\"}");
        assertThrows(AgentException.class,()->service.resolve(actor,request));
    }
    @Test void ordinaryQuestionDoesNotNavigate() {
        answer("{\"intent\":\"chat\"}");
        assertEquals(Map.of("intent","chat"),service.resolve(actor,request));
    }
    @Test void disabledIdentityNeverInvokesModel() {
        assertThrows(AgentException.class,()->service.resolve(actor,request));verifyNoInteractions(model);
    }
    @Test void malformedFlagsNeverBecomeActions() {
        answer("{\"intent\":\"open\",\"targetId\":\"page-1\",\"fill\":\"true\"}");
        assertEquals("INVALID_MODEL_OUTPUT",assertThrows(AgentException.class,()->service.resolve(actor,request)).getCode());
    }
    @Test void fillRequiresItsOwnCapability() {
        answer("{\"intent\":\"open\",\"targetId\":\"page-1\",\"fill\":true}");
        assertEquals("CAPABILITY_DISABLED",assertThrows(AgentException.class,()->service.resolve(actor,request)).getCode());
    }
    @Test void revokedCapabilityDuringModelCallRejectsResult() {
        answer("{\"intent\":\"open\",\"targetId\":\"page-1\"}");
        when(caps.has(actor,"agent_chat")).thenReturn(true,false);
        assertEquals("CAPABILITY_DISABLED",assertThrows(AgentException.class,()->service.resolve(actor,request)).getCode());
    }
    @Test void failedModelReleasesOwnerSlot() {
        answer("not-json");assertThrows(AgentException.class,()->service.resolve(actor,request));
        answer("{\"intent\":\"chat\"}");assertEquals("chat",service.resolve(actor,request).get("intent"));
    }
    @Test void concurrentRequestForSameOwnerDoesNotInvokeModelTwice() {
        answer("{\"intent\":\"chat\"}");
        when(model.call(anyList(),anyList())).thenAnswer(invocation -> {
            assertEquals("BUSY",assertThrows(AgentException.class,()->service.resolve(actor,request)).getCode());
            return new ChatResponse(List.of(new Generation(new AssistantMessage("{\"intent\":\"chat\"}"))));
        });
        service.resolve(actor,request);verify(model,times(1)).call(anyList(),anyList());
    }
}
