package com.gdmu.entity;

import lombok.Data;
import java.util.Date;

/**
 * 公告阅读记录实体类
 */
@Data
public class AnnouncementReadRecord {
    private Long id;
    
    private Long announcementId;
    
    private String userId;
    
    private String userType; // 用户类型：STUDENT-学生，TEACHER-教师，ENTERPRISE-企业导师，ADMIN-管理员
    
    private Date readTime;
}
