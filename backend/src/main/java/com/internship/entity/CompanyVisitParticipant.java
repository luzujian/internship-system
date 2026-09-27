package com.internship.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CompanyVisitParticipant {
    private Long id;
    private Long visitRecordId;
    private Long teacherId;
    private String teacherAccount;
    private String teacherName;
    private String teacherPhone;
    private LocalDateTime createTime;
}
