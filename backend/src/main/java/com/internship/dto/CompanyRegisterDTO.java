package com.internship.dto;

import com.internship.entity.CompanyAlumniMember;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CompanyRegisterDTO {

    @NotBlank(message = "企业名称不能为空")
    @Size(min = 2, max = 255, message = "企业名称长度必须在 2-255 个字符之间")
    private String companyName;

    @NotBlank(message = "统一社会信用代码不能为空")
    @Size(max = 50, message = "统一社会信用代码长度不能超过50")
    private String creditCode;

    @NotBlank(message = "所属行业不能为空")
    private String industry;

    @NotBlank(message = "企业规模不能为空")
    private String scale;

    @NotBlank(message = "省份不能为空")
    private String province;

    private String city;

    private String district;

    @NotBlank(message = "详细地址不能为空")
    @Size(min = 5, max = 255, message = "详细地址长度必须在 5-255 个字符之间")
    private String detailAddress;

    @NotBlank(message = "联系人不能为空")
    @Size(min = 1, max = 50, message = "联系人长度必须在 1-50 个字符之间")
    private String contactPerson;

    @NotBlank(message = "联系电话不能为空")
    @Size(min = 11, max = 20, message = "联系电话长度必须在 11-20 个字符之间")
    private String contactPhone;

    @Email(message = "联系邮箱格式不正确")
    @Pattern(regexp = "^$|^.{5,100}$", message = "联系邮箱长度必须在 5-100 个字符之间")
    private String contactEmail;

    @NotBlank(message = "个人邮箱不能为空")
    @Email(message = "个人邮箱格式不正确")
    private String email;

    @Pattern(regexp = "^$|^https?://.*", message = "企业网站必须以 http:// 或 https:// 开头")
    private String website;

    private String cooperationMode;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Deprecated
    @Size(min = 5, max = 255, message = "企业地址长度必须在 5-255 个字符之间")
    private String address;

    private String introduction;

    private String businessLicense;

    private String legalIdCardFront;

    private String legalIdCardBack;

    private Integer isInternshipBase;

    private String plaquePhoto;

    private Integer acceptBackup;

    private Integer isAlumniUnit;

    private List<CompanyAlumniMember> alumniMembers;

    @Size(max = 50, message = "校友代表姓名长度不能超过50")
    private String alumniRepresentativeName;

    @Size(max = 20, message = "校友代表联系电话长度不能超过20")
    private String alumniRepresentativePhone;

    @Size(max = 100, message = "校友代表任职长度不能超过100")
    private String alumniRepresentativePosition;

    private Integer hasTrainingBaseAgreement;
}
