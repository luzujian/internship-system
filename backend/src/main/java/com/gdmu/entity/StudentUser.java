package com.gdmu.entity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.Date;

/**
 * 学生用户实体类
 */
@Data
public class StudentUser {
    private Long id;
    
    private String username; // 用户名，与studentUserId相同
    
    // 密码字段在DTO中验证，实体类不需要验证注解，防止密码被序列化
    private String password;
    
    private String role = "ROLE_STUDENT"; // 学生角色固定为ROLE_STUDENT
    
    @NotBlank(message = "姓名不能为空")
    private String name;
    
    private Integer gender;
    
    private Date createTime;
    private Date updateTime;
    
    // 新增字段：最后登录时间
    private Date lastLoginTime;
    
    // 新增字段：用户状态（1启用，0禁用）
    private Integer status = 1;
    
    // 学生特有字段
    private String studentUserId; // 学号
    private Long majorId; // 专业ID
    private Integer grade; // 年级
    private Long classId; // 班级ID，对应数据库中的class_id字段
    private String className; // 班级名称，用于前端显示

    // 补充字段
    private String school; // 学院
    private String department; // 系/部门
    private String major; // 专业名称
    private String classes; // 班级（与className类似）
    private String phone; // 电话
    private String email; // 邮箱
    private String avatar; // 头像URL
    
    // 兼容旧代码的getter/setter方法
    public String getStudentId() {
        return this.studentUserId;
    }
    
    public void setStudentId(String studentId) {
        this.studentUserId = studentId;
    }
}