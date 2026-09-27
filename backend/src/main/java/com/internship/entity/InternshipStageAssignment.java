package com.internship.entity;

import lombok.Data;

import java.util.Date;

/** 学生在某个实习阶段中的一段实际实习经历。 */
@Data
public class InternshipStageAssignment {
    private Long id;
    private Long studentId;
    private Integer stageNo;
    private Integer sequenceNo;
    private String sourceType;
    private Long sourceAssignmentId;
    private Long applicationId;
    private Long confirmationRecordId;
    private Long companyId;
    private String companyName;
    private Long positionId;
    private String positionName;
    private Date internshipStartTime;
    private Date internshipEndTime;
    private String status;
    private Integer currentFlag = 1;
    private Integer quotaReleased = 0;
    private Date quotaReleasedAt;
    private String quotaReleaseReason;
    private String remark;
    private Date createTime;
    private Date updateTime;
}
