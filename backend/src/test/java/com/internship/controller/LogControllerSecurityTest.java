package com.internship.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;

class LogControllerSecurityTest {

    @Test
    void operationLogsCannotBeCreatedFromClientSuppliedPayloads() {
        boolean exposesClientWriteEndpoint = Arrays.stream(LogController.class.getDeclaredMethods())
                .map(Method::getDeclaredAnnotations)
                .flatMap(Arrays::stream)
                .filter(PostMapping.class::isInstance)
                .map(PostMapping.class::cast)
                .flatMap(mapping -> Arrays.stream(mapping.value()))
                .anyMatch("/operation"::equals);

        assertFalse(exposesClientWriteEndpoint,
                "operation audit logs must only be generated from server-side execution context");
    }
}
