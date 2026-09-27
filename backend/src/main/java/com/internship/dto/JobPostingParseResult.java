package com.internship.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class JobPostingParseResult {
    @JsonAlias("position_name")
    private String positionName;
    private String department;
    @JsonAlias("position_type")
    private String positionType;
    private String province;
    private String city;
    private String district;
    @JsonAlias("detail_address")
    private String detailAddress;
    @JsonAlias("salary_unit")
    private String salaryUnit;
    @JsonAlias("salary_min")
    private BigDecimal salaryMin;
    @JsonAlias("salary_max")
    private BigDecimal salaryMax;
    @JsonAlias("planned_recruit")
    private Integer plannedRecruit;
    @JsonAlias("required_majors")
    private List<String> requiredMajors;
    private String description;
    private String requirements;
    @JsonAlias("internship_environment")
    private String internshipEnvironment;
    @JsonAlias("provides_accommodation")
    private Integer providesAccommodation;
    @JsonAlias("interview_time")
    private String interviewTime;
    @JsonAlias("interview_location")
    private String interviewLocation;
    @JsonAlias("interview_method")
    private String interviewMethod;
    @JsonAlias("interview_remark")
    private String interviewRemark;
    private Map<String, BigDecimal> confidence;
    private List<String> warnings;
}
