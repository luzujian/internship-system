package com.gdmu.entity;

import lombok.Data;
import java.util.Date;

/**
 * 撤回申请记录实体类
 */
@Data
public class ApplicationWithdrawalRecord {
    private Long id;

    private Long statusId; // 实习状态 ID

    private Long applicantId; // 申请人 ID

    private String applicantRole; // 申请人身份：ROLE_STUDENT/ROLE_COMPANY

    private String withdrawalReason; // 撤回原因

    private Date withdrawalTime; // 撤回时间

    private Date createTime;

    private Date updateTime;

    // 关联信息（用于前端展示）
    private StudentUser student;
    private CompanyUser company;
    private Position position;
    private StudentInternshipStatus internshipStatus;
}
