package com.internship.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;
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
    
    // 密码只接受输入、永不输出（防哈希泄露）；更新接口空密码表示保持原密码
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
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

    @Email(message = "邮箱格式不正确")
    @Size(max = 254, message = "邮箱长度不能超过254个字符")
    private String email; // 问题反馈提醒收件邮箱

    public String getPhone() {
        return phone;
    }
    
    public void setPhone(String phone) {
        this.phone = phone;
    }
}
