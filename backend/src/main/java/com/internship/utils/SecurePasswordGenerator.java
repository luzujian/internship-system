package com.internship.utils;

/**
 * Compatibility wrapper for callers that still request a generated account password.
 * All created/imported accounts now use the same approved default password.
 */
public final class SecurePasswordGenerator {
    private SecurePasswordGenerator() { }

    public static String generate() {
        return UserCredentialDefaults.PASSWORD;
    }
}
