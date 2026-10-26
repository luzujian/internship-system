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

    @org.springframework.beans.factory.annotation.Autowired
    private InternshipAgreementService agreementService;

    @org.springframework.beans.factory.annotation.Autowired
    private com.internship.mapper.StudentApplicationMapper studentApplicationMapper;
    @org.springframework.beans.factory.annotation.Autowired
    private com.internship.mapper.CompanyUserMapper companyUserMapper;

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
        InternshipAgreementService.requireFileIds(record.getAgreementFileIds());
        lockStudent(record.getStudentId());
        if (studentApplicationMapper.findPendingByStudentAndTypeForUpdate(record.getStudentId(), "selfPractice") != null) {
            throw new BusinessException("您已有待处理的自主实习申请，请先处理或撤回");
        }
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
        agreementService.bindSubmission(record.getStudentId(), "CONFIRMATION", record.getId(), record.getAgreementFileIds());

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

    /** 自主申请与协议已落库；这里只建立本轮待企业确认记录，不创建 Offer、不占名额。 */
    @Transactional(rollbackFor = Exception.class)
    public InternshipConfirmationRecord submitSelfPractice(InternshipConfirmationRecord record, Long applicationId) {
        lockStudent(record.getStudentId());
        if (confirmationMapper.findActiveByStudentForUpdate(record.getStudentId()) != null) {
            throw new BusinessException("您已有待企业确认的实习申请");
        }
        ensureDifferentFromCurrent(record);
        record.setSourceApplicationId(applicationId);
        record.setStatus(0);
        record.setRecallStatus(0);
        if (confirmationMapper.insert(record) <= 0) throw new BusinessException("实习确认记录创建失败");
        InternshipStageAssignment assignment = preparePendingAssignment(record, null, "SELF_PRACTICE", applicationId);
        assignment.setApplicationId(applicationId);
        assignment.setConfirmationRecordId(record.getId());
        if (assignmentMapper.insert(assignment) <= 0) throw new BusinessException("待确认实习记录创建失败");
        if (studentApplicationMapper.linkConfirmation(applicationId, record.getId()) <= 0) {
            throw new BusinessException("自主实习申请关联失败");
        }
        return record;
    }

    @Transactional(rollbackFor = Exception.class)
    public ConfirmationOutcome confirm(Long companyId, Long recordId, Date confirmedAt) {
        return confirm(companyId, recordId, confirmedAt, null, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public ConfirmationOutcome confirm(Long companyId, Long recordId, Date confirmedAt, Long selectedPositionId, boolean textPosition) {
        InternshipConfirmationRecord initial = confirmationMapper.findById(recordId);
        if (initial == null) throw new BusinessException("确认记录不存在");
        lockStudent(initial.getStudentId());
        lockLinkedApplication(initial);
        InternshipConfirmationRecord record = confirmationMapper.findByIdForUpdate(recordId);
        requireCompanyAndPending(companyId, record);
        if (record.getSourceApplicationId() != null) {
            var company = companyUserMapper.findById(companyId);
            if (company == null || !Integer.valueOf(1).equals(company.getStatus())
                    || !Integer.valueOf(1).equals(company.getAuditStatus()) || Integer.valueOf(1).equals(company.getRecallStatus())) {
                throw new BusinessException("企业当前不具备有效合作资格，无法确认");
            }
        }
        if (record.getPositionId() != null && (textPosition || selectedPositionId != null
                && !Objects.equals(record.getPositionId(), selectedPositionId))) {
            throw new BusinessException("学生已选择具体岗位，不可改为其他岗位或文字确认，请退回学生核对");
        }
        if (textPosition && selectedPositionId != null) throw new BusinessException("请只选择一种岗位确认方式");
        if (record.getSourceApplicationId() != null && record.getPositionId() == null && selectedPositionId == null) {
            if (!textPosition) throw new BusinessException("请选择关联已有岗位或按文字岗位确认");
            boolean existingName = positionMapper.findByCompanyId(companyId).stream()
                    .anyMatch(p -> normalizedPositionName(p.getPositionName()).equals(normalizedPositionName(record.getPositionName())));
            if (existingName) throw new BusinessException("该岗位已在系统中发布，请关联已有岗位后确认");
        }
        if (selectedPositionId != null) record.setPositionId(selectedPositionId);
        if (record.getPositionId() == null && (record.getSourceApplicationId() == null
                || record.getPositionName() == null || record.getPositionName().isBlank())) {
            throw new BusinessException("实习岗位信息不完整");
        }

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
        if (samePlacement(current, record)) {
            throw new BusinessException("当前已在该公司该岗位实习，无需重复确认");
        }

        Date transitionTime = confirmedAt == null ? new Date() : confirmedAt;
        InternshipPeriod period = resolveInternshipPeriod(transitionTime);
        Map<Long, Position> lockedPositions = lockPositionsInOrder(
                current == null ? null : current.positionId(), record.getPositionId());
        Position newPosition = lockedPositions.get(record.getPositionId());
        if (record.getPositionId() != null && (newPosition == null || !record.getCompanyId().equals(newPosition.getCompanyId()))) {
            throw new BusinessException("岗位不存在或与企业不匹配");
        }
        if (newPosition != null && (newPosition.getRemainingQuota() == null || newPosition.getRemainingQuota() <= 0)) {
            throw new BusinessException("该岗位已无剩余名额");
        }
        if (newPosition != null) record.setPositionName(newPosition.getPositionName());
        record.setCompanyPhone(com.internship.entity.dto.InternshipContact.resolve(record.getCompanyId(),
                newPosition == null ? companyUserMapper.findById(record.getCompanyId()) : null, newPosition).phone());

        boolean releaseOldQuota = closeCurrentAssignments(
                currentAssignments, current, period.switchBoundary());
        closeCurrentConfirmationRecords(currentAssignments, period.switchBoundary());
        adjustPositionQuotas(lockedPositions, current, newPosition, releaseOldQuota);

        pendingAssignment.setStageNo(1);
        pendingAssignment.setPositionId(record.getPositionId());
        pendingAssignment.setPositionName(record.getPositionName());
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
        resolveLinkedApplication(record, "approved", null);
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

    private static String normalizedPositionName(String name) {
        return java.text.Normalizer.normalize(Objects.toString(name, ""), java.text.Normalizer.Form.NFKC)
                .replaceAll("[\\s\\p{Z}]+", "").toLowerCase(java.util.Locale.ROOT);
    }

    @Transactional(rollbackFor = Exception.class)
    public ConfirmationOutcome reject(Long companyId, Long recordId, String reason) {
        InternshipConfirmationRecord initial = confirmationMapper.findById(recordId);
        if (initial == null) throw new BusinessException("确认记录不存在");
        lockStudent(initial.getStudentId());
        lockLinkedApplication(initial);
        InternshipConfirmationRecord record = confirmationMapper.findByIdForUpdate(recordId);
        requireCompanyAndPending(companyId, record);
        if (confirmationMapper.resolvePending(recordId, 2, reason) <= 0) {
            throw new BusinessException("该确认表已被处理，请刷新后重试");
        }
        record.setStatus(2);
        record.setRemark(reason);
        resolveLinkedApplication(record, "rejected", reason);
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
        lockLinkedApplication(initial);
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
        resolveLinkedApplication(record, "withdrawn", null);
        InternshipStageAssignment assignment = assignmentMapper
                .findByConfirmationRecordIdForUpdate(recordId);
        if (assignment != null) {
            assignment.setStatus("WITHDRAWN");
            assignment.setCurrentFlag(0);
            if (assignmentMapper.update(assignment) <= 0) {
                throw new BusinessException("实习经历撤回状态更新失败");
            }
            // 清空确认记录关联，允许学生撤回后重新确认该岗位
            if (record.getSourceApplicationId() == null) assignmentMapper.clearConfirmationRecordId(assignment.getId());
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
        InternshipConfirmationRecord record = new InternshipConfirmationRecord();
        record.setStudentId(studentId); record.setCompanyId(companyId); record.setPositionId(positionId);
        ensureDifferentFromCurrent(record);
    }

    private void ensureDifferentFromCurrent(InternshipConfirmationRecord record) {
        StudentInternshipStatus status = statusMapper.findByStudentId(record.getStudentId());
        if (status != null && isCurrentStatus(status) && samePlacement(
                new CurrentPlacement(status.getCompanyId(), status.getPositionId(), status.getCompanyName(), status.getPositionName()), record)) {
            throw new BusinessException("当前已在该公司该岗位实习，无需重复提交");
        }
        List<InternshipStageAssignment> assignments = assignmentMapper.findByStudentId(record.getStudentId());
        if (assignments != null && assignments.stream().anyMatch(item -> Integer.valueOf(1).equals(item.getCurrentFlag())
                && samePlacement(new CurrentPlacement(item.getCompanyId(), item.getPositionId(), item.getCompanyName(), item.getPositionName()), record))) {
            throw new BusinessException("当前已在该公司该岗位实习，无需重复提交");
        }
    }

    private boolean samePlacement(CurrentPlacement current, InternshipConfirmationRecord record) {
        if (current == null) return false;
        boolean companyMatches = current.companyId() != null && record.getCompanyId() != null
                ? Objects.equals(current.companyId(), record.getCompanyId())
                : normalized(current.companyName()).equals(normalized(record.getCompanyName()));
        boolean positionMatches = current.positionId() != null && record.getPositionId() != null
                ? Objects.equals(current.positionId(), record.getPositionId())
                : current.positionId() == null && record.getPositionId() == null
                    && normalized(current.positionName()).equals(normalized(record.getPositionName()));
        return companyMatches && positionMatches;
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private void lockLinkedApplication(InternshipConfirmationRecord record) {
        if (record.getSourceApplicationId() == null) return;
        var application = studentApplicationMapper.findByIdForUpdate(record.getSourceApplicationId());
        if (application == null || !application.isCompanyReview() || !"pending".equals(application.getStatus())
                || !Objects.equals(application.getConfirmationRecordId(), record.getId())
                || !Objects.equals(application.getStudentId(), record.getStudentId())
                || !Objects.equals(application.getCompanyId(), record.getCompanyId())) {
            throw new BusinessException("对应自主实习申请已处理、已重提或不属于该企业，请刷新");
        }
    }

    private void resolveLinkedApplication(InternshipConfirmationRecord record, String status, String reason) {
        if (record.getSourceApplicationId() != null && studentApplicationMapper.resolveCompanyApplication(
                record.getSourceApplicationId(), record.getId(), status, reason, record.getPositionId()) <= 0) {
            throw new BusinessException("对应自主实习申请状态已变化，请刷新");
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
                && (status.getCompanyId() != null || status.getCompanyName() != null)
                ? new CurrentPlacement(status.getCompanyId(), status.getPositionId(), status.getCompanyName(), status.getPositionName()) : null;
        TreeSet<String> assignmentPairs = new TreeSet<>();
        CurrentPlacement fromAssignment = null;
        for (InternshipStageAssignment assignment : assignments) {
            if (assignment.getCompanyId() == null && assignment.getCompanyName() == null) continue;
            if (!"CONFIRMED".equals(assignment.getStatus())
                    && !"IN_PROGRESS".equals(assignment.getStatus())) continue;
            fromAssignment = new CurrentPlacement(assignment.getCompanyId(), assignment.getPositionId(), assignment.getCompanyName(), assignment.getPositionName());
            assignmentPairs.add(placementKey(fromAssignment));
        }
        if (assignmentPairs.size() > 1) {
            throw new BusinessException("当前实习经历存在多个不同岗位，请先联系管理员处理");
        }
        if (fromStatus != null && fromAssignment != null
                && !placementKey(fromStatus).equals(placementKey(fromAssignment))) {
            throw new BusinessException("当前实习状态与经历记录不一致，请先联系管理员处理");
        }
        return fromStatus != null ? fromStatus : fromAssignment;
    }

    private String placementKey(CurrentPlacement value) {
        return (value.companyId() != null ? "id:" + value.companyId() : "name:" + normalized(value.companyName())) + "/"
                + (value.positionId() != null ? "id:" + value.positionId() : "name:" + normalized(value.positionName()));
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
        boolean releaseOldQuota = current != null && current.positionId() != null && assignments.isEmpty();
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
            if (current != null && current.positionId() != null && Objects.equals(current.positionId(), assignment.getPositionId())
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
        if (newPosition != null) {
            newPosition.setRemainingQuota(newPosition.getRemainingQuota() - 1);
            newPosition.setRecruitedCount((newPosition.getRecruitedCount() == null ? 0 : newPosition.getRecruitedCount()) + 1);
        }
        for (Position position : positions.values()) {
            boolean changedOld = releaseOldQuota && current != null
                    && Objects.equals(position.getId(), current.positionId());
            if ((changedOld || newPosition != null && Objects.equals(position.getId(), newPosition.getId()))
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

    private record CurrentPlacement(Long companyId, Long positionId, String companyName, String positionName) {
    }

    private record InternshipPeriod(Date start, Date end, Date switchBoundary) {
    }

    public record ConfirmationOutcome(InternshipConfirmationRecord record,
                                      Long oldPositionId,
                                      Long newPositionId,
                                      Integer internshipStatus) {
    }
}
