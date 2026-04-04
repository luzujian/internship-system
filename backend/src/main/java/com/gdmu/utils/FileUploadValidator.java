package com.gdmu.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
public class FileUploadValidator {
    
    private static final long MAX_FILE_SIZE_MB = 10;
    
    public static ValidationResult validateFile(MultipartFile file) {
        ValidationResult result = new ValidationResult();
        
        if (file == null || file.isEmpty()) {
            result.setValid(false);
            result.setMessage("文件不能为空");
            return result;
        }
        
        long maxSizeBytes = MAX_FILE_SIZE_MB * 1024 * 1024;
        
        if (file.getSize() > maxSizeBytes) {
            result.setValid(false);
            result.setMessage("文件大小不能超过" + MAX_FILE_SIZE_MB + "MB");
            return result;
        }
        
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.lastIndexOf('.') == -1) {
            result.setValid(false);
            result.setMessage("文件格式不支持");
            return result;
        }
        
        String extension = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
        
        String allowedTypes = "image,document,archive";
        if (!isFileTypeAllowed(extension, allowedTypes)) {
            result.setValid(false);
            result.setMessage("不支持的文件类型: " + extension);
            return result;
        }
        
        result.setValid(true);
        result.setMessage("文件验证通过");
        return result;
    }
    
    private static boolean isFileTypeAllowed(String extension, String allowedTypes) {
        if (allowedTypes.contains("image")) {
            if (extension.matches("jpg|jpeg|png|gif|bmp|svg")) {
                return true;
            }
        }
        
        if (allowedTypes.contains("document")) {
            if (extension.matches("pdf|doc|docx|ppt|pptx|xls|xlsx|txt")) {
                return true;
            }
        }
        
        if (allowedTypes.contains("archive")) {
            if (extension.matches("zip|rar|7z")) {
                return true;
            }
        }
        
        return false;
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
