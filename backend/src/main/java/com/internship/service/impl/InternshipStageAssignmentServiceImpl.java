package com.internship.service.impl;

import com.internship.entity.InternshipStageAssignment;
import com.internship.exception.BusinessException;
import com.internship.mapper.InternshipStageAssignmentMapper;
import com.internship.service.InternshipStageAssignmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
public class InternshipStageAssignmentServiceImpl implements InternshipStageAssignmentService {
    private static final String STAGE_FLOW_RETIRED = "双阶段实习流程已停用，请直接申请新岗位";

    private final InternshipStageAssignmentMapper mapper;

    public InternshipStageAssignmentServiceImpl(InternshipStageAssignmentMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public InternshipStageAssignment findById(Long id) {
        return mapper.findById(id);
    }

    @Override
    public InternshipStageAssignment findByApplicationId(Long applicationId) {
        return applicationId == null ? null : mapper.findByApplicationId(applicationId);
    }

    @Override
    public InternshipStageAssignment findBySource(String sourceType, Long sourceId) {
        return sourceType == null || sourceId == null ? null : mapper.findBySource(sourceType, sourceId);
    }

    @Override
    public InternshipStageAssignment findByConfirmationRecordId(Long confirmationRecordId) {
        return confirmationRecordId == null ? null : mapper.findByConfirmationRecordId(confirmationRecordId);
    }

    @Override
    public InternshipStageAssignment findByConfirmationRecordIdForUpdate(Long confirmationRecordId) {
        return confirmationRecordId == null ? null : mapper.findByConfirmationRecordIdForUpdate(confirmationRecordId);
    }

    @Override
    public List<InternshipStageAssignment> findAll() {
        return mapper.findAll();
    }

    @Override
    public List<InternshipStageAssignment> findByStudentId(Long studentId) {
        return mapper.findByStudentId(studentId);
    }

    @Override
    public List<InternshipStageAssignment> findCurrentByStudentForUpdate(Long studentId) {
        return mapper.findCurrentByStudentForUpdate(studentId);
    }

    @Override
    public InternshipStageAssignment findPendingByStudentAndPosition(Long studentId,
                                                                     Long companyId,
                                                                     Long positionId) {
        if (studentId == null || companyId == null || positionId == null) return null;
        return mapper.findPendingByStudentAndPosition(studentId, companyId, positionId);
    }

    @Override
    public InternshipStageAssignment findCurrentByStudentAndStage(Long studentId, Integer stageNo) {
        return mapper.findCurrentByStudentAndStage(studentId, stageNo);
    }

    @Override
    public List<Long> findStudentsAwaitingStageTwoDecision() {
        return List.of();
    }

    @Override
    public InternshipStageAssignment createContinuedAssignment(Long studentId) {
        throw new BusinessException(STAGE_FLOW_RETIRED);
    }

    @Override
    public InternshipStageAssignment createAutoContinuedAssignment(Long studentId, Date occurredAt) {
        throw new BusinessException(STAGE_FLOW_RETIRED);
    }

    @Override
    public void beginStageTwoChange(Long studentId, String reason) {
        throw new BusinessException(STAGE_FLOW_RETIRED);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InternshipStageAssignment createFromApplication(InternshipStageAssignment assignment) {
        if (assignment == null || assignment.getStudentId() == null) {
            throw new BusinessException("实习经历记录参数不完整");
        }

        assignment.setStageNo(1);
        InternshipStageAssignment existing = null;
        if (assignment.getSourceType() != null && assignment.getSourceAssignmentId() != null) {
            existing = mapper.findBySource(
                    assignment.getSourceType(), assignment.getSourceAssignmentId());
        }
        if (existing == null && assignment.getApplicationId() != null) {
            existing = mapper.findByApplicationId(assignment.getApplicationId());
        }
        if (existing == null && assignment.getCompanyId() != null && assignment.getPositionId() != null) {
            existing = mapper.findPendingByStudentAndPosition(
                    assignment.getStudentId(), assignment.getCompanyId(), assignment.getPositionId());
        }
        if (existing != null) {
            return existing;
        }

        if (assignment.getSequenceNo() == null) {
            assignment.setSequenceNo(mapper.countByStudent(assignment.getStudentId()) + 1);
        }
        if (assignment.getSourceType() == null) assignment.setSourceType("NEW_APPLICATION");
        if (assignment.getStatus() == null) assignment.setStatus("PENDING_CONFIRMATION");
        assignment.setCurrentFlag(0);
        if (mapper.insert(assignment) <= 0) {
            throw new BusinessException("实习经历记录创建失败");
        }
        return assignment;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(InternshipStageAssignment assignment) {
        if (assignment == null || assignment.getId() == null) {
            throw new BusinessException("实习经历记录ID不能为空");
        }
        if (mapper.update(assignment) <= 0) {
            throw new BusinessException("实习经历记录更新失败");
        }
    }
}
