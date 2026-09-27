package com.internship.entity;

import com.internship.entity.dto.RequiredCompanyRating;
import lombok.Data;

import java.util.Date;
import java.util.List;

/** 学生的一次最终实习鉴定表提交。 */
@Data
public class InternshipAppraisalSubmission {
    private Long id;
    private Long studentId;
    private String status;
    private Date submittedAt;
    private Date reviewedAt;
    private Long reviewerId;
    private String reviewComment;
    private Date createTime;
    private Date updateTime;
    private List<InternshipAppraisalFile> files;
    private List<RequiredCompanyRating> requiredCompanyRatings;

    // 由当前学生真实实习状态派生，用于前端判断是否已进入鉴定提交窗口。
    private Integer internshipStatus;
    private Boolean internshipEnded;
}
