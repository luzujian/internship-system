package com.gdmu.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 小组实体类
 */
@Data
public class GroupTable {
    private Long id;
    private String name;
    private Long leaderId;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    // 新增字段：课程ID
    private Long courseId;
    
    // 新增字段：作业ID（关联特定作业）
    private Long assignmentId;
    
    // 新增字段：班级ID
    private Long classId;
    
    // 新增字段：专业ID
    private Long majorId;
    
    // 新增字段：小组描述
    private String description;
    
    // 新增字段：成员数量
    private Integer memberCount;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
    
    // 新增字段：小组状态
    private Integer status;

    private Long teacherId;
    
    // 新增字段：学生组长ID
    private Long leaderStudentId;
    
    // 新增字段：教师组长ID
    private Long leaderTeacherId;
    

    
    // 新增字段：小组最大人数限制
    private Integer maxMembers = 6;
    
    // 新增字段：小组当前人数
    private Integer currentMembers = 1;
    
    // 新增字段：小组加入方式（FREE自由加入，APPROVAL需要审批）
    private String joinMethod = "FREE";
    
    // 新增字段：小组是否公开可见
    private Boolean isPublic = true;
    
    // 新增字段：小组邀请码（用于邀请加入）
    private String inviteCode;
    
    // 新增字段：小组标签/分类
    private String tags;
    
    // 新增字段：小组封面图片URL
    private String coverImage;
    
    // 新增字段：小组活跃度评分
    private Double activityScore = 0.0;
    
    // 新增字段：小组评分（基于历史表现）
    private Double groupRating = 0.0;
    
    // 新增字段：小组完成作业数量
    private Integer completedAssignments = 0;
    
    // 新增字段：小组平均成绩
    private Double averageScore = 0.0;
    
    // 新增字段：审批状态
    private Integer approvalStatus;
}