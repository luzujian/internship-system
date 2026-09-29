package com.internship.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SecurePasswordGeneratorTest {
    @Test
    void generatedAccountPasswordsUseTheApprovedDefault() {
        assertEquals(UserCredentialDefaults.PASSWORD, SecurePasswordGenerator.generate());
        assertEquals("123456", SecurePasswordGenerator.generate());
    }
}
