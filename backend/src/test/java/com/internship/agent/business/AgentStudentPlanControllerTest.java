package com.internship.agent.business;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentStudentPlanControllerTest {
    @Test void dedicatedRefreshChecksFeatureGateAndUsesScopedReadToolWithoutModel() {
        var identity = mock(AgentIdentityService.class);
        var gate = mock(AgentFeatureGate.class);
        var reads = mock(AgentReadTools.class);
        var actor = new AgentTypes.Actor("ROLE_STUDENT:8:false:", 8L, "ROLE_STUDENT", "student", null, null, null, false, Set.of());
        when(identity.resolve("Bearer local-test")).thenReturn(actor);
        doThrow(new AgentException("CAPABILITY_DISABLED", "未开通", 403)).when(reads).execute(actor, "student_plan", "{}");
        var controller = new AgentStudentPlanController(identity, gate, reads);
        var exception = assertThrows(AgentException.class, () -> controller.current("Bearer local-test"));
        assertEquals(403, exception.getHttpStatus());
        verify(gate).requireEnabled();
        verify(reads).execute(actor, "student_plan", "{}");
    }

    @Test void disabledFeatureDoesNotReadPlan() {
        var identity = mock(AgentIdentityService.class);
        var gate = mock(AgentFeatureGate.class);
        var reads = mock(AgentReadTools.class);
        doThrow(new AgentException("AI_DISABLED", "未启用", 503)).when(gate).requireEnabled();
        assertThrows(AgentException.class, () -> new AgentStudentPlanController(identity, gate, reads).current("Bearer local-test"));
        verifyNoInteractions(reads);
    }
}
