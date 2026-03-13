package com.gdmu.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 课程学生关联实体类
 * 记录学生加入课程的信息
 */
@Data
public class CourseStudent {
    private Long id;
    
    // 课程ID
    private Long courseId;
    
    // 学生ID
    private Long studentId;
    
    // 加入时间
    private LocalDateTime joinTime;
    
    // 状态（ACTIVE: 活跃，INACTIVE: 不活跃，DROPPED: 退课）
    private String status = "ACTIVE";
    
    // 创建时间
    private LocalDateTime createTime;
    
    // 更新时间
    private LocalDateTime updateTime;
    
    // 加入方式（MANUAL: 手动加入，INVITE: 邀请加入，AUTO: 自动加入）
    private String joinMethod = "MANUAL";
    
    // 备注信息
    private String remark;
}