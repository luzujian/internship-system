package com.internship.controller;

import com.internship.entity.InternshipStageAssignment;
import com.internship.entity.Result;
import com.internship.entity.TeacherUser;
import com.internship.mapper.TeacherAutomationScopeMapper;
import com.internship.mapper.TeacherUserMapper;
import com.internship.service.InternshipStageAssignmentService;
import com.internship.utils.CurrentHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

@RestController
@RequestMapping("/api/teacher")
@PreAuthorize("hasAnyRole('TEACHER_COLLEGE', 'TEACHER_COUNSELOR', 'TEACHER_DEPARTMENT', 'TEACHER')")
public class TeacherInternshipFlowController {
    private final InternshipStageAssignmentService assignmentService;

    @Autowired
    private TeacherUserMapper teacherUserMapper;

    @Autowired
    private TeacherAutomationScopeMapper teacherScopeMapper;

    public TeacherInternshipFlowController(InternshipStageAssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    /** Legacy route is retained only to give old clients an explicit migration response. */
    @GetMapping("/internship-stage-assignments")
    public Result listLegacyStageAssignments() {
        return Result.error("阶段实习接口已停用，请使用 /api/teacher/internship-placements");
    }

    @GetMapping("/internship-placements")
    public Result listPlacements() {
        Long userId = CurrentHolder.getUserId();
        TeacherUser teacher = userId == null ? null : teacherUserMapper.findById(userId);
        if (teacher == null || !Integer.valueOf(1).equals(teacher.getStatus())
                || teacher.getTeacherType() == null
                || !Set.of("COLLEGE", "DEPARTMENT", "COUNSELOR").contains(teacher.getTeacherType())) {
            throw new AccessDeniedException("教师身份或负责范围不可用");
        }
        List<Long> scopedIds = teacherScopeMapper.studentIds(teacher);
        if (scopedIds == null || scopedIds.isEmpty()) {
            return Result.success(List.of());
        }
        Set<Long> allowedStudentIds = new HashSet<>(scopedIds);
        List<PlacementView> placements = assignmentService.findAll().stream()
                .filter(assignment -> allowedStudentIds.contains(assignment.getStudentId()))
                .map(PlacementView::from)
                .toList();
        return Result.success(placements);
    }

    public record PlacementView(Long placementId,
                                Long studentId,
                                Integer sequenceNo,
                                String sourceType,
                                Long sourceId,
                                Long applicationId,
                                Long confirmationRecordId,
                                Long companyId,
                                String companyName,
                                Long positionId,
                                String positionName,
                                Date internshipStartTime,
                                Date internshipEndTime,
                                String lifecycleStatus,
                                boolean current,
                                String remark,
                                Date createTime,
                                Date updateTime) {
        private static PlacementView from(InternshipStageAssignment assignment) {
            return new PlacementView(
                    assignment.getId(), assignment.getStudentId(), assignment.getSequenceNo(),
                    assignment.getSourceType(), assignment.getSourceAssignmentId(),
                    assignment.getApplicationId(), assignment.getConfirmationRecordId(),
                    assignment.getCompanyId(), assignment.getCompanyName(),
                    assignment.getPositionId(), assignment.getPositionName(),
                    assignment.getInternshipStartTime(), assignment.getInternshipEndTime(),
                    assignment.getStatus(), Integer.valueOf(1).equals(assignment.getCurrentFlag()),
                    assignment.getRemark(), assignment.getCreateTime(), assignment.getUpdateTime());
        }
    }
}
