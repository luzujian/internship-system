package com.gdmu.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.Date;

@Data
public class CompanyUser {
    private Long id;
    
    @NotBlank(message = "公司名称不能为空")
    @Size(min = 2, max = 255, message = "公司名称长度必须在2-255个字符之间")
    private String companyName;
    
    @NotBlank(message = "联系人不能为空")
    @Size(min = 1, max = 50, message = "联系人长度必须在1-50个字符之间")
    private String contactPerson;
    
    @NotBlank(message = "联系电话不能为空")
    @Size(min = 11, max = 20, message = "联系电话长度必须在11-20个字符之间")
    private String contactPhone;
    
    @NotBlank(message = "联系邮箱不能为空")
    @Size(min = 5, max = 100, message = "联系邮箱长度必须在5-100个字符之间")
    private String contactEmail;
    
    private String phone;
    
    private String email;
    
    private String username;
    
    private String password;
    
    private String role = "ROLE_COMPANY";
    
    @NotBlank(message = "公司地址不能为空")
    @Size(min = 5, max = 255, message = "公司地址长度必须在5-255个字符之间")
    private String address;
    
    private String introduction;
    
    private String businessLicense;
    
    private String legalIdCard;
    
    private Integer isInternshipBase = 0;
    
    private String plaquePhoto;
    
    private Integer hasReceivedInterns = 0;
    
    private Integer currentEmployeesCount = 0;
    
    private Integer acceptBackup = 0;
    
    private Integer maxBackupStudents = 0;
    
    private String companyTag;
    
    private Date registerTime;
    
    private Date applyTime;
    
    private Date auditTime;
    
    private Integer auditStatus = 0;
    
    private String auditRemark;
    
    private Long reviewerId;
    
    private Integer status = 0;
    
    private Integer recallStatus = 0;
    
    private String recallReason;
    
    private Date recallApplyTime;
    
    private Date recallAuditTime;
    
    private Long recallReviewerId;
    
    private String recallAuditRemark;
    
    private Date createTime;
    private Date updateTime;

    private String industry;
    private String scale;
    private String province;
    private String city;
    private String district;
    private String detailAddress;
    private String website;
    private String description;
    private String cooperationMode;
    private String logo;
    private String photos;
    private String videos;
}