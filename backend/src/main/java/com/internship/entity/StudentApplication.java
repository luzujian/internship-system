package com.internship.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.Date;
import java.util.Map;

/**
 * 学生申请实体类
 * 用于处理自主实习申请、单位变更申请、考研延迟申请等
 */
@Data
public class StudentApplication {
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    private java.util.List<Long> agreementFileIds;
    private Long id;
    
    @NotNull(message = "学生ID不能为空")
    private Long studentId;
    
    @NotBlank(message = "学生姓名不能为空")
    private String studentName;
    
    @NotBlank(message = "学号不能为空")
    private String studentUserId;
    
    private String grade;
    
    private String className;
    
    @NotBlank(message = "申请类型不能为空")
    private String applicationType; // selfPractice-自主实习申请, unitChange-单位变更申请, delay-考研延迟申请
    
    @NotBlank(message = "申请理由不能为空")
    private String reason;
    
    private String company; // 自主实习申请：实习单位

    private String positionName; // 自主实习申请：实习岗位

    private String companyCity; // 自主实习申请：单位所在城市

    private String hrManagerName; // 自主实习申请：人事经理姓名

    private String hrManagerPhone; // 自主实习申请：人事经理联系电话

    private String oldCompany; // 单位变更申请：原实习单位
    
    private String newCompany; // 单位变更申请：新实习单位
    
    private Map<String, String> materials; // 申请材料，JSON格式存储
    
    private String status; // pending-待审核, approved-已通过, rejected-已驳回, withdrawn-学生已撤回
    
    private String rejectReason; // 驳回理由
    
    private Long reviewerId; // 审核人ID
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date reviewTime;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date applyTime;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;
    
    // 关联对象
    private StudentUser student; // 学生信息

    // 查询时动态计算字段（不落库）：自主实习单位是否命中系统内有效的校企合作企业
    private Boolean cooperativeCompany;
    private Long cooperativeCompanyId;
}
