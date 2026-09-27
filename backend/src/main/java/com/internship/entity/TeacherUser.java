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
 * 教师用户实体类
 */
@Data
public class TeacherUser {
    private Long id;
    
    private String username; // 用户名，与teacherUserId相同
    
    // 密码只接受输入、永不输出（防哈希泄露）；更新接口空密码表示保持原密码
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    private String password;
    
    private String role; // 由经确认的教师类型派生，不接受通用教师角色
    
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
    private String divisionId;
    private String phone;
    private String email;
    
    // 用户状态：1-启用，0-禁用
    private Integer status = 1;
    
    // 教师类型：COLLEGE-学院，DEPARTMENT-系室，COUNSELOR-辅导员
    private String teacherType;

}
