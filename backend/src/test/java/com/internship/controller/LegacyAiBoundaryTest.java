package com.internship.controller;
import com.internship.agent.business.*;
import com.internship.config.DynamicChatClientFactory;
import com.internship.service.JobRecommendationAgentService;
import com.internship.service.JobDescriptionGeneratorService;
import com.internship.entity.User;
import com.internship.utils.CurrentHolder;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class LegacyAiBoundaryTest {
    private AgentTypes.Actor actor(String role) {
        return new AgentTypes.Actor(role+":1", 1L, role, "test", null, null, null, false, Set.of());
    }
    @Test void recommendationUsesAuthenticatedStudentInsteadOfRequestIdentity() {
        var jobs = mock(JobRecommendationAgentService.class);
        var factory = mock(DynamicChatClientFactory.class);
        var access = mock(LegacyAiAccessPolicy.class);
        when(access.require()).thenReturn(actor("ROLE_STUDENT"));
        when(jobs.processJobRecommendation(anyString(), eq("student-a"), isNull())).thenReturn(Map.of("success",true));
        var controller = new AIChatController(factory, jobs);
        ReflectionTestUtils.setField(controller,"legacyAccess",access);
        ReflectionTestUtils.setField(controller,"requestLimiter",new LegacyAiRequestLimiter());
        var user = new User(); user.setUsername("student-a"); CurrentHolder.setUser(user);
        try {
            assertEquals(200, controller.chat(Map.of("message","推荐实习岗位","username","victim","role","admin")).getStatusCode().value());
            verify(jobs).processJobRecommendation("推荐实习岗位","student-a",null);
            verifyNoInteractions(factory);
            assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> controller.chat(Map.of("message","x".repeat(4001))));
        } finally { CurrentHolder.remove(); controller.shutdown(); }
    }
    @Test void jobDescriptionSharesLimitsAndAllowsOrdinaryCompanyRequest() throws Exception {
        var service = mock(JobDescriptionGeneratorService.class);
        var access = mock(LegacyAiAccessPolicy.class);
        when(access.require()).thenReturn(actor("ROLE_COMPANY"));
        var limiter = new LegacyAiRequestLimiter();
        var controller = new JobDescriptionGeneratorController(service);
        ReflectionTestUtils.setField(controller,"legacyAccess",access);
        ReflectionTestUtils.setField(controller,"requestLimiter",limiter);
        assertEquals(400,controller.generateJobDescription(Map.of("positionName","x".repeat(4001))).getStatusCode().value());
        try (var lease=limiter.acquire("ROLE_COMPANY:1")) {
            assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> controller.generateJobDescription(Map.of("positionName","nurse")));
        }
        verifyNoInteractions(service);
        when(service.generateJobDescription("nurse",null,null,null)).thenReturn(Map.of("description","fixture"));
        assertEquals(200,controller.generateJobDescription(Map.of("positionName","nurse")).getStatusCode().value());
    }
}
