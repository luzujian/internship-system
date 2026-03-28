package com.gdmu.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class InternshipConfirmationRecord {
    private Long id;
    private Long studentId;
    private String studentName;
    private String studentUserId;
    private String gender;
    private String grade;
    private String major;
    private String className;
    private String contactPhone;
    private String email;
    private Long companyId;
    private String companyName;
    private String companyAddress;
    private String companyPhone;
    private Long positionId;
    private String positionName;
    private LocalDateTime internshipStartTime;
    private LocalDateTime internshipEndTime;
    private Integer internshipDuration;
    private String remark;
    private Integer status; // 0=待企业确认，1=企业已确认，2=企业已拒绝
    private Integer recallStatus = 0; // 撤回状态：0-未撤回，1-撤回申请中，2-已撤回
    private String recallReason; // 撤回原因
    private LocalDateTime recallApplyTime; // 撤回申请时间
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
