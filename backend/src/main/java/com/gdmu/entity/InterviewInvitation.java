package com.gdmu.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class InterviewInvitation {
    private Long id;
    private Long studentId;
    private Long companyId;
    private Long positionId;
    private String positionName;
    private String companyName;
    private LocalDateTime interviewTime;
    private String interviewLocation;
    private String interviewMethod;
    private String status;
    private String rejectReason;
    private String remark;
    private String contactPerson;
    private String contactPhone;
    private String website;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}