package com.internship.agent.business;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;
import java.util.Set;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class LegacyAiAccessPolicyTest {
    @Test void featureGateAndCapabilityApplyBeforeModelAccess() {
        var gate=mock(AgentFeatureGate.class); var identity=mock(AgentIdentityService.class);
        var caps=mock(AgentCapabilityService.class);
        var policy=new LegacyAiAccessPolicy(gate,caps,identity);
        var actor=new AgentTypes.Actor("student:1",1L,"ROLE_STUDENT","s",null,null,null,false,Set.of());
        var request=new MockHttpServletRequest(); request.addHeader("Authorization","Bearer fixture");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            when(identity.resolve("Bearer fixture")).thenReturn(actor);
            assertThrows(AgentException.class,policy::require);
            when(caps.has(actor,"agent_chat")).thenReturn(true);
            assertEquals(actor,policy.require());
            doThrow(new AgentException("DISABLED","disabled",403)).when(gate).requireEnabled();
            clearInvocations(identity,caps);
            assertThrows(AgentException.class,policy::require);
            verifyNoInteractions(identity,caps);
        } finally { RequestContextHolder.resetRequestAttributes(); }
    }
}
