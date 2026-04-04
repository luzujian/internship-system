package com.gdmu.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CompanyRegisterDTO {

    @NotBlank(message = "企业名称不能为空")
    @Size(min = 2, max = 255, message = "企业名称长度必须在 2-255 个字符之间")
    private String companyName;

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

    @Email(message = "个人邮箱格式不正确")
    @Pattern(regexp = "^$|^.{5,100}$", message = "个人邮箱长度必须在 5-100 个字符之间")
    private String email;

    private String website;

    private String cooperationMode;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Deprecated
    @Size(min = 5, max = 255, message = "企业地址长度必须在 5-255 个字符之间")
    private String address;

    private String introduction;

    @NotBlank(message = "验证码不能为空")
    private String verifyCode;

    @NotBlank(message = "营业执照不能为空")
    private String businessLicense;

    @NotBlank(message = "法人身份证正面不能为空")
    private String legalIdCardFront;

    @NotBlank(message = "法人身份证反面不能为空")
    private String legalIdCardBack;

    private Integer isInternshipBase;

    private String plaquePhoto;

    private Integer acceptBackup;

    @Min(value = 0, message = "兜底学生数量不能小于0")
    @Max(value = 1000, message = "兜底学生数量不能超过1000")
    private Long maxBackupStudents;
}
