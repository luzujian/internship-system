package com.internship.entity.dto;

import lombok.Data;

@Data
public class RequiredCompanyRating {
    private Long companyId;
    private String companyName;
    private Integer stars;

    public RequiredCompanyRating(Long companyId, String companyName) {
        this.companyId = companyId;
        this.companyName = companyName;
    }
}
