package com.gdmu.entity;

import lombok.Data;
import java.util.Date;

/**
 * 院系实体类
 */
@Data
public class Department {
    private Long id;

    private String name;
    
    private Date createTime;
    
    private Date updateTime;
    
    // 教师人数
    private Integer teacherCount = 0;
    
    // 学生人数
    private Integer studentCount = 0;
    
    // 已确定实习的学生数
    private Integer confirmedCount = 0;
    
    // 未找到实习的学生数
    private Integer notFoundCount = 0;
    
    // 有Offer未确定的学生数
    private Integer hasOfferCount = 0;
}