package com.internship.service.impl;

import com.internship.entity.InternshipTimeSettings;
import com.internship.entity.InternshipConfirmationRecord;
import com.internship.entity.InternshipStageAssignment;
import com.internship.entity.PageResult;
import com.internship.entity.CompanyUser;
import com.internship.entity.StudentApplication;
import com.internship.entity.StudentInternshipStatus;
import com.internship.entity.StudentUser;
import com.internship.config.EmailNotifyScope;
import com.internship.config.SystemSettingsConfig;
import com.internship.exception.BusinessException;
import com.internship.mapper.StudentApplicationMapper;
import com.internship.mapper.CompanyUserMapper;
import com.internship.util.StudentInternshipStatusCache;
import com.internship.mapper.StudentInternshipStatusMapper;
import com.internship.service.InternshipTimeSettingsService;
import com.internship.service.InternshipCalendarService;
import com.internship.service.InternshipConfirmationRecordService;
import com.internship.service.InternshipLifecycleService;
import com.internship.service.InternshipProgressRecordService;
import com.internship.service.EmailService;
import com.internship.service.InternshipStageAssignmentService;
import com.internship.service.StudentApplicationService;
import com.internship.service.StudentUserService;
import com.internship.entity.dto.InternshipCalendar;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.internship.service.InternshipProgressEvents.append;

/**
 * 学生申请服务实现类
 */
@Slf4j
@Service
public class StudentApplicationServiceImpl implements StudentApplicationService {

    @org.springframework.beans.factory.annotation.Autowired
    private com.internship.service.InternshipAgreementService agreementService;
    @org.springframework.beans.factory.annotation.Autowired
    private com.internship.service.InternshipConfirmationWorkflowService confirmationWorkflowService;
    @org.springframework.beans.factory.annotation.Autowired
    private com.internship.mapper.PositionMapper positionMapper;
    @org.springframework.beans.factory.annotation.Autowired
    private com.internship.mapper.InternshipStageAssignmentMapper stageAssignmentMapper;
    @org.springframework.beans.factory.annotation.Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private com.internship.websocket.AnnouncementWebSocketHandler webSocketHandler;
    @org.springframework.beans.factory.annotation.Autowired
    private com.internship.service.StudentJobApplicationService studentJobApplicationService;

    private final StudentApplicationMapper studentApplicationMapper;
    private final CompanyUserMapper companyUserMapper;
    private final InternshipTimeSettingsService internshipTimeSettingsService;
    private final StudentInternshipStatusMapper internshipStatusMapper;
    private final InternshipProgressRecordService progressRecordService;
    private final InternshipConfirmationRecordService confirmationRecordService;
    private final InternshipStageAssignmentService stageAssignmentService;
    private final StudentInternshipStatusCache statusCache;
    private final InternshipCalendarService internshipCalendarService;
    private final InternshipLifecycleService lifecycleService;
    private final StudentUserService studentUserService;
    private final EmailService emailService;

    public StudentApplicationServiceImpl(StudentApplicationMapper studentApplicationMapper,
                                         CompanyUserMapper companyUserMapper,
                                         InternshipTimeSettingsService internshipTimeSettingsService,
                                         StudentInternshipStatusMapper internshipStatusMapper,
                                         InternshipProgressRecordService progressRecordService,
                                         InternshipConfirmationRecordService confirmationRecordService,
                                         InternshipStageAssignmentService stageAssignmentService,
                                         StudentInternshipStatusCache statusCache,
                                         InternshipCalendarService internshipCalendarService,
                                         InternshipLifecycleService lifecycleService,
                                         StudentUserService studentUserService,
                                         EmailService emailService) {
        this.studentApplicationMapper = studentApplicationMapper;
        this.companyUserMapper = companyUserMapper;
        this.internshipTimeSettingsService = internshipTimeSettingsService;
        this.internshipStatusMapper = internshipStatusMapper;
        this.progressRecordService = progressRecordService;
        this.confirmationRecordService = confirmationRecordService;
        this.stageAssignmentService = stageAssignmentService;
        this.statusCache = statusCache;
        this.internshipCalendarService = internshipCalendarService;
        this.lifecycleService = lifecycleService;
        this.studentUserService = studentUserService;
        this.emailService = emailService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insert(StudentApplication application) {
        log.debug("插入学生申请: {}", application.getStudentName());

        validateApplicationInfo(application);
        validateSelfPracticeInfo(application);

        if ("selfPractice".equals(application.getApplicationType())) {
            com.internship.service.InternshipAgreementService.requireFileIds(application.getAgreementFileIds());
        }

        if ("unitChange".equals(application.getApplicationType())) {
            throw new BusinessException("单位变更申请流程已停用，请申请新岗位并提交新的实习确认表");
        }

        // 以学生主记录作为串行化源，避免重复点击在并发事务中插入两条 pending 申请和两条动态。
        studentApplicationMapper.lockStudent(application.getStudentId());
        prepareReviewTarget(application);
        StudentApplication pending = studentApplicationMapper.findPendingByStudentAndTypeForUpdate(
                application.getStudentId(), application.getApplicationType());
        if (pending != null) {
            throw new BusinessException("您已有待审核的" + applicationTitle(application.getApplicationType()));
        }

        int result = studentApplicationMapper.insert(application);
        if (result > 0) {
            if ("selfPractice".equals(application.getApplicationType())) {
                var version = agreementService.bindSubmission(application.getStudentId(), "SELF_PRACTICE", application.getId(), application.getAgreementFileIds());
                submitCompanyConfirmation(application, version == null ? null : version.getId());
            }
            appendSubmittedEvent(application);
        }
        log.info("学生申请插入成功，学生: {}", application.getStudentName());
        return result;
    }

    private void prepareReviewTarget(StudentApplication application) {
        application.setReviewTarget("COUNSELOR");
        application.setConfirmationRecordId(null);
        application.setReviewerId(null);
        application.setReviewTime(null);
        application.setRejectReason(null);
        if (!"selfPractice".equals(application.getApplicationType())) {
            application.setCompanyId(null); application.setPositionId(null);
            return;
        }
        if (confirmationRecordService.findByStudentId(application.getStudentId()).stream()
                .anyMatch(r -> Integer.valueOf(0).equals(r.getStatus()) && Integer.valueOf(0).equals(r.getRecallStatus()))) {
            throw new BusinessException("您已有待企业确认的实习申请，请先处理或撤回");
        }
        CompanyUser company = companyUserMapper.findApprovedByCompanyName(application.getCompany());
        if (company == null) {
            if (application.getCompanyId() != null || application.getPositionId() != null) {
                throw new BusinessException("企业或岗位信息与有效合作企业不匹配，请重新选择");
            }
            return;
        }
        if (application.getCompanyId() != null && !company.getId().equals(application.getCompanyId())) {
            throw new BusinessException("企业信息不匹配，请重新选择");
        }
        application.setCompanyId(company.getId());
        application.setCompany(company.getCompanyName());
        application.setReviewTarget("COMPANY");
        if (application.getPositionId() != null) {
            var position = positionMapper.findById(application.getPositionId());
            if (position == null || !company.getId().equals(position.getCompanyId())) {
                throw new BusinessException("所选岗位不属于该企业");
            }
            application.setPositionName(position.getPositionName());
        }
    }

    private void submitCompanyConfirmation(StudentApplication application, Long agreementVersionId) {
        if (!application.isCompanyReview()) return;
        InternshipConfirmationRecord record = new InternshipConfirmationRecord();
        record.setStudentId(application.getStudentId());
        record.setStudentName(application.getStudentName());
        record.setStudentUserId(application.getStudentUserId());
        record.setGrade(application.getGrade()); record.setClassName(application.getClassName());
        StudentUser student = studentUserService.findById(application.getStudentId());
        if (student != null) {
            record.setGender(student.getGender() == null ? null : student.getGender().toString());
            record.setMajor(student.getMajor()); record.setContactPhone(student.getPhone()); record.setEmail(student.getEmail());
        }
        CompanyUser company = companyUserMapper.findById(application.getCompanyId());
        record.setCompanyId(application.getCompanyId()); record.setCompanyName(application.getCompany());
        record.setCompanyAddress(company == null ? application.getCompanyCity() : company.getAddress());
        var position = application.getPositionId() == null ? null : positionMapper.findById(application.getPositionId());
        record.setCompanyPhone(com.internship.entity.dto.InternshipContact.resolve(application.getCompanyId(), company, position).phone());
        record.setPositionId(application.getPositionId()); record.setPositionName(application.getPositionName());
        record.setRemark(application.getReason()); record.setAgreementSubmissionId(agreementVersionId);
        try { record.setApplicationSnapshot(objectMapper.writeValueAsString(application)); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new BusinessException("申请信息保存失败"); }
        confirmationWorkflowService.submitSelfPractice(record, application.getId());
        application.setConfirmationRecordId(record.getId());
        notifyCompanyAfterCommit(application.getCompanyId());
    }

    private void notifyCompanyAfterCommit(Long companyId) {
        Runnable notify = () -> {
            try {
                if (webSocketHandler == null) return;
                long pendingApplications = studentJobApplicationService.findByCompanyId(companyId).stream()
                        .filter(item -> "pending".equals(item.getStatus())).count();
                long pendingConfirmations = confirmationRecordService.findByCompanyIdAndStatus(companyId, 0).size();
                webSocketHandler.sendCompanyTodoUpdate(companyId, pendingApplications, pendingConfirmations);
            } catch (Exception e) { log.warn("推送企业确认待办失败：{}", e.getMessage()); }
        };
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void afterCommit() { notify.run(); }
                    });
        } else { notify.run(); }
    }
    
    @Override
    public StudentApplication findById(Long id) {
        log.debug("根据ID查询学生申请，ID: {}", id);
        
        if (id == null || id <= 0) {
            throw new BusinessException("申请ID无效");
        }
        
        StudentApplication application = studentApplicationMapper.findById(id);
        markCooperativeCompanyFlag(application == null
                ? java.util.List.of()
                : java.util.List.of(application));
        return application;
    }
    
    @Override
    public List<StudentApplication> findByStudentId(Long studentId) {
        log.debug("根据学生ID查询申请列表，学生ID: {}", studentId);
        return studentApplicationMapper.findByStudentId(studentId);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int update(StudentApplication application) {
        log.debug("更新学生申请，ID: {}", application.getId());
        
        if (application.getId() == null || application.getId() <= 0) {
            throw new BusinessException("申请ID无效");
        }
        
        StudentApplication existingApplication = studentApplicationMapper.findById(application.getId());
        if (existingApplication == null) {
            throw new BusinessException("申请不存在");
        }
        if (existingApplication.isCompanyReview()) throw new BusinessException("该申请由企业处理，请通过撤回或重新提交办理");
        
        validateApplicationInfo(application);
        
        int result = studentApplicationMapper.update(application);
        log.info("学生申请更新成功，ID: {}", application.getId());
        return result;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        log.debug("删除学生申请，ID: {}", id);
        
        if (id == null || id <= 0) {
            throw new BusinessException("申请ID无效");
        }
        
        StudentApplication application = studentApplicationMapper.findById(id);
        if (application == null) {
            throw new BusinessException("申请不存在");
        }
        
        int result = studentApplicationMapper.deleteById(id);
        log.info("学生申请删除成功，ID: {}", id);
        return result;
    }
    
    @Override
    public List<StudentApplication> findAll() {
        log.debug("查询所有学生申请");
        return studentApplicationMapper.findAll();
    }
    
    @Override
    public List<StudentApplication> list(String applicationType, String status, String studentName, String studentUserId) {
        return list(applicationType, status, studentName, studentUserId, null);
    }

    @Override
    public List<StudentApplication> list(String applicationType, String status, String studentName, String studentUserId, java.util.List<Long> classIds) {
        log.debug("动态条件查询学生申请，类型: {}, 状态: {}, 姓名: {}, 学号: {}, 班级数: {}",
                applicationType, status, studentName, studentUserId,
                classIds == null ? 0 : classIds.size());
        return studentApplicationMapper.list(applicationType, status, studentName, studentUserId, classIds, null);
    }
    
    @Override
    public List<StudentApplication> findByIds(List<Long> ids) {
        log.debug("根据ID列表查询学生申请，ID列表: {}", ids);
        
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        
        return studentApplicationMapper.selectByIds(ids);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchDelete(List<Long> ids) {
        log.debug("批量删除学生申请，ID列表: {}", ids);
        
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("申请ID列表不能为空");
        }
        
        int result = studentApplicationMapper.batchDeleteByIds(ids);
        log.info("批量删除学生申请成功，删除数量: {}", result);
        return result;
    }
    
    @Override
    public Long count() {
        log.debug("查询学生申请总数");
        return studentApplicationMapper.count();
    }
    
    @Override
    public PageResult<StudentApplication> findPage(Integer page, Integer pageSize) {
        log.debug("分页查询学生申请，页码: {}, 每页大小: {}", page, pageSize);
        return findPage(page, pageSize, null, null, null, null, null);
    }

    @Override
    public PageResult<StudentApplication> findPage(Integer page, Integer pageSize, String applicationType, String status, String studentName, String studentUserId) {
        return findPage(page, pageSize, applicationType, status, studentName, studentUserId, null);
    }

    @Override
    public PageResult<StudentApplication> findPage(Integer page, Integer pageSize, String applicationType, String status, String studentName, String studentUserId, java.util.List<Long> classIds) {
        log.debug("分页查询学生申请，页码: {}, 每页大小: {}, 类型: {}, 状态: {}, 姓名: {}, 学号: {}, 班级数: {}",
                page, pageSize, applicationType, status, studentName, studentUserId,
                classIds == null ? 0 : classIds.size());

        PageHelper.startPage(page, pageSize);
        List<StudentApplication> applications = studentApplicationMapper.list(applicationType, status, studentName, studentUserId, classIds, null);

        PageInfo<StudentApplication> pageInfo = new PageInfo<>(applications);
        return PageResult.build(pageInfo.getTotal(), pageInfo.getList(),
                pageInfo.getPages(), pageInfo.getPageNum(), pageInfo.getPageSize());
    }

    @Override
    public PageResult<StudentApplication> findPageWithinStudentIds(Integer page, Integer pageSize,
            String applicationType, String status, String studentName, String studentUserId,
            java.util.List<Long> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) {
            return PageResult.build(0L, java.util.List.of(), 0, page, pageSize);
        }
        PageHelper.startPage(page, pageSize);
        List<StudentApplication> applications = studentApplicationMapper.list(
                applicationType, status, studentName, studentUserId, null, studentIds);
        PageInfo<StudentApplication> pageInfo = new PageInfo<>(applications);
        markCooperativeCompanyFlag(pageInfo.getList());
        return PageResult.build(pageInfo.getTotal(), pageInfo.getList(),
                pageInfo.getPages(), pageInfo.getPageNum(), pageInfo.getPageSize());
    }

    @Override
    public int countByTypeAndStatus(String applicationType, String status) {
        return countByTypeAndStatus(applicationType, status, null);
    }

    @Override
    public int countByTypeAndStatus(String applicationType, String status, java.util.List<Long> classIds) {
        log.debug("根据申请类型和状态查询申请数量，类型: {}, 状态: {}, 班级数: {}",
                applicationType, status, classIds == null ? 0 : classIds.size());
        return studentApplicationMapper.countByTypeAndStatus(applicationType, status, classIds);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int approve(Long id, Long reviewerId) {
        log.debug("批准学生申请，ID: {}, 审核人ID: {}", id, reviewerId);

        if (id == null || id <= 0) {
            throw new BusinessException("申请ID无效");
        }

        // 与提交、撤回和企业确认保持一致：先锁学生，再锁申请。
        studentApplicationMapper.lockStudentForApplication(id);
        StudentApplication application = studentApplicationMapper.findByIdForUpdate(id);
        if (application == null) {
            throw new BusinessException("申请不存在");
        }

        if (!"pending".equals(application.getStatus())) {
            throw new BusinessException("该申请已审核，无法重复操作");
        }
        if (application.isCompanyReview()) throw new BusinessException("该申请应由对应企业确认");
        if ("unitChange".equals(application.getApplicationType())) {
            throw new BusinessException("该旧申请流程已停用；历史记录仅供查看或驳回归档");
        }

        // 校验审核时限
        validateApprovalTimeLimit(application);

        application.setStatus("approved");
        application.setReviewerId(reviewerId);
        application.setReviewTime(new Date());

        int result = studentApplicationMapper.update(application);
        if (result == 0) {
            throw new BusinessException("该申请已被其他审核人处理，请刷新页面");
        }

        // 根据申请类型处理学生实习状态
        if ("unitChange".equals(application.getApplicationType())) {
            handleUnitChangeApproval(application);
        } else if ("delay".equals(application.getApplicationType())) {
            handleDelayApproval(application);
        } else if ("selfPractice".equals(application.getApplicationType())) {
            handleSelfPracticeApproval(application);
        }

        append(progressRecordService,
                application.getStudentId(),
                approvedEventType(application.getApplicationType()),
                applicationTitle(application.getApplicationType()) + "已通过",
                applicationDescription(application),
                "success",
                application.getId(),
                application.getReviewTime());
        if (sendsApplicationReviewEmail(application)) {
            sendApplicationReviewEmail(application, true, null);
        }

        log.info("学生申请批准成功，ID: {}", id);
        return result;
    }

    /**
     * 处理单位变更申请批准后的学生状态变更
     */
    private void handleUnitChangeApproval(StudentApplication application) {
        log.debug("处理单位变更申请批准，学生ID: {}", application.getStudentId());

        StudentInternshipStatus currentStatus = internshipStatusMapper
                .findByStudentId(application.getStudentId());
        if (currentStatus == null || currentStatus.getId() == null
                || (!Integer.valueOf(2).equals(currentStatus.getStatus())
                && !Integer.valueOf(3).equals(currentStatus.getStatus()))
                || StringUtils.isBlank(currentStatus.getCompanyName())) {
            throw new BusinessException("当前没有可变更的已确认实习");
        }
        InternshipStageAssignment originalAssignment = findAssignmentForStatus(currentStatus);
        if (originalAssignment == null) {
            throw new BusinessException("当前实习经历记录不存在，无法完成单位变更");
        }
        archiveInterruptedInternship(application, currentStatus);

        // 审核通过后才中断原实习；提交和驳回期间原实习保持不变。
        int interrupted = internshipStatusMapper.updateStatusToInterrupted(currentStatus.getId());
        if (interrupted <= 0) {
            throw new BusinessException("原实习状态已变化，请刷新后重试");
        }

        Date interruptedAt = application.getReviewTime() == null ? new Date() : application.getReviewTime();
        originalAssignment.setStatus("INTERRUPTED");
        originalAssignment.setCurrentFlag(0);
        originalAssignment.setInternshipEndTime(interruptedAt);
        stageAssignmentService.update(originalAssignment);

        append(progressRecordService,
                application.getStudentId(),
                "internship_interrupted",
                "原实习已中断",
                currentStatus.getCompanyName() + " · " + currentStatus.getPositionName(),
                "cancelled",
                originalAssignment.getId(),
                interruptedAt);

        log.info("单位变更申请批准处理完成，学生ID: {}, 状态已更新为已中断", application.getStudentId());
    }

    /**
     * 处理考研延迟申请批准后的学生状态变更
     */
    private void handleDelayApproval(StudentApplication application) {
        log.debug("处理考研延迟申请批准，学生ID: {}", application.getStudentId());

        StudentInternshipStatus status = internshipStatusMapper
                .findByStudentId(application.getStudentId());
        if (status != null) {
            if (internshipStatusMapper.updateStatusToDelayed(application.getStudentId()) <= 0) {
                throw new BusinessException("延期实习状态更新失败");
            }
        } else {
            StudentInternshipStatus newStatus = new StudentInternshipStatus();
            newStatus.setStudentId(application.getStudentId());
            newStatus.setStatus(6); // DELAYED
            newStatus.setIsDelayed(true);
            internshipStatusMapper.insert(newStatus);
        }
        // 状态变更后清除 Redis 缓存,学生端立即可见新状态
        statusCache.evictCache(application.getStudentId());

        log.info("考研延迟申请批准处理完成，学生ID: {}, 状态已更新为延期", application.getStudentId());
    }

    /**
     * 自主实习批准后落实单位，并按统一开始日期确定待实习或进行中状态。
     * 学生自填的实习单位/岗位为纯文本，不收录进企业表/岗位表，companyId/positionId 留空
     */
    private void handleSelfPracticeApproval(StudentApplication application) {
        log.debug("处理自主实习申请批准，学生ID: {}", application.getStudentId());

        StudentInternshipStatus status = internshipStatusMapper
                .findByStudentId(application.getStudentId());
        Date reviewTime = application.getReviewTime() == null ? new Date() : application.getReviewTime();

        // 优先使用教师统一实习日历的起止时间，与平台岗位流程保持一致
        Date calendarStart = null;
        Date calendarEnd = null;
        try {
            InternshipCalendar cal = internshipCalendarService.getCalendar();
            if (cal != null && cal.getStartDate() != null && cal.getEndDate() != null) {
                ZoneId zone = ZoneId.of("Asia/Shanghai");
                calendarStart = Date.from(LocalDate.parse(cal.getStartDate()).atStartOfDay(zone).toInstant());
                calendarEnd = Date.from(LocalDate.parse(cal.getEndDate()).atTime(LocalTime.of(23, 59, 59)).atZone(zone).toInstant());
            }
        } catch (Exception e) {
            log.warn("自主实习读取全局实习日历失败，回退到审批时间：{}", e.getMessage());
        }
        Date effectiveStart = calendarStart != null ? calendarStart : reviewTime;
        int placementStatus = effectiveStart.after(reviewTime) ? 2 : 3;
        Date effectiveEnd = calendarEnd; // 日历缺失时保持 null，与原行为一致
        LocalDateTime startTime = LocalDateTime.ofInstant(effectiveStart.toInstant(), ZoneId.of("Asia/Shanghai"));
        LocalDateTime endTime = effectiveEnd == null ? null
                : LocalDateTime.ofInstant(effectiveEnd.toInstant(), ZoneId.of("Asia/Shanghai"));

        // 1. 先归档原实习经历（必须在覆盖状态行之前，旧单位信息即将被覆盖）
        if (status != null) {
            archiveCurrentInternship(application, status, reviewTime);
        }

        if (status != null) {
            // 已有状态行：更新为按统一日历判定的正式实习状态。
            status.setStatus(placementStatus);
            status.setCompanyName(application.getCompany());
            status.setPositionName(application.getPositionName());
            status.setCompanyId(null);
            status.setPositionId(null);
            status.setInternshipStartTime(effectiveStart);
            status.setInternshipEndTime(effectiveEnd);
            status.setCompanyConfirmStatus(1);
            status.setRecallStatus(0);
            status.setIsDelayed(false);
            status.setIsInterrupted(false);
            status.setRemark(application.getReason());
            status.setUpdateTime(new Date());
            if (internshipStatusMapper.updateCurrentPlacement(status) <= 0) {
                throw new BusinessException("实习状态更新失败");
            }
        } else {
            StudentInternshipStatus newStatus = new StudentInternshipStatus();
            newStatus.setStudentId(application.getStudentId());
            newStatus.setStatus(placementStatus);
            newStatus.setCompanyName(application.getCompany());
            newStatus.setPositionName(application.getPositionName());
            newStatus.setInternshipStartTime(effectiveStart);
            newStatus.setInternshipEndTime(effectiveEnd);
            newStatus.setCompanyConfirmStatus(1);
            newStatus.setIsDelayed(false);
            newStatus.setIsInterrupted(false);
            newStatus.setCreateTime(reviewTime);
            newStatus.setUpdateTime(reviewTime);
            internshipStatusMapper.insert(newStatus);
        }
        // 状态变更后清除 Redis 缓存,学生端立即可见新状态
        statusCache.evictCache(application.getStudentId());

        // 2. 生成自主实习的确认记录和实习经历，确认记录模块才能展示自主实习
        createSelfPracticeConfirmationRecord(application, startTime, endTime, effectiveStart, effectiveEnd, placementStatus);

        // 3. 事件驱动：日历结束时间已到则立即结束实习，不等定时任务
        if (effectiveEnd != null && !effectiveEnd.after(new Date())) {
            StudentInternshipStatus current = internshipStatusMapper.findByStudentId(application.getStudentId());
            if (current != null && Integer.valueOf(3).equals(current.getStatus())) {
                lifecycleService.finishActiveInternship(current.getId(), effectiveEnd);
            }
        }

        log.info("自主实习申请批准处理完成，学生ID: {}，实习状态: {}", application.getStudentId(), placementStatus);
    }

    /**
     * 归档原实习经历：旧经历标记为已中断并移出当前，原确认记录保留在历史列表中
     */
    private void archiveCurrentInternship(StudentApplication application,
                                          StudentInternshipStatus status,
                                          Date reviewTime) {
        InternshipStageAssignment originalAssignment = findAssignmentForStatus(status);
        if (originalAssignment == null) {
            if (status.getPositionId() != null && (Integer.valueOf(2).equals(status.getStatus()) || Integer.valueOf(3).equals(status.getStatus()))) {
                releaseCounselorPreviousPosition(status.getPositionId(), null);
            }
            return; // 无当前实习经历（如首次实习），无需归档
        }
        originalAssignment.setStatus("INTERRUPTED");
        originalAssignment.setCurrentFlag(0);
        originalAssignment.setInternshipEndTime(reviewTime);
        stageAssignmentService.update(originalAssignment);
        if (originalAssignment.getPositionId() != null) {
            releaseCounselorPreviousPosition(originalAssignment.getPositionId(), originalAssignment.getId());
        }

        append(progressRecordService,
                application.getStudentId(),
                "internship_interrupted",
                "原实习已中断",
                status.getCompanyName() + " · " + status.getPositionName(),
                "cancelled",
                originalAssignment.getId(),
                reviewTime);
    }

    private void releaseCounselorPreviousPosition(Long positionId, Long assignmentId) {
        var position = positionMapper.findByIdForUpdate(positionId);
        if (position == null) throw new BusinessException("原实习岗位不存在，无法切换实习");
        if (assignmentId != null && stageAssignmentMapper.markQuotaReleased(assignmentId, "POSITION_SWITCH") <= 0) return;
        position.setRemainingQuota((position.getRemainingQuota() == null ? 0 : position.getRemainingQuota()) + 1);
        position.setRecruitedCount(Math.max(0, (position.getRecruitedCount() == null ? 0 : position.getRecruitedCount()) - 1));
        if (positionMapper.update(position) <= 0) throw new BusinessException("原实习岗位名额释放失败");
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                @Override public void afterCommit() { webSocketHandler.sendPositionUpdateToAll(position); }
            });
        }
    }

    /**
     * 创建自主实习已落实的确认记录和按日历判定状态的当前实习经历。
     * 仅用于辅导员办理的申请；companyId/positionId 留空，单位/岗位以纯文本记录。
     */
    private void createSelfPracticeConfirmationRecord(StudentApplication application,
                                                      LocalDateTime startTime,
                                                      LocalDateTime endTime,
                                                      Date effectiveStart,
                                                      Date effectiveEnd,
                                                      int placementStatus) {
        InternshipConfirmationRecord record = new InternshipConfirmationRecord();
        record.setStudentId(application.getStudentId());
        record.setStudentName(application.getStudentName());
        record.setStudentUserId(application.getStudentUserId());
        record.setGrade(application.getGrade());
        record.setClassName(application.getClassName());
        record.setCompanyName(application.getCompany());
        record.setPositionName(application.getPositionName());
        record.setInternshipStartTime(startTime);
        record.setInternshipEndTime(endTime);
        record.setRemark(application.getReason());
        record.setStatus(1); // 自主实习直接为已确认
        record.setRecallStatus(0);
        record.setCreateTime(startTime);
        if (confirmationRecordService.insert(record) <= 0) {
            throw new BusinessException("自主实习记录创建失败");
        }

        InternshipStageAssignment assignment = new InternshipStageAssignment();
        assignment.setStudentId(application.getStudentId());
        assignment.setSourceType("SELF_PRACTICE");
        assignment.setSourceAssignmentId(application.getId());
        assignment.setApplicationId(application.getId());
        assignment.setCompanyName(application.getCompany());
        assignment.setPositionName(application.getPositionName());
        assignment.setInternshipStartTime(effectiveStart);
        assignment.setInternshipEndTime(effectiveEnd);
        assignment.setStatus(placementStatus == 2 ? "CONFIRMED" : "IN_PROGRESS");
        assignment.setCurrentFlag(1);
        InternshipStageAssignment created = stageAssignmentService.createFromApplication(assignment);
        // createFromApplication 默认 currentFlag=0，自主实习是当前实习，需置 1
        created.setCurrentFlag(1);
        created.setStatus(placementStatus == 2 ? "CONFIRMED" : "IN_PROGRESS");
        created.setConfirmationRecordId(record.getId());
        stageAssignmentService.update(created);
    }

    /**
     * 校验审核时限
     * 如果审核时间超过设置的时限，记录警告日志但仍允许审核
     */
    private void validateApprovalTimeLimit(StudentApplication application) {
        InternshipTimeSettings settings = internshipTimeSettingsService.findLatest();
        if (settings == null || settings.getApprovalTimeLimit() == null) {
            return; // 没有设置时限，不做校验
        }

        Integer limit = settings.getApprovalTimeLimit();
        if (limit <= 0) {
            return; // 无效的时限设置
        }

        Date createTime = application.getCreateTime();
        if (createTime == null) {
            return; // 没有创建时间，无法校验
        }

        // 计算工作日天数（简化计算：直接按自然日计算，实际应扣除周末）
        long daysBetween = (new Date().getTime() - createTime.getTime()) / (1000 * 60 * 60 * 24);

        if (daysBetween > limit) {
            log.warn("审核超过时限！申请ID: {}, 创建时间: {}, 已超过 {} 天（时限 {} 天）",
                    application.getId(), createTime, daysBetween, limit);
            // 注意：这里只记录警告，不阻止审核操作，因为实际业务中可能需要特事特办
        }
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int reject(Long id, Long reviewerId, String rejectReason) {
        log.debug("驳回学生申请，ID: {}, 审核人ID: {}, 驳回理由: {}", id, reviewerId, rejectReason);
        
        if (id == null || id <= 0) {
            throw new BusinessException("申请ID无效");
        }
        
        if (StringUtils.isBlank(rejectReason)) {
            throw new BusinessException("驳回理由不能为空");
        }
        
        studentApplicationMapper.lockStudentForApplication(id);
        StudentApplication application = studentApplicationMapper.findByIdForUpdate(id);
        if (application == null) {
            throw new BusinessException("申请不存在");
        }

        if (!"pending".equals(application.getStatus())) {
            throw new BusinessException("该申请已审核，无法重复操作");
        }

        if (application.isCompanyReview()) throw new BusinessException("该申请应由对应企业确认");

        // 校验审核时限
        validateApprovalTimeLimit(application);

        application.setStatus("rejected");
        application.setReviewerId(reviewerId);
        application.setReviewTime(new Date());
        application.setRejectReason(rejectReason);

        int result = studentApplicationMapper.update(application);
        if (result == 0) {
            throw new BusinessException("该申请已被其他审核人处理，请刷新页面");
        }
        append(progressRecordService,
                application.getStudentId(),
                rejectedEventType(application.getApplicationType()),
                applicationTitle(application.getApplicationType()) + "未通过",
                applicationDescription(application) + "；原因：" + rejectReason,
                "failed",
                application.getId(),
                application.getReviewTime());
        if (sendsApplicationReviewEmail(application)) {
            sendApplicationReviewEmail(application, false, rejectReason);
        }
        log.info("学生申请驳回成功，ID: {}", id);
        return result;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int withdrawDelay(Long id, Long studentId) {
        return withdrawApplication(id, studentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int withdrawApplication(Long id, Long studentId) {
        if (id == null || id <= 0) {
            throw new BusinessException("申请ID无效");
        }
        if (studentId == null) {
            throw new BusinessException("学生ID不能为空");
        }

        studentApplicationMapper.lockStudent(studentId);
        StudentApplication application = studentApplicationMapper.findByIdForUpdate(id);
        if (application == null || !studentId.equals(application.getStudentId())) {
            throw new BusinessException("申请不存在或无权撤回");
        }
        String type = application.getApplicationType();
        if (!"selfPractice".equals(type) && !"delay".equals(type)) {
            throw new BusinessException("该申请不支持撤回");
        }
        if (!"pending".equals(application.getStatus()) || application.getReviewerId() != null || application.getReviewTime() != null) {
            throw new BusinessException("教师已处理该申请，无法撤回");
        }

        if (application.isCompanyReview()) {
            confirmationWorkflowService.withdraw(studentId, application.getConfirmationRecordId(), "学生撤回自主实习申请");
            notifyCompanyAfterCommit(application.getCompanyId());
            return 1;
        }
        application.setStatus("withdrawn");
        int result = studentApplicationMapper.update(application);
        if (result == 0) {
            throw new BusinessException("申请状态已变化，请刷新后重试");
        }
        append(progressRecordService,
                application.getStudentId(),
                "selfPractice".equals(type) ? "self_practice_withdrawn" : "delay_withdrawn",
                applicationTitle(type) + "已撤回",
                "学生已撤回申请，可修改后重新提交。",
                "cancelled",
                application.getId(),
                new Date());
        log.info("学生撤回申请成功，ID: {}, studentId: {}, type: {}", id, studentId, type);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int resubmit(Long id, Long studentId, StudentApplication application) {
        if (id == null || id <= 0) {
            throw new BusinessException("申请ID无效");
        }
        if (studentId == null) {
            throw new BusinessException("学生ID不能为空");
        }
        if (application == null) {
            throw new BusinessException("申请信息不能为空");
        }

        studentApplicationMapper.lockStudent(studentId);
        StudentApplication existing = studentApplicationMapper.findByIdForUpdate(id);
        if (existing == null || !studentId.equals(existing.getStudentId())) {
            throw new BusinessException("申请不存在或无权重新提交");
        }
        String type = existing.getApplicationType();
        if (!"selfPractice".equals(type) && !"delay".equals(type)) {
            throw new BusinessException("该申请不支持重新提交");
        }
        if (!Set.of("rejected", "withdrawn").contains(existing.getStatus())) {
            throw new BusinessException("只有被驳回或已撤回的申请才能重新提交");
        }
        if (studentApplicationMapper.findPendingByStudentAndTypeForUpdate(studentId, type) != null) {
            throw new BusinessException("您已有待审核的" + applicationTitle(type));
        }

        application.setId(existing.getId());
        application.setStudentId(existing.getStudentId());
        application.setStudentName(existing.getStudentName());
        application.setStudentUserId(existing.getStudentUserId());
        application.setGrade(existing.getGrade());
        application.setClassName(existing.getClassName());
        application.setApplicationType(type);
        application.setStatus("pending");
        application.setRejectReason(null);
        application.setReviewerId(null);
        application.setReviewTime(null);
        application.setApplyTime(new Date());
        validateApplicationInfo(application);
        validateSelfPracticeInfo(application);
        prepareReviewTarget(application);

        Map<String, String> materials = new java.util.LinkedHashMap<>();
        if (existing.getMaterials() != null && normalizeCompanyNameKey(existing.getCompany()).equals(normalizeCompanyNameKey(application.getCompany()))) materials.putAll(existing.getMaterials());
        if (application.getMaterials() != null) {
            application.getMaterials().forEach((key, value) -> {
                if (StringUtils.isNotBlank(value)) materials.put(key, value);
            });
        }
        application.setMaterials(materials);

        if ("selfPractice".equals(type)) {
            com.internship.service.InternshipAgreementService.requireFileIds(application.getAgreementFileIds());
        }
        int result = studentApplicationMapper.resetForResubmit(application);
        if (result == 0) {
            throw new BusinessException("申请状态已变化，请刷新后重试");
        }
        if ("selfPractice".equals(type)) {
            var version = agreementService.bindSubmission(studentId, "SELF_PRACTICE", id, application.getAgreementFileIds());
            submitCompanyConfirmation(application, version == null ? null : version.getId());
        }
        appendSubmittedEvent(application);
        log.info("学生重新提交申请成功，ID: {}, studentId: {}, type: {}", id, studentId, type);
        return result;
    }

    private boolean sendsApplicationReviewEmail(StudentApplication application) {
        return "selfPractice".equals(application.getApplicationType())
                || "delay".equals(application.getApplicationType());
    }

    private void sendApplicationReviewEmail(StudentApplication application, boolean approved, String rejectReason) {
        String title = applicationTitle(application.getApplicationType());
        if (!SystemSettingsConfig.isEmailNotifyScopeEnabled(EmailNotifyScope.STUDENT_APPLICATION)) {
            log.info("系统邮件提醒已关闭（学生申请审核），跳过{}审核结果邮件: applicationId={}", title, application.getId());
            return;
        }
        try {
            StudentUser student = studentUserService.findById(application.getStudentId());
            String recipient = student == null ? null : student.getEmail();
            if (StringUtils.isBlank(recipient)) {
                log.warn("{}审核结果邮件未发送：学生邮箱为空，applicationId={}, studentId={}",
                        title, application.getId(), application.getStudentId());
                return;
            }
            boolean selfPractice = "selfPractice".equals(application.getApplicationType());
            String resultText = approved ? "已通过" : selfPractice ? "已驳回" : "未通过";
            String subject = title + resultText + "通知";
            StringBuilder body = new StringBuilder();
            body.append(application.getStudentName()).append("同学，你好：").append(System.lineSeparator()).append(System.lineSeparator());
            body.append("你的").append(title).append(resultText).append("。").append(System.lineSeparator());
            if (!approved && StringUtils.isNotBlank(rejectReason)) {
                body.append("驳回理由：").append(rejectReason).append(System.lineSeparator());
            }
            body.append(System.lineSeparator()).append("审核时间：").append(application.getReviewTime());
            boolean sent = emailService.sendEmail(recipient.trim(), subject, body.toString());
            if (!sent) {
                log.warn("{}审核结果邮件未发送成功，applicationId={}, recipient={}",
                        title, application.getId(), recipient);
            }
        } catch (Exception e) {
            log.error("{}审核结果邮件发送异常，applicationId={}", title, application.getId(), e);
        }
    }
    /**
     * 验证申请信息
     */
    private void validateApplicationInfo(StudentApplication application) {
        if (application == null) {
            throw new BusinessException("申请信息不能为空");
        }
        
        if (application.getStudentId() == null) {
            throw new BusinessException("学生ID不能为空");
        }
        
        if (StringUtils.isBlank(application.getStudentName())) {
            throw new BusinessException("学生姓名不能为空");
        }
        
        if (StringUtils.isBlank(application.getStudentUserId())) {
            throw new BusinessException("学号不能为空");
        }
        
        if (StringUtils.isBlank(application.getApplicationType())) {
            throw new BusinessException("申请类型不能为空");
        }
        
        if (StringUtils.isBlank(application.getReason())) {
            throw new BusinessException("申请理由不能为空");
        }
        
        if (!"selfPractice".equals(application.getApplicationType()) &&
            !"unitChange".equals(application.getApplicationType()) &&
            !"delay".equals(application.getApplicationType())) {
            throw new BusinessException("申请类型无效");
        }
    }

    /**
     * 标记自主实习申请的实习单位是否为系统内已审核的有效校企合作企业。
     * 查询时动态计算，不落库；教师端审核列表与详情据此展示“校企合作企业”标识。
     */
    private void markCooperativeCompanyFlag(java.util.List<StudentApplication> applications) {
        if (applications == null || applications.isEmpty()) {
            return;
        }
        boolean needMark = applications.stream().anyMatch(item -> item != null
                && "selfPractice".equals(item.getApplicationType())
                && StringUtils.isNotBlank(item.getCompany()));
        if (!needMark) {
            return;
        }
        try {
            java.util.Map<String, CompanyUser> approvedByName = new java.util.HashMap<>();
            List<CompanyUser> approvedCompanies = companyUserMapper.findAllApprovedCompanies();
            if (approvedCompanies != null) {
                for (CompanyUser company : approvedCompanies) {
                    if (company != null && StringUtils.isNotBlank(company.getCompanyName())) {
                        approvedByName.putIfAbsent(normalizeCompanyNameKey(company.getCompanyName()), company);
                    }
                }
            }
            for (StudentApplication item : applications) {
                if (item == null || !"selfPractice".equals(item.getApplicationType())
                        || StringUtils.isBlank(item.getCompany())) {
                    continue;
                }
                CompanyUser matched = approvedByName.get(normalizeCompanyNameKey(item.getCompany()));
                item.setCooperativeCompany(matched != null);
                item.setCooperativeCompanyId(matched == null ? null : matched.getId());
            }
        } catch (Exception e) {
            log.warn("标记自主实习校企合作企业失败: {}", e.getMessage());
        }
    }

    private String normalizeCompanyNameKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private void validateSelfPracticeInfo(StudentApplication application) {
        if (!"selfPractice".equals(application.getApplicationType())) {
            return;
        }
        if (StringUtils.isBlank(application.getCompany())) {
            throw new BusinessException("实习单位不能为空");
        }
        if (StringUtils.isBlank(application.getPositionName())) {
            throw new BusinessException("实习岗位不能为空");
        }
        if (StringUtils.isBlank(application.getCompanyCity())) {
            throw new BusinessException("单位所在城市不能为空");
        }
        if (StringUtils.isBlank(application.getHrManagerName())) {
            throw new BusinessException("人事经理姓名不能为空");
        }
        if (StringUtils.isBlank(application.getHrManagerPhone())) {
            throw new BusinessException("人事经理联系电话不能为空");
        }
        // 系统内已审核的校企合作企业允许作为自主实习单位提交；
        // 但若名称与系统企业相近又不完全一致，视为疑似同一企业名称不规范，
        // 要求从下拉候选中选择系统登记的标准名称，防止同一企业以不同名称进入审核流程。
        CompanyUser cooperativeCompany = companyUserMapper.findApprovedByCompanyName(application.getCompany());
        if (cooperativeCompany == null) {
            String keyword = application.getCompany().trim();
            if (keyword.length() >= 2) {
                List<CompanyUser> similarCompanies = companyUserMapper.findApprovedCompanyMatches(keyword);
                if (similarCompanies != null && !similarCompanies.isEmpty()) {
                    String names = similarCompanies.stream()
                            .map(CompanyUser::getCompanyName)
                            .filter(StringUtils::isNotBlank)
                            .distinct()
                            .limit(3)
                            .collect(java.util.stream.Collectors.joining("、"));
                    throw new BusinessException("系统企业库存在名称相近的校企合作单位（" + names
                            + "），请从下拉候选中选择系统登记的标准企业名称；若该企业确实不在系统中，请核对名称后重新提交");
                }
            }
        }
        if (application.getCompanyCity().length() > 100) {
            throw new BusinessException("单位所在城市不能超过100个字符");
        }
        if (application.getHrManagerName().length() > 50) {
            throw new BusinessException("人事经理姓名不能超过50个字符");
        }
        if (application.getHrManagerPhone().length() > 30) {
            throw new BusinessException("人事经理联系电话不能超过30个字符");
        }
    }

    @Override
    public StudentApplication findLatestUnitChangeByStudentId(Long studentId) {
        log.debug("查询学生最新单位变更申请，学生ID: {}", studentId);
        List<StudentApplication> apps = studentApplicationMapper.findByStudentId(studentId);
        if (apps == null || apps.isEmpty()) {
            return null;
        }
        // 返回最新的unitChange申请
        return apps.stream()
            .filter(a -> "unitChange".equals(a.getApplicationType()))
            .max(Comparator.comparing(StudentApplication::getCreateTime))
            .orElse(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int resubmitUnitChange(Long id, Long studentId, String newCompany, String reason, Map<String, String> materials) {
        throw new BusinessException("单位变更申请流程已停用，请申请新岗位并提交新的实习确认表");
    }

    private void appendSubmittedEvent(StudentApplication application) {
        String eventType;
        if ("selfPractice".equals(application.getApplicationType())) {
            eventType = "self_practice_submitted";
        } else if ("delay".equals(application.getApplicationType())) {
            eventType = "delay_submitted";
        } else {
            eventType = "unit_change_submitted";
        }
        append(progressRecordService,
                application.getStudentId(),
                eventType,
                "已提交" + applicationTitle(application.getApplicationType()),
                applicationDescription(application),
                "pending",
                application.getId(),
                application.getApplyTime());
    }

    private String approvedEventType(String type) {
        if ("selfPractice".equals(type)) return "self_practice_approved";
        if ("delay".equals(type)) return "delay_approved";
        return "unit_change_approved";
    }

    private String rejectedEventType(String type) {
        if ("selfPractice".equals(type)) return "self_practice_rejected";
        if ("delay".equals(type)) return "delay_rejected";
        return "unit_change_rejected";
    }

    private String applicationTitle(String type) {
        if ("selfPractice".equals(type)) return "自主实习申请";
        if ("delay".equals(type)) return "考研延迟申请";
        return "单位变更申请";
    }

    private String applicationDescription(StudentApplication application) {
        if ("selfPractice".equals(application.getApplicationType())) {
            return "实习单位：" + application.getCompany();
        }
        if ("delay".equals(application.getApplicationType())) {
            return "考研延迟原因：" + application.getReason();
        }
        return application.getOldCompany() + " → " + application.getNewCompany();
    }

    private InternshipStageAssignment findAssignmentForStatus(StudentInternshipStatus status) {
        if (status == null || status.getStudentId() == null) return null;
        List<InternshipStageAssignment> assignments = stageAssignmentService.findByStudentId(status.getStudentId());
        if (assignments == null) return null;
        return assignments.stream()
                .filter(item -> Integer.valueOf(1).equals(item.getCurrentFlag()))
                .filter(item -> java.util.Objects.equals(status.getCompanyId(), item.getCompanyId()))
                .filter(item -> java.util.Objects.equals(status.getPositionId(), item.getPositionId()))
                .max(Comparator.comparing(item -> item.getStageNo() == null ? 0 : item.getStageNo()))
                .orElse(null);
    }

    private void archiveInterruptedInternship(StudentApplication application,
                                              StudentInternshipStatus status) {
        InternshipConfirmationRecord record = new InternshipConfirmationRecord();
        record.setStudentId(application.getStudentId());
        record.setStudentName(application.getStudentName());
        record.setStudentUserId(application.getStudentUserId());
        record.setGrade(application.getGrade());
        record.setClassName(application.getClassName());
        record.setCompanyId(status.getCompanyId());
        record.setCompanyName(status.getCompanyName());
        record.setCompanyAddress(status.getCompanyAddress());
        record.setCompanyPhone(status.getCompanyPhone());
        record.setPositionId(status.getPositionId());
        record.setPositionName(status.getPositionName());
        if (status.getInternshipStartTime() != null) {
            record.setInternshipStartTime(LocalDateTime.ofInstant(
                    status.getInternshipStartTime().toInstant(), ZoneId.of("Asia/Shanghai")));
        }
        if (status.getInternshipEndTime() != null) {
            record.setInternshipEndTime(LocalDateTime.ofInstant(
                    status.getInternshipEndTime().toInstant(), ZoneId.of("Asia/Shanghai")));
        }
        record.setInternshipDuration(status.getInternshipDuration());
        record.setRemark("单位变更申请通过，原实习已中断");
        record.setStatus(1);
        Date archivedAt = application.getReviewTime() == null ? new Date() : application.getReviewTime();
        record.setCreateTime(LocalDateTime.ofInstant(archivedAt.toInstant(), ZoneId.of("Asia/Shanghai")));
        if (confirmationRecordService.insert(record) <= 0) {
            throw new BusinessException("原实习记录归档失败");
        }
    }
}
