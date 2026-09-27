package com.internship.controller;

import com.internship.entity.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudentInternshipFlowControllerTest {
    private StudentInternshipFlowController controller;

    @BeforeEach
    void setUp() {
        controller = new StudentInternshipFlowController();
    }

    @Test
    void getFlowReportsFreePlacementCompatibilityMode() {
        Result result = controller.getFlow();

        assertEquals(200, result.getCode());
        Map<?, ?> data = (Map<?, ?>) result.getData();
        assertEquals(Boolean.TRUE, data.get("retired"));
        assertEquals("FREE_PLACEMENT", data.get("mode"));
    }

    @Test
    void stageTwoDecisionEndpointsAreRetired() {
        Result continueResult = controller.continueOriginalPlacement();
        Result changeResult = controller.changePlacement();

        assertTrue(continueResult.getMessage().contains("双阶段实习流程已停用"));
        assertTrue(changeResult.getMessage().contains("双阶段实习流程已停用"));
    }
}
