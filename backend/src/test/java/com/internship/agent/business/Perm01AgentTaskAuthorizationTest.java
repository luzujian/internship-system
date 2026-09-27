package com.internship.agent.business;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.service.SystemConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static com.internship.agent.business.AgentTypes.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Perm01AgentTaskAuthorizationTest {
    @Test void checkpointRejectsRevokedChatBeforeModelOrToolExecution() {
        AgentIdentityService identity = mock(AgentIdentityService.class);
        AgentReadTools tools = mock(AgentReadTools.class);
        AgentModel model = mock(AgentModel.class);
        AgentStore store = mock(AgentStore.class);
        AgentCapabilityService capabilities = mock(AgentCapabilityService.class);
        Actor actor = new Actor("ROLE_STUDENT:8:false:", 8L, "ROLE_STUDENT", "student",
                null, null, null, false, Set.of());
        when(identity.resolve("mock-token")).thenReturn(actor);
        when(tools.scopeFingerprint(actor)).thenReturn("scope");
        AgentTaskService service = new AgentTaskService(identity, tools, model, store, new ObjectMapper(),
                mock(SystemConfigService.class), capabilities, "true");
        Task task = new Task();
        task.setId("task");
        task.setOwnerKey(actor.ownerKey());
        task.setScopeFingerprint("scope");
        task.setCreatedAt(System.currentTimeMillis());
        try {
            when(capabilities.has(actor, "agent_chat")).thenReturn(true);
            assertEquals(actor, ReflectionTestUtils.invokeMethod(service, "checkpoint", "mock-token", task));
            when(capabilities.has(actor, "agent_chat")).thenReturn(false);
            AgentException error = assertThrows(AgentException.class,
                    () -> ReflectionTestUtils.invokeMethod(service, "checkpoint", "mock-token", task));
            assertEquals("CAPABILITY_DISABLED", error.getCode());
            verifyNoInteractions(model);
        } finally {
            service.close();
        }
    }
}
