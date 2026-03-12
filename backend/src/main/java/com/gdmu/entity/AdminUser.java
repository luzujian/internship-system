package com.gdmu.entity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.Date;

/**
 * 管理员用户实体类
 */
@Data
public class AdminUser {
    // 添加无参构造函数，用于Jackson反序列化
    public AdminUser() {
    }
    
    // 添加带Long id参数的构造函数，用于处理Long类型ID
    public AdminUser(Long id) {
        this.id = id;
    }
    
    // 添加带int id参数的构造函数，用于处理Integer类型ID（Jackson反序列化需要）
    public AdminUser(int id) {
        this.id = Long.valueOf(id);
    }
    private Long id;
    
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度必须在3-20个字符之间")
    private String username;
    
    // 密码字段在DTO中验证，实体类不需要验证注解，防止密码被序列化
    private String password;
    
    @NotBlank(message = "姓名不能为空")
    @Size(min = 2, max = 20, message = "姓名长度必须在2-20个字符之间")
    private String name;
    
    private String role = "ROLE_ADMIN"; // 管理员角色固定为ROLE_ADMIN
    
    // 用户状态：1-启用，0-禁用
    private Integer status = 1;
    
    private Date createTime;
    private Date updateTime;
    
    // 新增字段：最后登录时间
    private Date lastLoginTime;
    
    // 管理员特有字段
    private Integer adminLevel = 1; // 管理员级别，默认为1
    private String department; // 所属部门
    private String phone; // 手机号

    public String getPhone() {
        return phone;
    }
    
    public void setPhone(String phone) {
        this.phone = phone;
    }
}