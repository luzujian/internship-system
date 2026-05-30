package com.internship.service;

public interface EmailService {
    
    boolean sendEmail(String to, String subject, String content);
    
    boolean sendTestEmail();
}
