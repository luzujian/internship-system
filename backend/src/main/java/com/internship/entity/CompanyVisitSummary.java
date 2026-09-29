package com.internship.entity;

import lombok.Data;

import java.time.LocalDate;

@Data
public class CompanyVisitSummary {
    private Long companyId;
    private Long totalVisitCount;
    private Integer selectedYear;
    private Long selectedYearVisitCount;
    private Long currentYearVisitCount;
    private Boolean visitedInSelectedYear;
    private Boolean visitedThisYear;
    private LocalDate lastVisitDate;
}
