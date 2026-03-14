package com.gdmu.entity;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.Date;

/**
 * 学生实习状态实体类
 */
@Data
public class StudentInternshipStatus {
    private Long id;
    
    @NotNull(message = "学生ID不能为空")
    private Long studentId;
    
    @Min(value = 0, message = "实习状态值无效")
    private Integer status; // 实习状态（0未找到，1已有offer，2已确定，3已结束）
    
    private Long companyId;
    
    private Long positionId;
    
    private Boolean hasComplaint; // 是否有投诉
    
    private Boolean isDelayed; // 是否延期
    
    private Boolean isInterrupted; // 是否中断
    
    private Date createTime;
    private Date updateTime;
    
    private Integer recallStatus;
    private String recallReason;
    private Date recallApplyTime;
    private Date recallAuditTime;
    private Long recallReviewerId;
    private String recallAuditRemark;
    
    private Integer companyConfirmStatus;
    
    private Date internshipStartTime;
    private Date internshipEndTime;
    private Integer internshipDuration;
    private String feedback;
    private String remark;
    
    private StudentUser student;
    private CompanyUser company;
    private Position position;
}