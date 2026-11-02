package com.internship.service;

import com.internship.entity.ProblemFeedback;
import com.internship.entity.ProblemFeedbackEmailReply;
import com.internship.entity.User;
import com.internship.entity.dto.ProblemFeedbackEmailReplyModels.*;
import com.internship.exception.BusinessException;
import com.internship.mapper.ProblemFeedbackEmailReplyMapper;
import com.internship.mapper.ProblemFeedbackMapper;
import com.internship.utils.CurrentHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ProblemFeedbackEmailReplyService {
    private static final String SENDER_NAME = "DeepIntern管理员";
    private static final Pattern EMAIL = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    private static final Set<String> FINAL_STATES = Set.of("SENT", "FAILED", "UNKNOWN");
    private final ProblemFeedbackMapper feedbacks;
    private final ProblemFeedbackEmailReplyMapper replies;
    private final EmailService email;
    private final TransactionTemplate transaction;
    private final String senderEmail;

    public ProblemFeedbackEmailReplyService(ProblemFeedbackMapper feedbacks,
            ProblemFeedbackEmailReplyMapper replies, EmailService email,
            PlatformTransactionManager transactionManager,
            @Value("${spring.mail.username:}") String senderEmail) {
        this.feedbacks = feedbacks;
        this.replies = replies;
        this.email = email;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.senderEmail = senderEmail;
    }

    public Context context(Long feedbackId) {
        requireAdmin();
        ProblemFeedback feedback = requireFeedback(feedbacks.findById(feedbackId));
        return new Context(feedback, recipient(feedbackId), SENDER_NAME, senderEmail,
                email.isConfigured(), version(feedback), history(feedbackId).stream().map(this::receipt).toList());
    }

    public Receipt send(Long feedbackId, Request request) {
        User admin = requireAdmin();
        validate(request);
        // Commit the claim before SMTP. The feedback row serializes different admins/tabs.
        Claim claim = transaction.execute(status -> claim(feedbackId, request, admin));
        if (!claim.created()) return receipt(claim.reply());
        ProblemFeedbackEmailReply reply = claim.reply();
        EmailService.DeliveryOutcome outcome;
        try {
            outcome = email.sendWithReceipt(reply.getRecipientEmail(), reply.getSubject(), reply.getMailBody(),
                    "problem-feedback-" + feedbackId + "-" + reply.getRequestId(), SENDER_NAME);
        } catch (RuntimeException failure) {
            log.warn("问题反馈邮件发送结果不确定: feedbackId={}, replyId={}", feedbackId, reply.getId());
            outcome = null;
        }
        String state = outcome != null && outcome.status() != null && FINAL_STATES.contains(outcome.status()) ? outcome.status() : "UNKNOWN";
        String detail = switch (state) {
            case "SENT" -> "邮件服务器已接收邮件。";
            case "FAILED" -> outcome.detail() == null ? "邮件未发出，可检查后重新发送。" : outcome.detail();
            default -> "暂时无法确认邮件是否发出，请核实邮件服务记录，避免重复发送。";
        };
        Date finished = new Date();
        try {
            Integer updated = transaction.execute(status -> replies.finish(reply.getId(), state, clip(detail, 500), finished));
            if (!Objects.equals(updated, 1)) throw new IllegalStateException("Missing delivery receipt");
            reply.setDeliveryStatus(state);
            reply.setDeliveryDetail(detail);
            reply.setFinishedAt(finished);
        } catch (RuntimeException persistenceFailure) {
            // A persisted SENDING claim blocks all resends if the final receipt cannot be saved.
            log.error("问题反馈邮件回执保存失败: feedbackId={}, replyId={}", feedbackId, reply.getId(), persistenceFailure);
            reply.setDeliveryStatus("UNKNOWN");
            reply.setDeliveryDetail("未能保存最终发送结果，请核实邮件服务记录后处理。");
        }
        return receipt(reply);
    }

    private Claim claim(Long feedbackId, Request request, User admin) {
        ProblemFeedback feedback = requireFeedback(feedbacks.findByIdForUpdate(feedbackId));
        ProblemFeedbackEmailReply existing = replies.findByRequestId(request.requestId().toString());
        if (existing != null) {
            if (!Objects.equals(feedbackId, existing.getFeedbackId())
                    || !existing.getReplyContent().equals(request.adminReply().trim())
                    || !existing.getRecipientEmail().equalsIgnoreCase(request.recipientEmail().trim())
                    || !existing.getFeedbackStatus().equals(request.status())) {
                throw new BusinessException("发送请求已变化，请重新打开回复窗口。");
            }
            return new Claim(existing, false);
        }
        List<ProblemFeedbackEmailReply> history = history(feedbackId);
        ProblemFeedbackEmailReply latest = history.isEmpty() ? null : history.getFirst();
        if (!Objects.equals(request.previousReplyId(), latest == null ? null : latest.getId())) {
            throw new BusinessException("邮件回复记录已更新，请重新检查后再发送。");
        }
        if (latest != null && Set.of("SENDING", "UNKNOWN").contains(latest.getDeliveryStatus())) {
            throw new BusinessException("上一封邮件仍在发送或结果待确认，请先核实，避免重复发送。");
        }
        if (request.feedbackVersion() != version(feedback)) {
            throw new BusinessException("反馈内容或处理状态已更新，请重新检查后再发送。");
        }
        String recipient = recipient(feedbackId);
        if (!EMAIL.matcher(recipient).matches()) throw new BusinessException("反馈人尚未填写有效邮箱，无法发送邮件。");
        if (!recipient.equalsIgnoreCase(request.recipientEmail().trim())) {
            throw new BusinessException("反馈人的邮箱已变更，请重新检查收件邮箱后再发送。");
        }
        if (latest != null && "SENT".equals(latest.getDeliveryStatus())
                && latest.getRecipientEmail().equalsIgnoreCase(recipient)
                && latest.getReplyContent().equals(request.adminReply().trim())) {
            throw new BusinessException("相同内容已发送，请查看发送记录。");
        }
        ProblemFeedbackEmailReply reply = new ProblemFeedbackEmailReply();
        reply.setFeedbackId(feedbackId);
        reply.setRequestId(request.requestId().toString());
        reply.setRecipientEmail(recipient);
        reply.setSubject("【DeepIntern】问题反馈回复 #" + feedbackId + "：" + clip(text(feedback.getTitle()).replaceAll("[\\r\\n]+", " "), 160));
        reply.setReplyContent(request.adminReply().trim());
        reply.setAdminId(admin.getId());
        reply.setAdminName(clip(text(admin.getName()).isBlank() ? text(admin.getUsername()) : admin.getName(), 100));
        reply.setFeedbackStatus(request.status());
        reply.setDeliveryStatus("SENDING");
        reply.setDeliveryDetail("正在发送邮件。");
        reply.setCreatedAt(new Date());
        reply.setMailBody(text(feedback.getUserName()) + "，你好：\n\n"
                + "你提交的问题反馈已收到管理员回复。\n"
                + "反馈标题：" + text(feedback.getTitle()) + "\n\n"
                + reply.getReplyContent() + "\n\nDeepIntern管理员\n");
        if (replies.insert(reply) != 1
                || feedbacks.reply(feedbackId, reply.getReplyContent(), admin.getId(), reply.getAdminName(), request.status()) != 1) {
            throw new BusinessException("未能保存邮件发送记录，邮件未发送。");
        }
        return new Claim(reply, true);
    }

    private Receipt receipt(ProblemFeedbackEmailReply reply) {
        String state = reply.getDeliveryStatus();
        String detail = reply.getDeliveryDetail();
        if ("SENDING".equals(state) && new Date().getTime() - reply.getCreatedAt().getTime() > 120_000) {
            state = "UNKNOWN";
            detail = "邮件发送处理中断或尚未收到回执，请核实邮件服务记录，避免重复发送。";
        }
        return new Receipt(reply.getId(), reply.getRequestId(), reply.getRecipientEmail(), reply.getSubject(),
                reply.getReplyContent(), reply.getAdminName(), reply.getFeedbackStatus(), state, detail,
                reply.getCreatedAt(), reply.getFinishedAt());
    }

    private List<ProblemFeedbackEmailReply> history(Long feedbackId) {
        return replies.findHistory(feedbackId);
    }

    private String recipient(Long id) { return text(feedbacks.findRecipientEmail(id)).trim(); }
    private static long version(ProblemFeedback feedback) { return feedback.getUpdateTime() == null ? 0 : feedback.getUpdateTime().getTime(); }
    private static String text(String value) { return value == null ? "" : value; }
    private static String clip(String value, int limit) { return value.length() > limit ? value.substring(0, limit) : value; }

    private static User requireAdmin() {
        User user = CurrentHolder.getUser();
        if (user == null || user.getId() == null || !"ROLE_ADMIN".equals(user.getRole())) {
            throw new AccessDeniedException("仅管理员可以进行邮件回复");
        }
        return user;
    }

    private static ProblemFeedback requireFeedback(ProblemFeedback feedback) {
        if (feedback == null || Integer.valueOf(1).equals(feedback.getDeleted())) throw new BusinessException("反馈不存在或已删除");
        return feedback;
    }

    private static void validate(Request request) {
        if (request == null || request.requestId() == null || request.feedbackVersion() == null
                || request.adminReply() == null || request.adminReply().isBlank() || request.adminReply().length() > 10000
                || request.recipientEmail() == null || !EMAIL.matcher(request.recipientEmail().trim()).matches()
                || !Set.of("processing", "resolved", "closed").contains(text(request.status()))) {
            throw new BusinessException("请检查收件邮箱、回复内容和处理状态");
        }
    }

    private record Claim(ProblemFeedbackEmailReply reply, boolean created) {}
}
