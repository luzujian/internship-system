package com.internship.entity.dto;

import lombok.Data;

/** 批量派生求职进度时需要的最小数据库事实集合。 */
@Data
public class JobSearchStatusEvidenceDTO {
    private Long studentId;
    private Integer internshipStatus;
    private Boolean hasInterviewOpportunity;
    private Boolean hasPassedOffer;
    private Boolean hasPendingConfirmation;
    private Boolean hasPendingSelfPractice;
    private Boolean hasPendingCompanySelfPractice;
    private Boolean hasPendingDelay;
}
