package com.gdmu.entity;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.Date;

@Data
public class Position {
    private Long id;
    
    private Long companyId;
    
    private Long categoryId;
    
    @NotBlank(message = "岗位名称不能为空")
    @Size(min = 2, max = 100, message = "岗位名称长度必须在2-100个字符之间")
    private String positionName;
    
    private String department;
    
    private String positionType;
    
    private String province;
    
    private String city;
    
    private String district;
    
    private String detailAddress;
    
    private Integer salaryMin;
    
    private Integer salaryMax;
    
    private String description;
    
    private String requirements;
    
    private String internshipStartDate;
    
    private String internshipEndDate;
    
    @Min(value = 0, message = "计划招聘人数不能为负数")
    private Integer plannedRecruit = 0;
    
    @Min(value = 0, message = "已招人数不能为负数")
    private Integer recruitedCount = 0;
    
    @Min(value = 0, message = "剩余缺口不能为负数")
    private Integer remainingQuota = 0;
    
    private String status;
    
    private Date publishDate;
    
    private Date createTime;
    
    private Date updateTime;
}