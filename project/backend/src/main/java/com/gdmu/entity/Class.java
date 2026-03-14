package com.gdmu.entity;

import lombok.Data;
import java.util.Date;

/**
 * 班级实体类
 */
@Data
public class Class {
    private Long id; // 班级ID
    private String name; // 班级名称
    private Long majorId; // 所属专业ID
    private String teacherId; // 负责教师ID
    private Integer grade; // 年级
    private Date createTime;
    private Date updateTime;
    private Integer studentCount = 0; // 班级人数，默认0
    private Integer confirmedCount = 0; // 已确定实习人数，默认0
    private Integer notFoundCount = 0; // 未找到实习人数，默认0
    private Integer hasOfferCount = 0; // 有Offer未确定人数，默认0
}