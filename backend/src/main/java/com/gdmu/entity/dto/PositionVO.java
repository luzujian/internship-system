package com.gdmu.entity.dto;

import lombok.Data;
import java.util.Date;
import java.util.List;

/**
 * 学生端职位展示VO - 转换后端Position为前端期望的格式
 */
@Data
public class PositionVO {
    private Long id;

    // 职位名称 -> title
    private String title;

    // 公司名称 -> company
    private String company;

    // 完整地址 -> location
    private String location;

    // 薪资范围 -> salary
    private String salary;

    // 实习时长 -> duration
    private String duration;

    // 标签 -> tags (从company_tag拆分)
    private List<String> tags;

    // 是否收藏 -> isFavorite (暂无收藏表，默认false)
    private Boolean isFavorite = false;

    // 是否已申请 -> isApplied (从student_job_application查询)
    private Boolean isApplied = false;

    // 实习基地级别 -> internshipBase (national/provincial/null)
    private String internshipBase;

    // 行业ID -> industry
    private Long industry;

    // 行业名称 -> industryName (从company.industry获取)
    private String industryName;

    // 职位类型 -> type
    private String type;

    // 福利待遇 -> benefits (从company.introduction获取)
    private String benefits;

    // 任职要求 -> requirements (从position.requirements获取)
    private String requirements;

    // 职位描述 -> description (从position.description获取)
    private String description;

    // 联系人 -> contactPerson (从company.contactPerson获取)
    private String contactPerson;

    // 联系电话 -> contactPhone (从company.contactPhone获取)
    private String contactPhone;

    // 联系邮箱 -> contactEmail (从company.contactEmail获取)
    private String contactEmail;

    // 发布时间 -> publishTime
    private Date publishTime;

    // 浏览次数 -> viewCount
    private Integer viewCount = 0;

    // 申请人数 -> applyCount (从position.recruitedCount获取)
    private Integer applyCount;

    // 部门
    private String department;

    // 计划招聘人数
    private Integer plannedRecruit;

    // 已招人数
    private Integer recruitedCount;

    // 剩余名额
    private Integer remainingQuota;

    // 职位状态
    private String status;

    // 实习开始日期
    private Date internshipStartDate;

    // 实习结束日期
    private Date internshipEndDate;

    // 创建时间
    private Date createTime;

    // 公司规模 (从company.scale获取)
    private String scale;

    // 公司简介 (从company.introduction获取)
    private String companyIntroduction;

    // 公司ID
    private Long companyId;
}