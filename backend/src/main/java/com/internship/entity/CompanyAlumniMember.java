package com.internship.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CompanyAlumniMember {
    private Long id;
    private Long companyId;

    @NotBlank(message = "校友姓名不能为空")
    @Size(max = 50, message = "校友姓名长度不能超过50个字符")
    private String name;

    @NotBlank(message = "校友联系电话不能为空")
    @Size(max = 20, message = "校友联系电话长度不能超过20个字符")
    private String phone;

    @NotBlank(message = "校友任职不能为空")
    @Size(max = 100, message = "校友任职长度不能超过100个字符")
    private String position;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
