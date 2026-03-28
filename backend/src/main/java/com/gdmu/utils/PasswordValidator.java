package com.gdmu.utils;

import com.gdmu.config.SystemSettingsConfig;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PasswordValidator {
    
    public static ValidationResult validatePassword(String password) {
        ValidationResult result = new ValidationResult();
        
        int minLength = SystemSettingsConfig.getMinPasswordLength();
        if (password == null || password.length() < minLength) {
            result.setValid(false);
            result.setMessage("密码长度不能少于" + minLength + "位");
            return result;
        }
        
        String complexity = getComplexityRequirement();
        if (complexity.contains("lowercase") && !password.matches(".*[a-z].*")) {
            result.setValid(false);
            result.setMessage("密码必须包含小写字母");
            return result;
        }
        
        if (complexity.contains("uppercase") && !password.matches(".*[A-Z].*")) {
            result.setValid(false);
            result.setMessage("密码必须包含大写字母");
            return result;
        }
        
        if (complexity.contains("number") && !password.matches(".*\\d.*")) {
            result.setValid(false);
            result.setMessage("密码必须包含数字");
            return result;
        }
        
        if (complexity.contains("special") && !password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) {
            result.setValid(false);
            result.setMessage("密码必须包含特殊字符");
            return result;
        }
        
        result.setValid(true);
        result.setMessage("密码验证通过");
        return result;
    }
    
    private static String getComplexityRequirement() {
        return SystemSettingsConfig.getPasswordComplexity();
    }
    
    public static class ValidationResult {
        private boolean valid;
        private String message;
        
        public boolean isValid() {
            return valid;
        }
        
        public void setValid(boolean valid) {
            this.valid = valid;
        }
        
        public String getMessage() {
            return message;
        }
        
        public void setMessage(String message) {
            this.message = message;
        }
    }
}
