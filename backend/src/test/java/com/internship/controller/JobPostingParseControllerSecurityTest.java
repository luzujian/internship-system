package com.internship.controller;

import jakarta.validation.Valid;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JobPostingParseControllerSecurityTest {

    @Test
    void endpointIsCompanyOnlyAndValidatesRequestBody() throws Exception {
        PreAuthorize authorization = JobPostingParseController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(authorization);
        assertEquals("hasRole('COMPANY')", authorization.value());

        Method method = JobPostingParseController.class.getDeclaredMethod(
                "parseJobPosting", com.internship.dto.JobPostingParseRequest.class);
        assertNotNull(method.getParameters()[0].getAnnotation(Valid.class));
    }
}
