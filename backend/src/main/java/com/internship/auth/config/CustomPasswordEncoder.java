package com.internship.auth.config;

import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 自定义密码编码器
 * BCrypt password encoder. Legacy plaintext matching is opt-in for isolated migration only.
 */
@Component
public class CustomPasswordEncoder implements PasswordEncoder {

    private static final int BCRYPT_STRENGTH = 10;
    private boolean allowLegacyPlaintextPasswords;

    @Value("${security.allow-legacy-plaintext-passwords:false}")
    public void setAllowLegacyPlaintextPasswords(boolean allowLegacyPlaintextPasswords) {
        this.allowLegacyPlaintextPasswords = allowLegacyPlaintextPasswords;
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return BCrypt.hashpw(rawPassword.toString(), BCrypt.gensalt(BCRYPT_STRENGTH));
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null || encodedPassword.isEmpty()) {
            return false;
        }
        if (!encodedPassword.startsWith("$2a$") && !encodedPassword.startsWith("$2b$")
                && !encodedPassword.startsWith("$2y$")) {
            return allowLegacyPlaintextPasswords && rawPassword.toString().equals(encodedPassword);
        }
        try {
            return BCrypt.checkpw(rawPassword.toString(), encodedPassword);
        } catch (IllegalArgumentException invalidHash) {
            return false;
        }
    }
}
