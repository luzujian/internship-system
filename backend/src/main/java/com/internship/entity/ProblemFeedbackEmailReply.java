package com.internship.entity;

import lombok.Data;
import java.util.Date;

@Data
public class ProblemFeedbackEmailReply {
    private Long id;
    private Long feedbackId;
    private String requestId;
    private String recipientEmail;
    private String subject;
    private String replyContent;
    private String mailBody;
    private Long adminId;
    private String adminName;
    private String feedbackStatus;
    private String deliveryStatus;
    private String deliveryDetail;
    private Date createdAt;
    private Date finishedAt;
}
