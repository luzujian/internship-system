package com.gdmu.exception;

public class BackupException extends RuntimeException {
    
    private String errorCode;
    
    public BackupException(String message) {
        super(message);
    }
    
    public BackupException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public BackupException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public BackupException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
}