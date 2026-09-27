package com.internship.controller;

import com.internship.entity.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudentInternshipConfirmationDraftFlowTest {
    private StudentInternshipConfirmationController controller;

    @BeforeEach
    void setUp() {
        controller = new StudentInternshipConfirmationController();
    }

    @Test
    void legacyDraftAndSubmitEndpointsAreRetired() {
        Result save = controller.save(Map.of());
        Result submit = controller.submit(Map.of("recordId", 70L));

        assertEquals(400, save.getCode());
        assertEquals(400, submit.getCode());
        assertTrue(save.getMessage().contains("offerId"));
        assertTrue(submit.getMessage().contains("offerId"));
    }
}
