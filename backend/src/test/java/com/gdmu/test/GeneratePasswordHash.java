package com.gdmu.test;

import org.springframework.security.crypto.bcrypt.BCrypt;

public class GeneratePasswordHash {
    public static void main(String[] args) {
        String password = args.length > 0 ? args[0] : "123456";
        String hash = BCrypt.hashpw(password, BCrypt.gensalt(10));
        System.out.println("Password: " + password);
        System.out.println("Hash: " + hash);

        // 验证
        boolean matches = BCrypt.checkpw(password, hash);
        System.out.println("Matches: " + matches);
    }
}
