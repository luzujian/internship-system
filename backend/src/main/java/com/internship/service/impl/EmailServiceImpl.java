package com.internship.service.impl;

import com.internship.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.util.Properties;

@Slf4j
@Service
public class EmailServiceImpl implements EmailService {
    @org.springframework.beans.factory.annotation.Autowired
    private com.internship.config.LocalRuntimeGuard localRuntimeGuard;

    @Value("${spring.mail.host:smtp.qq.com}")
    private String mailHost;

    @Value("${spring.mail.port:465}")
    private Integer mailPort;
    
    @Value("${spring.mail.username:}")
    private String mailUsername;
    
    @Value("${spring.mail.password:}")
    private String mailPassword;
    
    @Value("${mail.allow-local-send:false}")
    private boolean allowLocalSend;

    private boolean blockedByLocalGuard() {
        return localRuntimeGuard != null && localRuntimeGuard.isLocal() && !allowLocalSend;
    }

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
    public boolean isConfigured() {
        if (blockedByLocalGuard()) return false;
        return mailHost != null && !mailHost.isBlank()
                && mailPort != null && mailPort > 0
                && mailUsername != null && !mailUsername.isBlank()
                && mailPassword != null && !mailPassword.isBlank();
    }

    private static String sanitizeSender(String senderName) {
        if (senderName == null || senderName.isBlank()) return "DeepIntern";
        String clean = senderName.replaceAll("[\\r\\n]+", " ").trim();
        if (clean.isEmpty()) return "DeepIntern";
        if (clean.length() > 60) clean = clean.substring(0, 60);
        return clean;
    }

    @Override
    public boolean sendEmail(String to, String subject, String content) {
        return sendEmail(to, subject, content, "DeepIntern");
    }

    @Override
    public boolean sendEmail(String to, String subject, String content, String senderName) {
        if (blockedByLocalGuard()) return false;
        try {
            if (mailUsername == null || mailUsername.isEmpty()) {
                log.warn("邮件服务未配置");
                return false;
            }
            
            JavaMailSender mailSender = getMailSender();
            jakarta.mail.internet.MimeMessage message = mailSender.createMimeMessage();
            org.springframework.mail.javamail.MimeMessageHelper helper =
                    new org.springframework.mail.javamail.MimeMessageHelper(message, false, "UTF-8");
            String personal = sanitizeSender(senderName);
            try {
                helper.setFrom(mailUsername, personal);
            } catch (java.io.UnsupportedEncodingException encodingFallback) {
                helper.setFrom(mailUsername);
            }
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, false);
            
            mailSender.send(message);
            log.info("邮件发送成功: 收件人={}, 主题={}, 发件人={}", to, subject, personal);
            return true;
        } catch (Exception e) {
            log.error("邮件发送失败: {}", e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public DeliveryOutcome sendWithReceipt(String to, String subject, String content, String deliveryId) {
        return sendWithReceipt(to, subject, content, deliveryId, "DeepIntern");
    }

    @Override
    public DeliveryOutcome sendWithReceipt(String to, String subject, String content, String deliveryId, String senderName) {
        if (!isConfigured()) return new DeliveryOutcome("FAILED", "邮件通道未配置或当前环境禁止外发。", false);
        try {
            JavaMailSender sender = getMailSender();
            jakarta.mail.internet.MimeMessage message = sender.createMimeMessage();
            org.springframework.mail.javamail.MimeMessageHelper helper = new org.springframework.mail.javamail.MimeMessageHelper(message, "UTF-8");
            String personal = sanitizeSender(senderName);
            try {
                helper.setFrom(mailUsername, personal);
            } catch (java.io.UnsupportedEncodingException encodingFallback) {
                helper.setFrom(mailUsername);
            }
            helper.setTo(to); helper.setSubject(subject); helper.setText(content, false);
            message.setHeader("X-Intern-Automation-Delivery", deliveryId);
            sender.send(message);
            return new DeliveryOutcome("SENT", "邮件服务已受理。", false);
        } catch (org.springframework.mail.MailAuthenticationException | org.springframework.mail.MailParseException e) {
            return new DeliveryOutcome("FAILED", "邮件账号认证或收件地址不正确，邮件未受理。", false);
        } catch (Exception e) {
            for (Throwable cause=e;cause!=null;cause=cause.getCause()) {
                if (cause instanceof java.net.ConnectException || cause instanceof java.net.UnknownHostException
                        || cause.getClass().getSimpleName().equals("MailConnectException"))
                    return new DeliveryOutcome("FAILED", "尚未连接邮件服务，可稍后重试。", true);
            }
            return new DeliveryOutcome("UNKNOWN", "投递未返回明确回执，请核实邮件服务记录。", false);
        }
    }

    @Override
    public boolean sendTestEmail() {
        if (blockedByLocalGuard()) return false;
        try {
            if (mailUsername == null || mailUsername.isEmpty()) {
                log.warn("邮件服务未配置");
                return false;
            }
            
            JavaMailSender mailSender = getMailSender();
            jakarta.mail.internet.MimeMessage message = mailSender.createMimeMessage();
            org.springframework.mail.javamail.MimeMessageHelper helper =
                    new org.springframework.mail.javamail.MimeMessageHelper(message, false, "UTF-8");
            try {
                helper.setFrom(mailUsername, "DeepIntern");
            } catch (java.io.UnsupportedEncodingException encodingFallback) {
                helper.setFrom(mailUsername);
            }
            helper.setTo(mailUsername);
            helper.setSubject("系统设置 - 邮件测试");
            helper.setText("这是一封测试邮件，如果您收到此邮件，说明邮件配置正确。\n\n发送时间: " + new java.util.Date(), false);
            
            mailSender.send(message);
            log.info("测试邮件发送成功");
            return true;
        } catch (Exception e) {
            log.error("测试邮件发送失败: {}", e.getMessage(), e);
            return false;
        }
    }
}
