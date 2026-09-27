package com.internship.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CompanySchoolLiaison {
    private Long id;
    private Long companyId;
    private Long teacherId;
    private Long creatorTeacherId;
    private String creatorTeacherName;
    private String teacherAccount;
    private String teacherName;
    private String teacherPhone;
    private LocalDateTime createTime;
}
