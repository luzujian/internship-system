package com.gdmu.entity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.Date;

/**
 * 基类，包含用户共有的属性，已经细分为AdminUser、CompanyUser、StudentUser、TeacherUser
 */
@Data
public class User {
    private Long id;
    
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度必须在3-20个字符之间")
    private String username;
    
    // 密码字段在DTO中验证，实体类不需要验证注解，防止密码被序列化
    private String password;
    
    @NotBlank(message = "角色不能为空")
    private String role; // ROLE_STUDENT, ROLE_TEACHER, ROLE_ADMIN
    
    @NotBlank(message = "姓名不能为空")
    private String name;
    
    private Date createTime;
    private Date updateTime;
    
    // 新增字段：最后登录时间
    private Date lastLoginTime;
}