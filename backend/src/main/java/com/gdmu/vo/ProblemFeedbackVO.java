package com.gdmu.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.util.Date;

@Data
public class ProblemFeedbackVO {
    private Long id;

    private String userType;

    private Long userId;

    private String userName;

    private String userAccount;

    private String title;

    private String content;

    private String feedbackType;

    private String status;

    private String attachmentUrl;

    private String adminReply;

    private Long adminId;

    private String adminName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date replyTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date resolveTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;
}
