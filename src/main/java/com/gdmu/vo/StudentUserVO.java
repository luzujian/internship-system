package com.gdmu.vo;

import lombok.Data;
import java.util.Date;

/**
 * 学生用户视图对象，用于AI查询结果展示
 */
@Data
public class StudentUserVO {
    private Long id;
    private String studentId; // 学号
    private String name; // 姓名
    private String role; // 角色
    private Date createTime; // 创建时间
    private Date lastLoginTime; // 最后登录时间
    private Integer status; // 状态
    
    // 关联信息
    private String majorName; // 专业名称
    private String departmentName; // 学院名称
    private String className; // 班级名称
    private Integer grade; // 年级
    
    // 小组信息
    private String groupName; // 所在小组名称
    private Long groupId; // 小组ID
    
    public StudentUserVO() {
    }
    
    public StudentUserVO(Long id, String studentId, String name, String role, 
                         Date createTime, Date lastLoginTime, Integer status,
                         String majorName, String departmentName, String className, 
                         Integer grade, String groupName, Long groupId) {
        this.id = id;
        this.studentId = studentId;
        this.name = name;
        this.role = role;
        this.createTime = createTime;
        this.lastLoginTime = lastLoginTime;
        this.status = status;
        this.majorName = majorName;
        this.departmentName = departmentName;
        this.className = className;
        this.grade = grade;
        this.groupName = groupName;
        this.groupId = groupId;
    }
}