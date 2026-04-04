package com.gdmu.entity.dto;

import lombok.Data;

/**
 * 学生实习详情DTO
 */
@Data
public class StudentInternshipDetailDTO {
    private Long id;
    private String college;
    private String major;
    private Integer totalCount;
    private Integer internshipCount;
    private Double internshipRate;
}
