package com.internship.entity.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CompanyRatingSummary {
    private Long companyId;
    private BigDecimal averageStars;
    private Integer ratingCount;
}
