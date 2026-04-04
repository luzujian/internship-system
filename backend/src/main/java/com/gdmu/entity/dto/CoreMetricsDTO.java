package com.gdmu.entity.dto;

import lombok.Data;

/**
 * 核心指标DTO
 */
@Data
public class CoreMetricsDTO {
    private Integer companyCount;
    private Integer companyChange;
    private Double internshipRate;
    private Integer internshipRateChange;
    private Integer approvalCount;
    private Integer approvalCountChange;
    private Integer resourceDownloads;
    private Integer resourceDownloadsChange;
}
