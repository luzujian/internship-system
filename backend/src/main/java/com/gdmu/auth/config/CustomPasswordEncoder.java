package com.gdmu.auth.config;

import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 自定义密码编码器
 * 支持 BCrypt 加密和明文密码（过渡期兼容）
 */
@Component
public class CustomPasswordEncoder implements PasswordEncoder {

    private static final int BCRYPT_STRENGTH = 10;

    @Override
    public String encode(CharSequence rawPassword) {
        return BCrypt.hashpw(rawPassword.toString(), BCrypt.gensalt(BCRYPT_STRENGTH));
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (encodedPassword == null || encodedPassword.isEmpty()) {
            return false;
        }
        // 兼容旧明文密码（过渡期支持）
        if (!encodedPassword.startsWith("$2a$") && !encodedPassword.startsWith("$2b$")) {
            return rawPassword.toString().equals(encodedPassword);
        }
        return BCrypt.checkpw(rawPassword.toString(), encodedPassword);
    }
}
