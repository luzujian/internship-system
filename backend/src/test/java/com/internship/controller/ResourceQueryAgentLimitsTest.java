package com.internship.controller;

import com.internship.service.ResourceQueryAgentService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ResourceQueryAgentLimitsTest {
    @Test
    void oversizedQueryIsRejectedBeforeCallingModel() {
        var service = mock(ResourceQueryAgentService.class);
        var controller = new ResourceQueryAgentController(service);
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "legacyAccess",
                mock(com.internship.agent.business.LegacyAiAccessPolicy.class));
        try {
            var response = controller.queryResources(Map.of("query", "x".repeat(2001)));
            assertEquals(400, response.getStatusCode().value());
            verifyNoInteractions(service);
        } finally {
            controller.closeStreams();
        }
    }

    @Test
    void oversizedContextIsRejectedBeforeCallingModel() {
        var service = mock(ResourceQueryAgentService.class);
        var controller = new ResourceQueryAgentController(service);
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "legacyAccess",
                mock(com.internship.agent.business.LegacyAiAccessPolicy.class));
        try {
            var response = controller.queryResources(Map.of("query", "Find a file", "context", "x".repeat(4001)));
            assertEquals(400, response.getStatusCode().value());
            verifyNoInteractions(service);
        } finally {
            controller.closeStreams();
        }
    }
}
