package com.internship.service;

public interface EmailService {
    record DeliveryOutcome(String status, String detail, boolean retryable) {}

    default DeliveryOutcome sendWithReceipt(String to, String subject, String content, String deliveryId) {
        if (!isConfigured()) return new DeliveryOutcome("FAILED", "邮件服务尚未配置。", false);
        return sendEmail(to, subject, content)
                ? new DeliveryOutcome("SENT", "邮件服务已受理。", false)
                : new DeliveryOutcome("UNKNOWN", "邮件服务是否受理暂时无法确认，请核实后处理。", false);
    }

    boolean sendEmail(String to, String subject, String content);

    default boolean sendEmail(String to, String subject, String content, String senderName) {
        return sendEmail(to, subject, content);
    }

    default DeliveryOutcome sendWithReceipt(String to, String subject, String content, String deliveryId, String senderName) {
        return sendWithReceipt(to, subject, content, deliveryId);
    }

    static String roleLabel(String teacherType) {
        if (teacherType == null) return "";
        return switch (teacherType) {
            case "COUNSELOR" -> "辅导员";
            case "DEPARTMENT" -> "系室教师";
            case "COLLEGE" -> "学院教师";
            default -> teacherType;
        };
    }

    static String senderDisplayName(String teacherType, String teacherName) {
        String role = roleLabel(teacherType);
        String name = teacherName == null ? "" : teacherName.replaceAll("[\\r\\n]+", " ").replaceAll("[<>\"',;]+", "").trim();
        if (name.length() > 20) name = name.substring(0, 20);
        if (role.isEmpty() && name.isEmpty()) return "DeepIntern";
        if (name.isEmpty()) return "DeepIntern-" + role;
        if (role.isEmpty()) return "DeepIntern-" + name;
        return "DeepIntern-" + role + "-" + name;
    }

    /** 邮件通道是否已完成主机/账号/密码配置（不代表网络可达）。 */
    boolean isConfigured();

    boolean sendTestEmail();
}