package com.gdmu.auth;

import org.springframework.security.crypto.bcrypt.BCrypt;

public class PasswordTest {
    public static void main(String[] args) {
        String password = "666666";
        String hashed = "$2a$10$aWTee4CVujz3mu/aoyOF0uK/qiLssG4mJxCg6z3N5zLlJX9tMUgjC";

        boolean matches = BCrypt.checkpw(password, hashed);
        System.out.println("Password '666666' matches: " + matches);

        // Also test with the hash format
        System.out.println("Hash starts with $2a$: " + hashed.startsWith("$2a$"));
        System.out.println("Hash length: " + hashed.length());
    }
}
