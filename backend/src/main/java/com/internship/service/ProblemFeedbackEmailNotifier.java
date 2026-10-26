package com.internship.service;

import com.internship.config.EmailNotifyScope;
import com.internship.config.SystemSettingsConfig;
import com.internship.entity.AdminUser;
import com.internship.entity.ProblemFeedback;
import com.internship.mapper.AdminUserMapper;
import com.internship.mapper.ProblemFeedbackMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
public class ProblemFeedbackEmailNotifier {
    private final ProblemFeedbackMapper feedbackMapper;
    private final AdminUserMapper adminMapper;
    private final EmailService emailService;

    public ProblemFeedbackEmailNotifier(ProblemFeedbackMapper feedbackMapper, AdminUserMapper adminMapper,
                                       EmailService emailService) {
        this.feedbackMapper = feedbackMapper;
        this.adminMapper = adminMapper;
        this.emailService = emailService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSubmitted(ProblemFeedbackSubmittedEvent event) {
        if (event == null || event.feedbackId() == null
                || !SystemSettingsConfig.isEmailNotifyScopeEnabled(EmailNotifyScope.PROBLEM_FEEDBACK)) return;
        try {
            ProblemFeedback feedback = feedbackMapper.findById(event.feedbackId());
            if (feedback == null || Integer.valueOf(1).equals(feedback.getDeleted())) return;
            Set<String> recipients = new HashSet<>();
            String body = buildBody(feedback);
            for (AdminUser admin : adminMapper.findFeedbackEmailRecipients()) {
                if (!Integer.valueOf(1).equals(admin.getStatus()) || !"ROLE_ADMIN".equals(admin.getRole())) continue;
                String email = text(admin.getEmail()).trim();
                if (!email.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")
                        || !recipients.add(email.toLowerCase(Locale.ROOT))) continue;
                try {
                    EmailService.DeliveryOutcome result = emailService.sendWithReceipt(email,
                            "【DeepIntern】收到新的问题反馈", body,
                            "problem-feedback-" + feedback.getId() + "-admin-" + admin.getId());
                    log.info("问题反馈邮件提醒已处理: feedbackId={}, adminId={}, state={}",
                            feedback.getId(), admin.getId(), result == null ? "UNKNOWN" : result.status());
                } catch (RuntimeException failure) {
                    log.warn("问题反馈邮件发送失败: feedbackId={}, adminId={}", feedback.getId(), admin.getId(), failure);
                }
            }
        } catch (RuntimeException failure) {
            log.warn("问题反馈邮件提醒处理失败: feedbackId={}", event.feedbackId(), failure);
        }
    }

    private String buildBody(ProblemFeedback feedback) {
        String role = switch (text(feedback.getUserType())) {
            case "student" -> "学生";
            case "teacher" -> "教师";
            case "company" -> "企业";
            default -> "用户";
        };
        String content = text(feedback.getContent());
        if (content.length() > 2000) content = content.substring(0, 2000) + "\n（内容较长，请登录系统查看全文）";
        String submittedAt = feedback.getCreateTime() == null ? "" : feedback.getCreateTime().toInstant()
                .atZone(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return "管理员，你好：\n\n系统收到一条新的问题反馈，请及时查看处理。\n\n"
                + "反馈编号：" + feedback.getId() + "\n"
                + "提交者：" + text(feedback.getUserName()) + "（" + role + "）\n"
                + "账号：" + text(feedback.getUserAccount()) + "\n"
                + "提交时间：" + submittedAt + "\n"
                + "标题：" + text(feedback.getTitle()) + "\n\n"
                + "反馈内容：\n" + content + "\n\n"
                + "请登录 DeepIntern 管理员端，在首页的“问题反馈”中查看详情并处理。\n"
                + "本邮件由系统自动发送，请勿直接回复。";
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
