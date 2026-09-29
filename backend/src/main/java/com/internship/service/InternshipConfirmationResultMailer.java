package com.internship.service;

import com.internship.config.EmailNotifyScope;
import com.internship.config.SystemSettingsConfig;
import com.internship.entity.StudentUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** Sends one confirmation-result email to the student's currently bound address. */
@Slf4j
@Service
public class InternshipConfirmationResultMailer {

    @Autowired
    private StudentUserService studentUserService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private AgentNotificationGuardService notificationGuard;

    @Async
    public void send(InternshipConfirmationResultNotification notification) {
        if (notification == null || !notification.supportsEmail()) {
            return;
        }
        if (!SystemSettingsConfig.isEmailNotifyScopeEnabled(EmailNotifyScope.INTERNSHIP_CONFIRMATION)) {
            log.info("系统邮件提醒已关闭（实习确认结果），跳过结果邮件: recordId={}, status={}",
                    notification.recordId(), notification.status());
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
                log.info("实习确认结果邮件已去重: recordId={}, status={}, reason={}",
                        notification.recordId(), notification.status(), claim.reason());
                return;
            }
        } catch (RuntimeException e) {
            log.warn("实习确认结果邮件去重记录不可用，继续发送一次: recordId={}, status={}, error={}",
                    notification.recordId(), notification.status(), e.getMessage());
        }

        String deliveryState = "UNKNOWN";
        try {
            StudentUser student = studentUserService.findById(notification.studentId());
            if (student == null || !Integer.valueOf(1).equals(student.getStatus())) {
                deliveryState = "CANCELLED";
                log.info("实习确认结果邮件未发送，学生不存在或已停用: studentId={}", notification.studentId());
                return;
            }

            String recipient = student.getEmail() == null ? "" : student.getEmail().trim();
            if (recipient.isEmpty()) {
                deliveryState = "CANCELLED";
                log.info("实习确认结果邮件未发送，学生未绑定邮箱: studentId={}", notification.studentId());
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
            log.info("实习确认结果邮件处理完成: recordId={}, status={}, studentId={}, deliveryState={}",
                    notification.recordId(), notification.status(), notification.studentId(), deliveryState);
        } catch (RuntimeException e) {
            deliveryState = "UNKNOWN";
            log.error("实习确认结果邮件发送异常: recordId={}, status={}, studentId={}",
                    notification.recordId(), notification.status(), notification.studentId(), e);
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
            log.warn("实习确认结果邮件回执写入失败: key={}, state={}, error={}",
                    claim.key(), state, e.getMessage());
        }
    }

    private Message buildMessage(InternshipConfirmationResultNotification notification, StudentUser student) {
        String studentName = student.getName() == null || student.getName().isBlank()
                ? "同学" : student.getName().trim();
        String companyName = notification.companyName() == null || notification.companyName().isBlank()
                ? "企业" : notification.companyName().trim();
        String positionName = notification.positionName() == null || notification.positionName().isBlank()
                ? "实习岗位" : notification.positionName().trim();

        boolean confirmed = notification.status() == InternshipConfirmationResultNotification.CONFIRMED;
        String subject = confirmed
                ? "【DeepIntern】实习确认表已通过"
                : "【DeepIntern】实习确认表未通过";
        String result = confirmed
                ? "企业已通过你的实习确认表，实习安排已生效。"
                : "很遗憾，企业未通过你的实习确认表。";
        String nextStep = confirmed
                ? "请登录 DeepIntern 查看实习安排，并按要求开展实习。"
                : "请登录 DeepIntern 查看详情；如有疑问，可与企业或指导老师联系。";

        StringBuilder body = new StringBuilder();
        body.append(studentName).append("同学，你好：").append(System.lineSeparator())
                .append(System.lineSeparator())
                .append("企业：").append(companyName).append(System.lineSeparator())
                .append("岗位：").append(positionName).append(System.lineSeparator())
                .append("结果：").append(result).append(System.lineSeparator());
        if (!confirmed) {
            String reason = notification.rejectReason() == null ? "" : notification.rejectReason().trim();
            body.append("拒绝原因：").append(reason.isEmpty() ? "企业未填写拒绝原因" : reason)
                    .append(System.lineSeparator());
        }
        body.append(System.lineSeparator())
                .append(nextStep).append(System.lineSeparator())
                .append(System.lineSeparator())
                .append("本邮件由 DeepIntern 自动发送，请勿直接回复。");
        return new Message(subject, body.toString());
    }

    private record Message(String subject, String body) {}
}
