package com.gdmu.entity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.Date;

/**
 * 教师用户实体类
 */
@Data
public class TeacherUser {
    private Long id;
    
    private String username; // 用户名，与teacherUserId相同
    
    // 密码字段在DTO中验证，实体类不需要验证注解，防止密码被序列化
    private String password;
    
    private String role = "ROLE_TEACHER"; // 教师角色固定为ROLE_TEACHER
    
    @NotBlank(message = "姓名不能为空")
    private String name;
    
    private Integer gender;
    
    private Date createTime;
    private Date updateTime;
    
    // 新增字段：最后登录时间
    private Date lastLoginTime;
    
    // 教师特有字段
    private String teacherUserId; 
    private String departmentId;
    
    // 用户状态：1-启用，0-禁用
    private Integer status = 1;
    
    // 教师类型：COLLEGE-学院，DEPARTMENT-系室，COUNSELOR-辅导员
    private String teacherType;

}