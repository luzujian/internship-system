package com.gdmu.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class CustomPasswordEncoderTest {

    @Autowired
    private CustomPasswordEncoder passwordEncoder;

    @Test
    public void testEncode_PlainTextPassword_ShouldReturnBCryptHash() {
        String rawPassword = "123456";
        String encodedPassword = passwordEncoder.encode(rawPassword);

        assertTrue(encodedPassword.startsWith("$2a$") || encodedPassword.startsWith("$2b$"),
            "Encoded password should start with $2a$ or $2b$, actual: " + encodedPassword);

        String encodedPassword2 = passwordEncoder.encode(rawPassword);
        assertNotEquals(encodedPassword, encodedPassword2,
            "BCrypt should produce different results for each encoding");
    }

    @Test
    public void testMatches_BCryptPassword_ShouldReturnTrue() {
        String rawPassword = "testPassword123";
        String encodedPassword = passwordEncoder.encode(rawPassword);
        assertTrue(passwordEncoder.matches(rawPassword, encodedPassword));
    }

    @Test
    public void testMatches_PlainTextPassword_ShouldReturnTrue() {
        String rawPassword = "plainTextPassword";
        String plainEncodedPassword = "plainTextPassword";
        assertTrue(passwordEncoder.matches(rawPassword, plainEncodedPassword),
            "Plain text password should be compatible");
    }

    @Test
    public void testMatches_WrongPassword_ShouldReturnFalse() {
        String rawPassword = "correctPassword";
        String encodedPassword = passwordEncoder.encode("differentPassword");
        assertFalse(passwordEncoder.matches(rawPassword, encodedPassword));
    }

    @Test
    public void testMatches_NullEncodedPassword_ShouldReturnFalse() {
        String rawPassword = "testPassword";
        assertFalse(passwordEncoder.matches(rawPassword, null));
    }

    @Test
    public void testMatches_EmptyEncodedPassword_ShouldReturnFalse() {
        String rawPassword = "testPassword";
        assertFalse(passwordEncoder.matches(rawPassword, ""));
    }

    @Test
    public void testBCryptStrength_ShouldBe10() {
        String rawPassword = "test";
        String encodedPassword = passwordEncoder.encode(rawPassword);
        String[] parts = encodedPassword.split("\\$");
        assertEquals("10", parts[2], "BCrypt strength should be 10");
    }
}
