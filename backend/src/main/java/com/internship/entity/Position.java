package com.internship.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 岗位实体类
 */
@Data
public class Position {
    private Long id;

    private Long companyId;

    private Long contactId;
    @JsonIgnore
    private boolean contactSpecified;
    public void setContactId(Long contactId) {
        this.contactId = contactId;
        this.contactSpecified = true;
    }
    private String contactPerson;
    private String contactPhone;
    private String contactEmail;

    private Long categoryId;

    @NotBlank(message = "岗位名称不能为空")
    @Size(min = 2, max = 100, message = "岗位名称长度必须在 2-100 个字符之间")
    private String positionName;

    private String department;

    private String positionType;

    private String province;

    private String city;

    private String district;

    private String detailAddress;

    @Min(value = 0, message = "最低薪资不能为负数")
    private BigDecimal salaryMin;

    @Min(value = 0, message = "最高薪资不能为负数")
    private BigDecimal salaryMax;

    @Size(max = 20, message = "薪资单位长度不能超过 20 个字符")
    @Pattern(regexp = "^(K/月|元/天|元/小时|面议)$", message = "薪资单位不合法")
    private String salaryUnit = "K/月";

    private String description;

    private String requirements;

    @Min(value = 0, message = "计划招聘人数不能为负数")
    private Integer plannedRecruit = 0;

    @Min(value = 0, message = "已招人数不能为负数")
    private Integer recruitedCount = 0;

    @Min(value = 0, message = "剩余缺口不能为负数")
    private Integer remainingQuota = 0;

    private String status;

    private Date publishDate;

    @JsonIgnore
    private Date internshipStartDate;

    @JsonIgnore
    private Date internshipEndDate;

    private Date interviewTime;

    private String interviewLocation;

    private String interviewMethod;

    private String interviewRemark;

    private String internshipEnvironment;

    private Integer providesAccommodation = 0;

    private String requiredMajors;

    @Min(value = 0, message = "浏览次数不能为负数")
    private Integer viewCount = 0;

    private Date createTime;
    private Date updateTime;

    @JsonIgnore
    @AssertTrue(message = "最低薪资不能高于最高薪资")
    public boolean isSalaryRangeValid() {
        return salaryMin == null || salaryMax == null || salaryMin.compareTo(salaryMax) <= 0;
    }
}
