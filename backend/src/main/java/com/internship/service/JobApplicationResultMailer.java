package com.internship.service;

import com.internship.config.EmailNotifyScope;
import com.internship.config.SystemSettingsConfig;
import com.internship.entity.StudentUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** Sends one result email to the student's currently bound address. */
@Slf4j
@Service
public class JobApplicationResultMailer {

    @Autowired
    private StudentUserService studentUserService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private AgentNotificationGuardService notificationGuard;

    @Async
    public void send(JobApplicationResultNotification notification) {
        if (notification == null || !notification.supportsEmail()) {
            return;
        }
        if (!SystemSettingsConfig.isEmailNotifyScopeEnabled(EmailNotifyScope.JOB_APPLICATION)) {
            log.info("系统邮件提醒已关闭（岗位申请结果），跳过结果邮件: applicationId={}, status={}",
                    notification.applicationId(), notification.status());
            return;
        }

        AgentNotificationGuardService.Claim claim = null;
        try {
            claim = notificationGuard.claim(
                    notification.studentId(),
                    notification.todoId(),
                    "EMAIL",
                    null,
                    Integer.MAX_VALUE);
            if (!claim.acquired()) {
                log.info("申请结果邮件已去重: applicationId={}, status={}, reason={}",
                        notification.applicationId(), notification.status(), claim.reason());
                return;
            }
        } catch (RuntimeException e) {
            log.warn("申请结果邮件去重记录不可用，继续发送一次: applicationId={}, status={}, error={}",
                    notification.applicationId(), notification.status(), e.getMessage());
        }

        String deliveryState = "UNKNOWN";
        try {
            StudentUser student = studentUserService.findById(notification.studentId());
            if (student == null || !Integer.valueOf(1).equals(student.getStatus())) {
                deliveryState = "CANCELLED";
                log.info("申请结果邮件未发送，学生不存在或已停用: studentId={}", notification.studentId());
                return;
            }

            String recipient = student.getEmail() == null ? "" : student.getEmail().trim();
            if (recipient.isEmpty()) {
                deliveryState = "CANCELLED";
                log.info("申请结果邮件未发送，学生未绑定邮箱: studentId={}", notification.studentId());
                return;
            }

            Message message = buildMessage(notification, student);
            EmailService.DeliveryOutcome outcome = emailService.sendWithReceipt(
                    recipient,
                    message.subject(),
                    message.body(),
                    notification.deliveryId(),
                    "DeepIntern");
            deliveryState = outcome == null ? "UNKNOWN" : outcome.status();
            log.info("申请结果邮件处理完成: applicationId={}, status={}, studentId={}, deliveryState={}",
                    notification.applicationId(), notification.status(), notification.studentId(), deliveryState);
        } catch (RuntimeException e) {
            deliveryState = "UNKNOWN";
            log.error("申请结果邮件发送异常: applicationId={}, status={}, studentId={}",
                    notification.applicationId(), notification.status(), notification.studentId(), e);
        } finally {
            finishGuard(claim, deliveryState);
        }
    }

    private void finishGuard(AgentNotificationGuardService.Claim claim, String state) {
        if (claim == null || !claim.acquired()) {
            return;
        }
        try {
            notificationGuard.finish(claim, state, null);
        } catch (RuntimeException e) {
            log.warn("申请结果邮件回执写入失败: key={}, state={}, error={}",
                    claim.key(), state, e.getMessage());
        }
    }

    private Message buildMessage(JobApplicationResultNotification notification, StudentUser student) {
        String studentName = student.getName() == null || student.getName().isBlank()
                ? "同学" : student.getName().trim();
        String companyName = notification.companyName() == null || notification.companyName().isBlank()
                ? "企业" : notification.companyName().trim();
        String positionName = notification.positionName() == null || notification.positionName().isBlank()
                ? "所投岗位" : notification.positionName().trim();

        String subject;
        String result;
        String nextStep;
        if (notification.directHire() && "interview_passed".equals(notification.status())) {
            subject = "【DeepIntern】企业已直接录取您";
            result = "企业已直接录取您（免面试）。";
            nextStep = "请登录 DeepIntern，尽快查看并提交实习确认表。";
        } else {
            switch (notification.status()) {
                case "approved" -> {
                    subject = "【DeepIntern】岗位申请已通过初审";
                    result = "企业已通过您的岗位申请初审。";
                    nextStep = "请登录 DeepIntern，留意后续面试安排。";
                }
                case "rejected" -> {
                    subject = "【DeepIntern】岗位申请结果通知";
                    result = "很遗憾，本次岗位申请未通过。";
                    nextStep = "您可以登录 DeepIntern 查看其他岗位机会。";
                }
                case "interview_passed" -> {
                    subject = "【DeepIntern】面试通过通知";
                    result = "您已通过该岗位的面试。";
                    nextStep = "请登录 DeepIntern，办理后续实习确认。";
                }
                case "interview_failed" -> {
                    subject = "【DeepIntern】面试结果通知";
                    result = "很遗憾，本次面试未通过。";
                    nextStep = "您可以登录 DeepIntern 查看其他岗位机会。";
                }
                case "hired" -> {
                    subject = "【DeepIntern】企业录用通知";
                    result = "企业已录用您。";
                    nextStep = "请登录 DeepIntern，办理后续实习确认。";
                }
                default -> throw new IllegalArgumentException("Unsupported application result: " + notification.status());
            }
        }

        String body = studentName + "同学，你好：" + System.lineSeparator()
                + System.lineSeparator()
                + "企业：" + companyName + System.lineSeparator()
                + "岗位：" + positionName + System.lineSeparator()
                + "结果：" + result + System.lineSeparator()
                + System.lineSeparator()
                + nextStep + System.lineSeparator()
                + ("rejected".equals(notification.status())
                        && notification.rejectReason() != null
                        && !notification.rejectReason().isBlank()
                    ? "拒绝理由：" + notification.rejectReason().trim() + System.lineSeparator()
                    : "")
                + System.lineSeparator()
                + "本邮件由 DeepIntern 自动发送，请勿直接回复。";
        return new Message(subject, body);
    }

    private record Message(String subject, String body) {}
}
