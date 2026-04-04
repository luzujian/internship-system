package com.gdmu.service;

public interface EmailService {
    
    boolean sendEmail(String to, String subject, String content);
    
    boolean sendTestEmail();
}
