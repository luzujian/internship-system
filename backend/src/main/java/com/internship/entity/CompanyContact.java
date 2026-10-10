package com.internship.entity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CompanyContact {
    private Long id;
    private Long companyId;
    @NotBlank(message = "联系人姓名不能为空")
    @Size(max = 50, message = "联系人姓名不能超过50个字符")
    private String name;
    @NotBlank(message = "联系人电话不能为空")
    @Pattern(regexp = "^[+0-9()（）\\-\\s转]{7,30}$", message = "联系人电话格式不正确")
    private String phone;
    @Email(message = "联系人邮箱格式不正确")
    @Size(max = 100, message = "联系人邮箱不能超过100个字符")
    private String email;
    private Long positionCount;
}
