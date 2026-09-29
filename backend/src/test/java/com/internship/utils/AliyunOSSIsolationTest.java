package com.internship.utils;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

class AliyunOSSIsolationTest {
    @Test void separateDeploymentWritesOnlyItsNamespaceAndPreservesInheritedFiles() {
        var storage = new AliyunOSSOperator();
        ReflectionTestUtils.setField(storage, "objectPrefix", "deployments/internai");
        assertEquals("deployments/internai/backups/a.sql", storage.isolatedObjectName("backups/a.sql"));
        assertEquals("deployments/internai/files/a.pdf", storage.isolatedObjectName("deployments/internai/files/a.pdf"));
        assertTrue(storage.ownsObject("deployments/internai/files/a.pdf"));
        assertFalse(storage.ownsObject("files/a.pdf"));
        assertFalse(storage.ownsObject("deployments/internai-other/files/a.pdf"));
        assertThrows(IllegalArgumentException.class, () -> storage.isolatedObjectName("../files/a.pdf"));
    }

    @Test void existingDeploymentKeepsItsPaths() {
        var storage = new AliyunOSSOperator();
        assertEquals("files/a.pdf", storage.isolatedObjectName("files/a.pdf"));
        assertTrue(storage.ownsObject("files/a.pdf"));
    }
}
