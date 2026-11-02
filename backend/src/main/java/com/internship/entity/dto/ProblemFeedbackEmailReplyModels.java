package com.internship.entity.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.internship.entity.ProblemFeedback;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public final class ProblemFeedbackEmailReplyModels {
    private ProblemFeedbackEmailReplyModels() {}

    public record Request(
            @NotNull UUID requestId,
            @NotBlank @Email @Size(max = 254) String recipientEmail,
            @NotBlank @Size(max = 10000) String adminReply,
            @NotBlank @Pattern(regexp = "processing|resolved|closed") String status,
            @NotNull Long feedbackVersion,
            Long previousReplyId) {}

    public record Receipt(Long id, String requestId, String recipientEmail, String subject,
                          String replyContent, String adminName, String feedbackStatus,
                          String deliveryStatus, String deliveryDetail,
                          @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8") Date createdAt,
                          @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8") Date finishedAt) {}

    public record Context(ProblemFeedback feedback, String recipientEmail, String senderName,
                          String senderEmail, boolean mailConfigured, long feedbackVersion,
                          List<Receipt> history) {}
}
