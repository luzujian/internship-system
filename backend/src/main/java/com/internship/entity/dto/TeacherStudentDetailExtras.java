package com.internship.entity.dto;

import lombok.Data;

/** 教师学生详情与导出共用的只读补充信息。 */
@Data
public class TeacherStudentDetailExtras {
    private Long studentId;
    private String department;
    private String phone;
    private String email;
    private String companyBusiness;
    private String companyCity;
    private String hrManagerName;
    private String hrManagerPhone;
}
