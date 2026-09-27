package com.internship.entity;

import lombok.Data;

import java.util.Date;

/** 学生在最终实习材料中提交的企业五星推荐评分。 */
@Data
public class CompanyRecommendationRating {
    private Long id;
    private Long submissionId;
    private Long companyId;
    private Integer stars;
    private Date createTime;
    private Date updateTime;
}
