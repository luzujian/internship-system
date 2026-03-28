package com.gdmu.service.impl;

import com.gdmu.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.util.Properties;

@Slf4j
@Service
public class EmailServiceImpl implements EmailService {
    
    @Value("${spring.mail.host:smtp.qq.com}")
    private String mailHost;
    
    @Value("${spring.mail.port:465}")
    private Integer mailPort;
    
    @Value("${spring.mail.username:}")
    private String mailUsername;
    
    @Value("${spring.mail.password:}")
    private String mailPassword;
    
    private JavaMailSender getMailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(mailHost);
        mailSender.setPort(mailPort);
        mailSender.setUsername(mailUsername);
        mailSender.setPassword(mailPassword);
        
        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtps");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ssl.enable", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");
        props.put("mail.smtp.writetimeout", "5000");
        
        return mailSender;
    }
    
    @Override
    public boolean sendEmail(String to, String subject, String content) {
        try {
            if (mailUsername == null || mailUsername.isEmpty()) {
                log.warn("邮件服务未配置");
                return false;
            }
            
            JavaMailSender mailSender = getMailSender();
            
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailUsername);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(content);
            
            mailSender.send(message);
            log.info("邮件发送成功: 收件人={}, 主题={}", to, subject);
            return true;
        } catch (Exception e) {
            log.error("邮件发送失败: {}", e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public boolean sendTestEmail() {
        try {
            if (mailUsername == null || mailUsername.isEmpty()) {
                log.warn("邮件服务未配置");
                return false;
            }
            
            JavaMailSender mailSender = getMailSender();
            
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailUsername);
            message.setTo(mailUsername);
            message.setSubject("系统设置 - 邮件测试");
            message.setText("这是一封测试邮件，如果您收到此邮件，说明邮件配置正确。\n\n发送时间: " + new java.util.Date());
            
            mailSender.send(message);
            log.info("测试邮件发送成功");
            return true;
        } catch (Exception e) {
            log.error("测试邮件发送失败: {}", e.getMessage(), e);
            return false;
        }
    }
}
