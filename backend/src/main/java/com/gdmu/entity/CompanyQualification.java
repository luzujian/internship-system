package com.gdmu.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 企业资质审核实体类
 */
@Data
public class CompanyQualification {
    private Long id;
    
    @NotNull(message = "企业ID不能为空")
    private Long companyId;
    
    @NotBlank(message = "企业名称不能为空")
    private String companyName;
    
    private String companyAddress;
    
    @NotBlank(message = "联系人不能为空")
    private String contactPerson;
    
    @NotBlank(message = "联系电话不能为空")
    private String contactPhone;
    
    private Map<String, String> qualifications; // 资质文件，JSON格式存储
    // businessLicense-营业执照, legalPersonId-法人身份证, organizationCode-组织机构代码证, basePhoto-牌匾照片
    
    private Boolean isPhoneVerified; // 是否已验证手机号
    
    private Boolean isInternshipBase; // 是否为实习基地
    
    private Boolean hasReceivedStudents; // 是否接收过学生
    
    private Integer currentEmployeeCount; // 我院毕业生在职员工数量
    
    private String internshipBaseLevel; // 实习基地等级：国家级/省级/市级/未评定
    
    private List<String> companyTags; // 企业标签：自主/双向/兜底
    
    private String status; // pending-待审核, approved-已通过, rejected-已驳回
    
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
    private CompanyUser company; // 企业信息
}
