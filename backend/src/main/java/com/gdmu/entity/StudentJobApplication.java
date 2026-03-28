package com.gdmu.entity;

import lombok.Data;
import java.util.Date;

/**
 * 学生求职申请实体类（区别于机构流程的 StudentApplication）
 */
@Data
public class StudentJobApplication {
    private Long id;
    private Long studentId;
    private Long positionId;
    private Long companyId;
    private String positionName;
    private String companyName;
    private String location;
    private String salary;
    private String duration;
    private String coverLetter;
    private String resumeUrl;
    private String studentName;
    private String studentNo;
    private String phone;
    private String email;
    private String major;
    private String grade;
    private String selfIntroduction;
    private String status; // pending, approved, rejected, withdrawn
    private String rejectReason;
    private Date applyDate;
    private Date createTime;
    private Date updateTime;

    // 职位详情字段（从 Position 表关联查询）
    private String type;        // position_type
    private String description;  // description
    private String requirements; // requirements
    private Integer viewCount;  // view_count
    private String publishTime;  // publish_date

    // 企业联系人字段（从 CompanyUser 表关联查询）
    private String contactPerson; // contact_person
    private String contactPhone; // contact_phone
    private String contactEmail; // contact_email
}
