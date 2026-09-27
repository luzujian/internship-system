package com.internship.mapper;

import com.internship.entity.InternshipStageAssignment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InternshipStageAssignmentMapper {
    List<InternshipStageAssignment> findAll();
    InternshipStageAssignment findById(@Param("id") Long id);
    InternshipStageAssignment findByApplicationId(@Param("applicationId") Long applicationId);
    InternshipStageAssignment findBySource(@Param("sourceType") String sourceType,
                                            @Param("sourceId") Long sourceId);
    InternshipStageAssignment findByConfirmationRecordId(@Param("confirmationRecordId") Long confirmationRecordId);
    InternshipStageAssignment findByConfirmationRecordIdForUpdate(@Param("confirmationRecordId") Long confirmationRecordId);
    List<InternshipStageAssignment> findByStudentId(@Param("studentId") Long studentId);
    List<InternshipStageAssignment> findCurrentByStudentForUpdate(@Param("studentId") Long studentId);
    InternshipStageAssignment findPendingByStudentAndPosition(@Param("studentId") Long studentId,
                                                               @Param("companyId") Long companyId,
                                                               @Param("positionId") Long positionId);
    InternshipStageAssignment findCurrentByStudentAndStage(@Param("studentId") Long studentId,
                                                           @Param("stageNo") Integer stageNo);
    InternshipStageAssignment findCurrentByStudentAndStageForUpdate(@Param("studentId") Long studentId,
                                                                    @Param("stageNo") Integer stageNo);
    List<Long> findStudentsAwaitingStageTwoDecision();
    int countByStudentAndStage(@Param("studentId") Long studentId, @Param("stageNo") Integer stageNo);
    int countByStudent(@Param("studentId") Long studentId);
    int insert(InternshipStageAssignment assignment);
    int update(InternshipStageAssignment assignment);
    int clearConfirmationRecordId(@Param("id") Long id);
    int markQuotaReleased(@Param("id") Long id,
                          @Param("reason") String reason);
}
