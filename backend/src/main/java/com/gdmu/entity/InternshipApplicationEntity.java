package com.gdmu.entity;

import lombok.Data;
import java.util.Date;

@Data
public class InternshipApplicationEntity {
    private Long id;
    private Long studentId;
    private Long positionId;
    private Long companyId;
    private String status;
    private Boolean viewed;
    private String skills;
    private String experience;
    private String selfEvaluation;
    private Date applyTime;
    private Date viewTime;
    private Date createTime;
    private Date updateTime;
    
    private String studentName;
    private String studentUserId;
    private String gender;
    private String grade;
    private String school;
    private String education;
    private String major;
    private String phone;
    private String email;
    private String positionName;
}