package com.gdmu.entity;

import lombok.Data;
import java.time.LocalDateTime;
import org.apache.ibatis.type.Alias;

@Data
@Alias("GroupMember")
public class GroupMember {
    private Long id;
    private Long groupId;
    // 对应数据库中的student_user_id字段
    private Long studentId;
    private LocalDateTime joinTime;
    private String role;
    
    // 新增字段：成员状态（ACTIVE: 活跃，INACTIVE: 不活跃，REMOVED: 已移除）
    private String status = "ACTIVE";
    
    // 新增字段：加入方式（INVITE: 邀请加入，APPLY: 申请加入，AUTO: 自动加入）
    private String joinMethod = "APPLY";
    
    // 新增字段：成员贡献度评分
    private Double contributionScore = 0.0;
    
    // 新增字段：成员活跃度评分
    private Double activityScore = 0.0;
    
    // 新增字段：是否为组长
    private Boolean isLeader = false;
    
    // 新增字段：成员备注
    private String remark;
}