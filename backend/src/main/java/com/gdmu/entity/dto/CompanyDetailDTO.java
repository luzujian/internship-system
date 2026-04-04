package com.gdmu.entity.dto;

import lombok.Data;
import java.util.Date;
import java.util.List;

/**
 * 企业入驻详情DTO
 */
@Data
public class CompanyDetailDTO {
    private Long id;
    private String name;
    private Date joinDate;
    private Integer positionCount;
    private Integer admittedCount;
    private String status;
    private List<String> tags;
    private String companyTag;
}
