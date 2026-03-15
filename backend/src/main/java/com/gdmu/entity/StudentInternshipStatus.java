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
    
    private Integer recallStatus; // 撤回状态：0-未撤回，1-已撤回（待确认）,2-已确认撤回
    private String recallReason;
    private Date recallApplyTime; // 撤回申请时间（自动撤回时即为撤回时间）
    private Date recallAuditTime; // 撤回审核时间
    private Long recallReviewerId; // 撤回审核人ID
    private String recallAuditRemark; // 撤回审核备注
    
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