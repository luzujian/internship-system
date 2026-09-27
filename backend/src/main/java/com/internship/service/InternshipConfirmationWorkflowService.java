package com.internship.service;

import com.internship.entity.InternshipConfirmationRecord;
import com.internship.entity.InternshipStageAssignment;
import com.internship.entity.Position;
import com.internship.entity.StudentInternshipStatus;
import com.internship.entity.dto.InternshipCalendar;
import com.internship.exception.BusinessException;
import com.internship.mapper.InternshipConfirmationRecordMapper;
import com.internship.mapper.InternshipStageAssignmentMapper;
import com.internship.mapper.PositionMapper;
import com.internship.mapper.StudentInternshipStatusMapper;
import com.internship.util.StudentInternshipStatusCache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

import static com.internship.service.InternshipProgressEvents.append;

/**
 * Owns the transactional boundary for submitting and resolving internship confirmations.
 * The current status row is changed only after a company confirms an offer.
 */
@lombok.extern.slf4j.Slf4j
@Service
public class InternshipConfirmationWorkflowService {
    public static final String OFFER_SOURCE = "INTERVIEW_INVITATION";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final InternshipConfirmationRecordMapper confirmationMapper;
    private final InternshipStageAssignmentMapper assignmentMapper;
    private final StudentInternshipStatusMapper statusMapper;
    private final PositionMapper positionMapper;
    private final InternshipProgressRecordService progressRecordService;
    private final StudentInternshipStatusCache statusCache;
    private final InternshipCalendarService internshipCalendarService;
    private final InternshipConfirmationResultNotifier confirmationResultNotifier;

    public InternshipConfirmationWorkflowService(InternshipConfirmationRecordMapper confirmationMapper,
                                                 InternshipStageAssignmentMapper assignmentMapper,
                                                 StudentInternshipStatusMapper statusMapper,
                                                 PositionMapper positionMapper,
                                                 InternshipProgressRecordService progressRecordService,
                                                 StudentInternshipStatusCache statusCache,
                                                 InternshipCalendarService internshipCalendarService,
                                                 InternshipConfirmationResultNotifier confirmationResultNotifier) {
        this.confirmationMapper = confirmationMapper;
        this.assignmentMapper = assignmentMapper;
        this.statusMapper = statusMapper;
        this.positionMapper = positionMapper;
        this.progressRecordService = progressRecordService;
        this.statusCache = statusCache;
        this.internshipCalendarService = internshipCalendarService;
        this.confirmationResultNotifier = confirmationResultNotifier;
    }

    @Transactional(rollbackFor = Exception.class)
    public InternshipConfirmationRecord submitOffer(InternshipConfirmationRecord record,
                                                     Long offerId,
                                                     Long applicationId) {
        if (record == null || record.getStudentId() == null || record.getCompanyId() == null
                || record.getPositionId() == null || offerId == null) {
            throw new BusinessException("确认记录或 Offer 信息不完整");
        }
        lockStudent(record.getStudentId());
        if (confirmationMapper.findActiveByStudentForUpdate(record.getStudentId()) != null) {
            throw new BusinessException("您已有待企业确认的实习确认表");
        }
        ensureDifferentFromCurrent(record.getStudentId(), record.getCompanyId(), record.getPositionId());

        InternshipStageAssignment assignment = assignmentMapper.findBySource(OFFER_SOURCE, offerId);
        if (assignment != null && assignment.getConfirmationRecordId() != null) {
            throw new BusinessException("该 Offer 已提交过实习确认表");
        }
        if (assignment == null) {
            assignment = assignmentMapper.findPendingByStudentAndPosition(
                    record.getStudentId(), record.getCompanyId(), record.getPositionId());
        }
        if (assignment != null && !record.getStudentId().equals(assignment.getStudentId())) {
            throw new BusinessException("Offer 对应的实习经历不属于当前学生");
        }

        record.setStatus(0);
        record.setRecallStatus(0);
        record.setInternshipStartTime(null);
        record.setInternshipEndTime(null);
        record.setInternshipDuration(null);
        if (confirmationMapper.insert(record) <= 0) {
            throw new BusinessException("实习确认表提交失败");
        }

        assignment = preparePendingAssignment(record, assignment, OFFER_SOURCE, offerId);
        if (applicationId != null) assignment.setApplicationId(applicationId);
        assignment.setConfirmationRecordId(record.getId());
        if (assignment.getId() == null) {
            if (assignmentMapper.insert(assignment) <= 0) {
                throw new BusinessException("实习经历记录创建失败");
            }
        } else if (assignmentMapper.update(assignment) <= 0) {
            throw new BusinessException("实习经历记录关联失败");
        }

        append(progressRecordService,
                record.getStudentId(),
                "confirmation_submitted",
                "已提交实习确认表",
                record.getCompanyName() + " · " + record.getPositionName(),
                "pending",
                record.getId(),
                new Date());
        return record;
    }

    /** Legacy draft submission is deliberately unavailable in free-placement mode. */
    public InternshipConfirmationRecord submitDraft(Long studentId, Long recordId, String remark) {
        throw new BusinessException("旧确认提交入口已停用，请提交 offerId");
    }

    @Transactional(rollbackFor = Exception.class)
    public ConfirmationOutcome confirm(Long companyId, Long recordId, Date confirmedAt) {
        InternshipConfirmationRecord initial = confirmationMapper.findById(recordId);
        if (initial == null) throw new BusinessException("确认记录不存在");
        lockStudent(initial.getStudentId());
        InternshipConfirmationRecord record = confirmationMapper.findByIdForUpdate(recordId);
        requireCompanyAndPending(companyId, record);

        InternshipStageAssignment pendingAssignment = assignmentMapper
                .findByConfirmationRecordIdForUpdate(recordId);
        if (pendingAssignment == null
                || !"PENDING_CONFIRMATION".equals(pendingAssignment.getStatus())) {
            throw new BusinessException("待确认实习经历不存在或状态已变化");
        }

        List<InternshipStageAssignment> lockedCurrentAssignments = assignmentMapper
                .findCurrentByStudentForUpdate(record.getStudentId());
        List<InternshipStageAssignment> currentAssignments = new ArrayList<>();
        if (lockedCurrentAssignments != null) {
            // 历史版本可能把待确认行错误标成 current_flag=1；它不是旧当前实习，
            // 但仍已被上面的 FOR UPDATE 锁住，可安全从切换来源中排除。
            lockedCurrentAssignments.stream()
                    .filter(item -> !Objects.equals(item.getId(), pendingAssignment.getId()))
                    .forEach(currentAssignments::add);
        }
        StudentInternshipStatus currentStatus = statusMapper.findByStudentId(record.getStudentId());
        CurrentPlacement current = resolveCurrentPlacement(currentStatus, currentAssignments);
        if (current != null && Objects.equals(current.companyId(), record.getCompanyId())
                && Objects.equals(current.positionId(), record.getPositionId())) {
            throw new BusinessException("当前已在该公司该岗位实习，无需重复确认");
        }

        Date transitionTime = confirmedAt == null ? new Date() : confirmedAt;
        InternshipPeriod period = resolveInternshipPeriod(transitionTime);
        Map<Long, Position> lockedPositions = lockPositionsInOrder(
                current == null ? null : current.positionId(), record.getPositionId());
        Position newPosition = lockedPositions.get(record.getPositionId());
        if (newPosition == null || !record.getCompanyId().equals(newPosition.getCompanyId())) {
            throw new BusinessException("岗位不存在或与企业不匹配");
        }
        if (newPosition.getRemainingQuota() == null || newPosition.getRemainingQuota() <= 0) {
            throw new BusinessException("该岗位已无剩余名额");
        }

        boolean releaseOldQuota = closeCurrentAssignments(
                currentAssignments, current, period.switchBoundary());
        closeCurrentConfirmationRecords(currentAssignments, period.switchBoundary());
        adjustPositionQuotas(lockedPositions, current, newPosition, releaseOldQuota);

        pendingAssignment.setStageNo(1);
        pendingAssignment.setStatus(resolveAssignmentStatus(period));
        pendingAssignment.setCurrentFlag(1);
        pendingAssignment.setInternshipStartTime(period.start());
        pendingAssignment.setInternshipEndTime(period.end());
        if (assignmentMapper.update(pendingAssignment) <= 0) {
            throw new BusinessException("实习经历确认失败");
        }

        record.setStatus(1);
        record.setInternshipStartTime(toLocalDateTime(period.start()));
        record.setInternshipEndTime(toLocalDateTime(period.end()));
        int durationDays = computeDurationDays(period.start(), period.end());
        if (durationDays > 0) record.setInternshipDuration(durationDays);
        if (confirmationMapper.resolvePending(recordId, 1, record.getRemark()) <= 0
                || confirmationMapper.update(record) <= 0) {
            throw new BusinessException("该确认表已被处理，请刷新后重试");
        }

        int statusCode = updateCurrentStatus(currentStatus, record, period);
        append(progressRecordService,
                record.getStudentId(),
                "confirmation_confirmed",
                "企业已确认实习",
                record.getCompanyName() + " · " + record.getPositionName(),
                "success",
                recordId,
                transitionTime);
        evictStatusAfterCommit(record.getStudentId());
        if (confirmationResultNotifier != null) {
            confirmationResultNotifier.afterCommit(new InternshipConfirmationResultNotification(
                    recordId, record.getStudentId(), record.getCompanyId(),
                    record.getCompanyName(), record.getPositionId(), record.getPositionName(),
                    InternshipConfirmationResultNotification.CONFIRMED, null));
        }
        return new ConfirmationOutcome(record, current == null ? null : current.positionId(),
                record.getPositionId(), statusCode);
    }

    @Transactional(rollbackFor = Exception.class)
    public ConfirmationOutcome reject(Long companyId, Long recordId, String reason) {
        InternshipConfirmationRecord initial = confirmationMapper.findById(recordId);
        if (initial == null) throw new BusinessException("确认记录不存在");
        lockStudent(initial.getStudentId());
        InternshipConfirmationRecord record = confirmationMapper.findByIdForUpdate(recordId);
        requireCompanyAndPending(companyId, record);
        if (confirmationMapper.resolvePending(recordId, 2, reason) <= 0) {
            throw new BusinessException("该确认表已被处理，请刷新后重试");
        }
        record.setStatus(2);
        record.setRemark(reason);
        InternshipStageAssignment assignment = assignmentMapper
                .findByConfirmationRecordIdForUpdate(recordId);
        if (assignment != null) {
            assignment.setStatus("REJECTED");
            assignment.setCurrentFlag(0);
            if (assignmentMapper.update(assignment) <= 0) {
                throw new BusinessException("实习经历拒绝状态更新失败");
            }
        }
        append(progressRecordService,
                record.getStudentId(),
                "confirmation_rejected",
                "企业未通过实习确认",
                record.getCompanyName() + " · " + record.getPositionName() + "；原因：" + reason,
                "failed",
                recordId,
                new Date());
        if (confirmationResultNotifier != null) {
            confirmationResultNotifier.afterCommit(new InternshipConfirmationResultNotification(
                    recordId, record.getStudentId(), record.getCompanyId(),
                    record.getCompanyName(), record.getPositionId(), record.getPositionName(),
                    InternshipConfirmationResultNotification.REJECTED, reason));
        }
        return new ConfirmationOutcome(record, null, null, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public ConfirmationOutcome withdraw(Long studentId, Long recordId, String reason) {
        InternshipConfirmationRecord initial = confirmationMapper.findById(recordId);
        if (initial == null) throw new BusinessException("确认记录不存在");
        lockStudent(studentId);
        InternshipConfirmationRecord record = confirmationMapper.findByIdForUpdate(recordId);
        if (record == null || !studentId.equals(record.getStudentId())) {
            throw new BusinessException("无权操作此确认记录");
        }
        requireSubmittedPending(record);
        if (confirmationMapper.recallPending(recordId, reason) <= 0) {
            throw new BusinessException("该确认表已被处理，请刷新后重试");
        }
        record.setRecallStatus(2);
        record.setRecallReason(reason);
        InternshipStageAssignment assignment = assignmentMapper
                .findByConfirmationRecordIdForUpdate(recordId);
        if (assignment != null) {
            assignment.setStatus("WITHDRAWN");
            assignment.setCurrentFlag(0);
            if (assignmentMapper.update(assignment) <= 0) {
                throw new BusinessException("实习经历撤回状态更新失败");
            }
            // 清空确认记录关联，允许学生撤回后重新确认该岗位
            assignmentMapper.clearConfirmationRecordId(assignment.getId());
        }
        append(progressRecordService,
                studentId,
                "confirmation_withdrawn",
                "已撤回实习确认表",
                record.getCompanyName() + " · " + record.getPositionName() + "；原因：" + reason,
                "cancelled",
                recordId,
                new Date());
        return new ConfirmationOutcome(record, null, null, null);
    }

    private InternshipStageAssignment preparePendingAssignment(InternshipConfirmationRecord record,
                                                               InternshipStageAssignment assignment,
                                                               String sourceType,
                                                               Long sourceId) {
        InternshipStageAssignment target = assignment == null ? new InternshipStageAssignment() : assignment;
        if (target.getId() == null) {
            target.setStudentId(record.getStudentId());
            target.setSequenceNo(assignmentMapper.countByStudent(record.getStudentId()) + 1);
        }
        target.setStageNo(1);
        target.setSourceType(sourceType);
        target.setSourceAssignmentId(sourceId);
        target.setCompanyId(record.getCompanyId());
        target.setCompanyName(record.getCompanyName());
        target.setPositionId(record.getPositionId());
        target.setPositionName(record.getPositionName());
        target.setStatus("PENDING_CONFIRMATION");
        target.setCurrentFlag(0);
        return target;
    }

    private void lockStudent(Long studentId) {
        if (studentId == null || confirmationMapper.lockStudent(studentId) == null) {
            throw new BusinessException("学生不存在");
        }
    }

    private void ensureDifferentFromCurrent(Long studentId, Long companyId, Long positionId) {
        StudentInternshipStatus status = statusMapper.findByStudentId(studentId);
        if (status != null && isCurrentStatus(status)
                && Objects.equals(companyId, status.getCompanyId())
                && Objects.equals(positionId, status.getPositionId())) {
            throw new BusinessException("当前已在该公司该岗位实习，无需重复提交");
        }
        List<InternshipStageAssignment> assignments = assignmentMapper.findByStudentId(studentId);
        if (assignments != null && assignments.stream().anyMatch(item ->
                Integer.valueOf(1).equals(item.getCurrentFlag())
                        && Objects.equals(companyId, item.getCompanyId())
                        && Objects.equals(positionId, item.getPositionId()))) {
            throw new BusinessException("当前已在该公司该岗位实习，无需重复提交");
        }
    }

    private void requireCompanyAndPending(Long companyId, InternshipConfirmationRecord record) {
        if (record == null || !Objects.equals(companyId, record.getCompanyId())) {
            throw new BusinessException("无权操作此确认记录");
        }
        requireSubmittedPending(record);
    }

    private void requireSubmittedPending(InternshipConfirmationRecord record) {
        if (!Integer.valueOf(0).equals(record.getStatus())
                || !Integer.valueOf(0).equals(record.getRecallStatus())) {
            throw new BusinessException("该确认表尚未正式提交或已被处理");
        }
    }

    private CurrentPlacement resolveCurrentPlacement(StudentInternshipStatus status,
                                                     List<InternshipStageAssignment> assignments) {
        CurrentPlacement fromStatus = status != null && isCurrentStatus(status)
                && status.getCompanyId() != null && status.getPositionId() != null
                ? new CurrentPlacement(status.getCompanyId(), status.getPositionId()) : null;
        TreeSet<String> assignmentPairs = new TreeSet<>();
        CurrentPlacement fromAssignment = null;
        for (InternshipStageAssignment assignment : assignments) {
            if (assignment.getCompanyId() == null || assignment.getPositionId() == null) continue;
            if (!"CONFIRMED".equals(assignment.getStatus())
                    && !"IN_PROGRESS".equals(assignment.getStatus())) continue;
            assignmentPairs.add(assignment.getCompanyId() + ":" + assignment.getPositionId());
            fromAssignment = new CurrentPlacement(assignment.getCompanyId(), assignment.getPositionId());
        }
        if (assignmentPairs.size() > 1) {
            throw new BusinessException("当前实习经历存在多个不同岗位，请先联系管理员处理");
        }
        if (fromStatus != null && fromAssignment != null
                && (!Objects.equals(fromStatus.companyId(), fromAssignment.companyId())
                || !Objects.equals(fromStatus.positionId(), fromAssignment.positionId()))) {
            throw new BusinessException("当前实习状态与经历记录不一致，请先联系管理员处理");
        }
        return fromStatus != null ? fromStatus : fromAssignment;
    }

    private boolean isCurrentStatus(StudentInternshipStatus status) {
        return Integer.valueOf(2).equals(status.getStatus()) || Integer.valueOf(3).equals(status.getStatus());
    }

    private Map<Long, Position> lockPositionsInOrder(Long oldPositionId, Long newPositionId) {
        TreeSet<Long> ids = new TreeSet<>();
        if (oldPositionId != null) ids.add(oldPositionId);
        if (newPositionId != null) ids.add(newPositionId);
        Map<Long, Position> positions = new LinkedHashMap<>();
        for (Long id : ids) {
            Position position = positionMapper.findByIdForUpdate(id);
            if (position == null) throw new BusinessException("岗位不存在：" + id);
            positions.put(id, position);
        }
        return positions;
    }

    private boolean closeCurrentAssignments(List<InternshipStageAssignment> assignments,
                                            CurrentPlacement current,
                                            Date switchBoundary) {
        boolean releaseOldQuota = current != null && assignments.isEmpty();
        for (InternshipStageAssignment assignment : assignments) {
            Date boundary = switchBoundary;
            if (assignment.getInternshipStartTime() != null
                    && boundary.before(assignment.getInternshipStartTime())) {
                boundary = assignment.getInternshipStartTime();
            }
            assignment.setInternshipEndTime(boundary);
            assignment.setStatus("REPLACED");
            assignment.setCurrentFlag(0);
            if (assignmentMapper.update(assignment) <= 0) {
                throw new BusinessException("旧实习经历关闭失败");
            }
            if (current != null && Objects.equals(current.positionId(), assignment.getPositionId())
                    && assignmentMapper.markQuotaReleased(assignment.getId(), "POSITION_SWITCH") > 0) {
                releaseOldQuota = true;
            }
        }
        return releaseOldQuota;
    }

    private void closeCurrentConfirmationRecords(List<InternshipStageAssignment> assignments,
                                                 Date switchBoundary) {
        TreeSet<Long> recordIds = new TreeSet<>();
        for (InternshipStageAssignment assignment : assignments) {
            if (assignment.getConfirmationRecordId() != null) {
                recordIds.add(assignment.getConfirmationRecordId());
            }
        }
        for (Long id : recordIds) {
            InternshipConfirmationRecord oldRecord = confirmationMapper.findByIdForUpdate(id);
            if (oldRecord == null || !Integer.valueOf(1).equals(oldRecord.getStatus())) continue;
            LocalDateTime boundary = toLocalDateTime(switchBoundary);
            if (oldRecord.getInternshipStartTime() != null
                    && boundary.isBefore(oldRecord.getInternshipStartTime())) {
                boundary = oldRecord.getInternshipStartTime();
            }
            oldRecord.setInternshipEndTime(boundary);
            oldRecord.setInternshipDuration(durationDays(
                    oldRecord.getInternshipStartTime(), oldRecord.getInternshipEndTime()));
            if (confirmationMapper.update(oldRecord) <= 0) {
                throw new BusinessException("旧确认记录分段失败");
            }
        }
    }

    private void adjustPositionQuotas(Map<Long, Position> positions,
                                      CurrentPlacement current,
                                      Position newPosition,
                                      boolean releaseOldQuota) {
        if (releaseOldQuota && current != null) {
            Position oldPosition = positions.get(current.positionId());
            oldPosition.setRemainingQuota((oldPosition.getRemainingQuota() == null
                    ? 0 : oldPosition.getRemainingQuota()) + 1);
            oldPosition.setRecruitedCount(Math.max(0, (oldPosition.getRecruitedCount() == null
                    ? 0 : oldPosition.getRecruitedCount()) - 1));
        }
        newPosition.setRemainingQuota(newPosition.getRemainingQuota() - 1);
        newPosition.setRecruitedCount((newPosition.getRecruitedCount() == null
                ? 0 : newPosition.getRecruitedCount()) + 1);
        for (Position position : positions.values()) {
            boolean changedOld = releaseOldQuota && current != null
                    && Objects.equals(position.getId(), current.positionId());
            if ((changedOld || Objects.equals(position.getId(), newPosition.getId()))
                    && positionMapper.update(position) <= 0) {
                throw new BusinessException("岗位名额更新失败");
            }
        }
    }

    private InternshipPeriod resolveInternshipPeriod(Date confirmedAt) {
        Date start = confirmedAt;
        Date end = null;
        Date switchBoundary = confirmedAt;
        // 优先使用教师在「重要设置」中配置的全局实习日历作为起止范围
        InternshipCalendar cal = null;
        try {
            cal = internshipCalendarService.getCalendar();
        } catch (Exception e) {
            log.warn("resolveInternshipPeriod 读取全局实习日历失败，将回退到默认值：{}", e.getMessage());
        }
        if (cal != null && cal.getStartDate() != null && cal.getEndDate() != null) {
            try {
                start = Date.from(LocalDate.parse(cal.getStartDate()).atStartOfDay(BUSINESS_ZONE).toInstant());
                end = Date.from(LocalDate.parse(cal.getEndDate()).atTime(LocalTime.of(23, 59, 59)).atZone(BUSINESS_ZONE).toInstant());
            } catch (Exception ex) {
                log.warn("解析全局实习日历日期失败（start={}, end={}）：{}", cal.getStartDate(), cal.getEndDate(), ex.getMessage());
            }
        }
        return new InternshipPeriod(start, end, switchBoundary);
    }

    /**
     * 根据「确认时间 vs 全局日历起止」计算确认当下应落到的学生实习状态码：
     *   now < start → 2（已确认，等待开始，次日 01:00 由定时任务切到 3）
     *   now ∈ [start, end] → 3（实习中）
     *   now > end → 4（已结束，同样定时任务会做后续清理）
     * 若 end 为空，退化到仅与 start 比较。
     */
    private int resolveStatusCodeForPeriod(InternshipPeriod period) {
        Date now = new Date();
        if (period == null || period.start() == null) return 3;
        if (now.before(period.start())) return 2;
        if (period.end() != null && now.after(period.end())) return 4;
        return 3;
    }

    /**
     * Assignment 的字符串状态与学生状态保持一致，但这里用字符串便于和前端生命周期展示直接匹配，
     * 也与定时任务切状态时写入的字符串保持一致。
     */
    private String resolveAssignmentStatus(InternshipPeriod period) {
        int code = resolveStatusCodeForPeriod(period);
        return switch (code) {
            case 2 -> "CONFIRMED";
            case 4 -> "ENDED";
            default -> "IN_PROGRESS";
        };
    }

    private int computeDurationDays(Date start, Date end) {
        if (start == null || end == null || end.before(start)) return 0;
        return Math.toIntExact(ChronoUnit.DAYS.between(
                start.toInstant().atZone(BUSINESS_ZONE).toLocalDate(),
                end.toInstant().atZone(BUSINESS_ZONE).toLocalDate()));
    }

    private int updateCurrentStatus(StudentInternshipStatus status,
                                    InternshipConfirmationRecord record,
                                    InternshipPeriod period) {
        StudentInternshipStatus target = status == null ? new StudentInternshipStatus() : status;
        target.setStudentId(record.getStudentId());
        target.setStatus(resolveStatusCodeForPeriod(period));
        target.setCompanyId(record.getCompanyId());
        target.setCompanyName(record.getCompanyName());
        target.setPositionId(record.getPositionId());
        target.setPositionName(record.getPositionName());
        target.setCompanyAddress(record.getCompanyAddress());
        target.setCompanyPhone(record.getCompanyPhone());
        target.setContactPhone(record.getContactPhone());
        target.setEmail(record.getEmail());
        target.setStudentName(record.getStudentName());
        target.setGrade(record.getGrade());
        target.setMajorName(record.getMajor());
        target.setClassName(record.getClassName());
        target.setInternshipStartTime(period.start());
        target.setInternshipEndTime(period.end());
        if (record.getInternshipDuration() == null || record.getInternshipDuration() <= 0) {
            int days = computeDurationDays(period.start(), period.end());
            if (days > 0) target.setInternshipDuration(days);
        } else {
            target.setInternshipDuration(record.getInternshipDuration());
        }
        target.setCompanyConfirmStatus(1);
        target.setRecallStatus(0);
        target.setRecallReason(null);
        target.setRecallApplyTime(null);
        target.setRecallAuditTime(null);
        target.setRecallReviewerId(null);
        target.setRecallAuditRemark(null);
        target.setIsDelayed(false);
        target.setIsInterrupted(false);
        target.setRemark(record.getRemark());
        target.setUpdateTime(new Date());
        if (target.getId() == null) {
            target.setHasComplaint(false);
            target.setCreateTime(new Date());
            if (statusMapper.insert(target) <= 0) throw new BusinessException("当前实习状态创建失败");
        } else if (statusMapper.updateCurrentPlacement(target) <= 0) {
            throw new BusinessException("当前实习状态更新失败");
        }
        return target.getStatus();
    }

    private void evictStatusAfterCommit(Long studentId) {
        Runnable action = () -> statusCache.evictCache(studentId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private LocalDateTime toLocalDateTime(Date value) {
        if (value == null) {
            return null;
        }
        return value.toInstant().atZone(BUSINESS_ZONE).toLocalDateTime();
    }

    private int durationDays(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || end.isBefore(start)) return 0;
        return Math.toIntExact(ChronoUnit.DAYS.between(start.toLocalDate(), end.toLocalDate()));
    }

    private record CurrentPlacement(Long companyId, Long positionId) {
    }

    private record InternshipPeriod(Date start, Date end, Date switchBoundary) {
    }

    public record ConfirmationOutcome(InternshipConfirmationRecord record,
                                      Long oldPositionId,
                                      Long newPositionId,
                                      Integer internshipStatus) {
    }
}
