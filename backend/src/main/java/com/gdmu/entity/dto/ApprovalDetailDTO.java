package com.gdmu.entity.dto;

import lombok.Data;

/**
 * 申请审核详情DTO
 */
@Data
public class ApprovalDetailDTO {
    private Long id;
    private String type;
    private Integer totalCount;
    private Integer approvedCount;
    private Integer rejectedCount;
    private Double approvalRate;
}
