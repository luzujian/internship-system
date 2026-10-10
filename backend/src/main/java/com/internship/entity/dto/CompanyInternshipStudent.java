package com.internship.entity.dto;

import lombok.Data;

import java.util.Date;

/** 企业当前实习名单，只返回联系和实习所需的字段。 */
@Data
public class CompanyInternshipStudent {
    private Long studentId;
    private Long placementId;
    private String studentName;
    private String studentNumber;
    private String grade;
    private String majorName;
    private String className;
    private String phone;
    private String email;
    private String positionName;
    private String sourceType;
    private Date internshipStartTime;
    private Date internshipEndTime;
}
