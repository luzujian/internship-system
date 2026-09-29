package com.internship.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CompanyVisitRecord {
    private Long id;
    private Long companyId;
    private String companyName;
    private LocalDate visitDate;
    private String remark;
    private Long creatorTeacherId;
    private String creatorTeacherAccount;
    private String creatorTeacherName;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<CompanyVisitParticipant> participants;
}
