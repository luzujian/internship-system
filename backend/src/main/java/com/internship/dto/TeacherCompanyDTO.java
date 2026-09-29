package com.internship.dto;

import com.internship.entity.CompanyAlumniMember;
import com.internship.entity.CompanySchoolLiaison;
import com.internship.entity.CompanyVisitSummary;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * Teacher-facing company data used by the company relations page.
 *
 * <p>This DTO deliberately excludes account credentials, identity documents,
 * qualification attachments and audit-internal fields.</p>
 */
@Data
public class TeacherCompanyDTO {
    private Long id;
    private String companyName;
    private String creditCode;
    private String contactPerson;
    private String contactPhone;
    private String contactEmail;
    private String address;
    private String introduction;
    private String industry;
    private String scale;
    private String province;
    private String city;
    private String district;
    private String detailAddress;
    private String website;
    private String cooperationMode;
    /** 企业宣传资料：logo 图片地址、photos/videos 为 JSON 数组字符串 */
    private String logo;
    private String photos;
    private String videos;
    private Integer isInternshipBase;
    private Integer acceptBackup;
    private Integer hasTrainingBaseAgreement;
    private String companyTag;
    private Integer isAlumniUnit;
    private Integer status;
    private Date applyTime;
    private Date auditTime;
    private Date createTime;
    private Date updateTime;

    private List<CompanyAlumniMember> alumniMembers;
    private List<CompanySchoolLiaison> schoolLiaisons;
    private CompanyVisitSummary visitSummary;
}
