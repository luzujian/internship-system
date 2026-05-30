package com.internship.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 企业入驻趋势DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompanyTrendDTO {
    private String label;
    private Integer value;
}
