package com.internship.service;

import com.internship.entity.InternshipStageAssignment;
import com.internship.entity.StudentInternshipStatus;
import com.internship.exception.BusinessException;
import com.internship.mapper.PositionMapper;
import com.internship.mapper.StudentInternshipStatusMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static com.internship.service.InternshipProgressEvents.append;

@Service
public class InternshipLifecycleService {
    private final StudentInternshipStatusMapper statusMapper;
    private final PositionMapper positionMapper;
    private final InternshipStageAssignmentService assignmentService;
    private final InternshipProgressRecordService progressRecordService;

    public InternshipLifecycleService(StudentInternshipStatusMapper statusMapper,
                                      PositionMapper positionMapper,
                                      InternshipStageAssignmentService assignmentService,
                                      InternshipProgressRecordService progressRecordService) {
        this.statusMapper = statusMapper;
        this.positionMapper = positionMapper;
        this.assignmentService = assignmentService;
        this.progressRecordService = progressRecordService;
    }

    /**
     * 在读取或提交业务前即时推进已到起止边界的状态，避免仅依赖 5 分钟定时任务。
     */
    @Transactional(rollbackFor = Exception.class)
    public StudentInternshipStatus refreshStudentLifecycle(Long studentId) {
        StudentInternshipStatus status = statusMapper.findByStudentId(studentId);
        if (status == null) return null;
        Date now = new Date();
        if (Integer.valueOf(2).equals(status.getStatus())
                && status.getInternshipStartTime() != null
                && !status.getInternshipStartTime().after(now)) {
            startConfirmedInternship(status.getId(), status.getInternshipStartTime());
            status = statusMapper.findByStudentId(studentId);
        }
        if (status != null
                && Integer.valueOf(3).equals(status.getStatus())
                && status.getInternshipEndTime() != null
                && !status.getInternshipEndTime().after(now)) {
            finishActiveInternship(status.getId(), status.getInternshipEndTime());
            status = statusMapper.findByStudentId(studentId);
        }
        return status;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean startConfirmedInternship(Long statusId, Date occurredAt) {
        StudentInternshipStatus status = statusMapper.findById(statusId);
        if (status == null || !Integer.valueOf(2).equals(status.getStatus())) {
            return false;
        }
        Date startTime = occurredAt == null ? status.getInternshipStartTime() : occurredAt;
        if (startTime == null || startTime.after(new Date())) {
            return false;
        }
        if (statusMapper.transitionStatus(statusId, 2, 3) <= 0) {
            return false;
        }
        status.setStatus(3);
        if (status.getPositionId() != null) {
            positionMapper.updateRecruitedCount(status.getPositionId());
        }

        InternshipStageAssignment assignment = findAssignmentForStatus(status, startTime, true);
        if (assignment != null) {
            assignment.setStatus("IN_PROGRESS");
            if (assignment.getInternshipStartTime() == null) assignment.setInternshipStartTime(startTime);
            assignmentService.update(assignment);
        }
        append(progressRecordService,
                status.getStudentId(),
                "internship_started",
                "实习已开始",
                status.getCompanyName() + " · " + status.getPositionName(),
                "info",
                assignment == null ? status.getId() : assignment.getId(),
                startTime);
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean finishActiveInternship(Long statusId, Date occurredAt) {
        StudentInternshipStatus status = statusMapper.findById(statusId);
        if (status == null || !Integer.valueOf(3).equals(status.getStatus())) {
            return false;
        }
        Date endTime = occurredAt == null ? status.getInternshipEndTime() : occurredAt;
        if (endTime == null || endTime.after(new Date())) {
            return false;
        }
        if (statusMapper.transitionStatus(statusId, 3, 4) <= 0) {
            return false;
        }
        status.setStatus(4);
        if (status.getPositionId() != null) {
            positionMapper.updateRecruitedCount(status.getPositionId());
        }

        InternshipStageAssignment assignment = findAssignmentForStatus(status, endTime, false);
        if (assignment != null) {
            assignment.setStatus("ENDED");
            assignment.setCurrentFlag(0);
            if (assignment.getInternshipEndTime() == null) assignment.setInternshipEndTime(endTime);
            assignmentService.update(assignment);
        }
        append(progressRecordService,
                status.getStudentId(),
                "internship_ended",
                "实习已结束",
                status.getCompanyName() + " · " + status.getPositionName(),
                "info",
                assignment == null ? status.getId() : assignment.getId(),
                endTime);
        return true;
    }

    private InternshipStageAssignment findAssignmentForStatus(StudentInternshipStatus status,
                                                               Date boundaryTime,
                                                               boolean starting) {
        List<InternshipStageAssignment> assignments = assignmentService.findByStudentId(status.getStudentId());
        if (assignments == null) return null;
        Comparator<InternshipStageAssignment> byBoundaryDistance = Comparator
                .<InternshipStageAssignment>comparingLong(
                        item -> boundaryDistance(item, boundaryTime, starting))
                .thenComparing(InternshipStageAssignment::getId,
                        Comparator.nullsLast(Comparator.reverseOrder()));
        return assignments.stream()
                .filter(item -> Integer.valueOf(1).equals(item.getCurrentFlag()))
                .filter(item -> Objects.equals(status.getCompanyId(), item.getCompanyId()))
                .filter(item -> Objects.equals(status.getPositionId(), item.getPositionId()))
                .min(byBoundaryDistance)
                .orElse(null);
    }

    private long boundaryDistance(InternshipStageAssignment assignment,
                                  Date boundaryTime,
                                  boolean starting) {
        Date assignmentBoundary = starting
                ? assignment.getInternshipStartTime()
                : assignment.getInternshipEndTime();
        if (assignmentBoundary == null || boundaryTime == null) return Long.MAX_VALUE;
        long difference = assignmentBoundary.getTime() - boundaryTime.getTime();
        return difference == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(difference);
    }
}
