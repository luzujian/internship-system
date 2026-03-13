package com.gdmu.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.Date;

@Data
public class ProblemFeedback {
    private Long id;

    @NotNull(message = "用户类型不能为空")
    private String userType;

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotBlank(message = "用户姓名不能为空")
    @Size(max = 100, message = "用户姓名长度不能超过100个字符")
    private String userName;

    @NotBlank(message = "用户账号不能为空")
    @Size(max = 100, message = "用户账号长度不能超过100个字符")
    private String userAccount;

    @NotBlank(message = "反馈标题不能为空")
    @Size(min = 2, max = 255, message = "反馈标题长度必须在2-255个字符之间")
    private String title;

    @NotBlank(message = "反馈内容不能为空")
    private String content;

    private String feedbackType;

    private String status;

    private String priority;

    @Size(max = 500, message = "附件URL长度不能超过500个字符")
    private String attachmentUrl;

    private String adminReply;

    private Long adminId;

    @Size(max = 100, message = "管理员姓名长度不能超过100个字符")
    private String adminName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date replyTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date resolveTime;

    private Integer deleted;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;
}
