package com.internship.entity.dto;

import lombok.Data;
import java.math.BigDecimal;
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

    private BigDecimal salaryMin;

    private BigDecimal salaryMax;

    private String salaryUnit;

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

    // 是否校友单位 -> isAlumniUnit (company.is_alumni_unit)
    private Integer isAlumniUnit;

    // 合作模式 -> cooperationMode (company.cooperation_mode，逗号分隔兼容新旧格式)
    private String cooperationMode;

    // 企业Logo -> logo (company.logo)
    private String logo;

    // 企业照片 -> photos (company.photos，JSON数组字符串)
    private String photos;

    // 企业视频 -> videos (company.videos，JSON数组字符串)
    private String videos;

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

    // 申请人数 -> applyCount (从student_job_application表统计获得)
    private Integer applyCount;

    // 部门
    private String department;

    // 计划招聘人数
    private Integer plannedRecruit;

    // 已招人数
    private Integer recruitedCount;

    // 剩余名额
    private Integer remainingQuota;

    // 仅供服务端兼容历史岗位到期判断，不属于公开岗位日期配置。
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Date internshipEndDate;

    // 职位状态
    private String status;

    // 创建时间
    private Date createTime;

    // 公司规模 (从company.scale获取)
    private String scale;

    // 公司简介 (从company.introduction获取)
    private String companyIntroduction;

    // 公司ID
    private Long companyId;

    // 企业推荐指数（仅包含已提交的最终材料评分，保留一位小数）
    private BigDecimal companyRatingAverage;

    // 企业有效评分人数
    private Integer companyRatingCount = 0;
}
