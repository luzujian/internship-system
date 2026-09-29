package com.internship.service;

import com.internship.entity.InternshipStageAssignment;

import java.util.List;
import java.util.Date;

public interface InternshipStageAssignmentService {
    List<InternshipStageAssignment> findAll();
    InternshipStageAssignment findById(Long id);
    InternshipStageAssignment findByApplicationId(Long applicationId);
    InternshipStageAssignment findBySource(String sourceType, Long sourceId);
    InternshipStageAssignment findByConfirmationRecordId(Long confirmationRecordId);
    InternshipStageAssignment findByConfirmationRecordIdForUpdate(Long confirmationRecordId);
    List<InternshipStageAssignment> findByStudentId(Long studentId);
    List<InternshipStageAssignment> findCurrentByStudentForUpdate(Long studentId);
    InternshipStageAssignment findPendingByStudentAndPosition(Long studentId, Long companyId, Long positionId);
    InternshipStageAssignment findCurrentByStudentAndStage(Long studentId, Integer stageNo);
    List<Long> findStudentsAwaitingStageTwoDecision();
    InternshipStageAssignment createContinuedAssignment(Long studentId);
    InternshipStageAssignment createAutoContinuedAssignment(Long studentId, Date occurredAt);
    void beginStageTwoChange(Long studentId, String reason);
    InternshipStageAssignment createFromApplication(InternshipStageAssignment assignment);
    void update(InternshipStageAssignment assignment);
}
