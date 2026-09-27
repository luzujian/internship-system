package com.internship.controller;

import com.internship.anno.Log;
import com.internship.entity.Result;
import com.internship.entity.InternshipApplicationEntity;
import com.internship.entity.Position;
import com.internship.service.InternshipApplicationService;
import com.internship.service.InterviewInvitationService;
import com.internship.service.JobApplicationResultNotification;
import com.internship.service.JobApplicationResultNotifier;
import com.internship.service.InternshipProgressRecordService;
import com.internship.service.StudentInternshipStatusService;
import com.internship.service.StudentJobApplicationService;
import com.internship.websocket.AnnouncementWebSocketHandler;
import com.internship.entity.InterviewInvitation;
import com.internship.mapper.StudentUserMapper;
import com.internship.mapper.PositionMapper;
import com.internship.mapper.CompanyUserMapper;
import com.internship.mapper.MajorMapper;
import com.internship.utils.CurrentHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.util.Date;
import java.util.List;

import static com.internship.service.InternshipProgressEvents.append;

/**
 * 实习申请控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/applications")
public class InternshipApplicationController {

    @Autowired
    private InternshipApplicationService internshipApplicationService;

    @Autowired
    private StudentInternshipStatusService studentInternshipStatusService;

    @Autowired
    private StudentJobApplicationService studentJobApplicationService;

    @Autowired
    private StudentUserMapper studentUserMapper;

    @Autowired
    private PositionMapper positionMapper;

    @Autowired
    private CompanyUserMapper companyUserMapper;

    @Autowired
    private MajorMapper majorMapper;

    @Autowired
    private InterviewInvitationService interviewService;

    @Autowired
    private InternshipProgressRecordService progressRecordService;

    @Autowired
    private AnnouncementWebSocketHandler webSocketHandler;

    @Autowired
    private JobApplicationResultNotifier jobApplicationResultNotifier;

    /**
     * 根据公司ID获取申请列表
     */
    @GetMapping("/company/{companyId}")
    public Result getApplications(@PathVariable Long companyId) {
        log.info("根据公司ID获取申请列表，公司ID: {}", companyId);
        try {
            // 安全校验：非管理员只能查看自己公司的申请
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));
            if (!isAdmin) {
                Long currentCompanyId = CurrentHolder.getUserId();
                if (currentCompanyId == null || !currentCompanyId.equals(companyId)) {
                    log.warn("用户尝试查看其他企业的申请: currentCompanyId={}, requestedCompanyId={}", currentCompanyId, companyId);
                    return Result.error("无权查看该企业的申请数据");
                }
            }
            List<InternshipApplicationEntity> applications = internshipApplicationService.findByCompanyId(companyId);
            return Result.success(applications);
        } catch (Exception e) {
            log.error("获取申请列表失败: {}", e.getMessage());
            return Result.error("获取申请列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取申请详情
     */
    @GetMapping("/{id}")
    public Result getApplicationById(@PathVariable Long id) {
        log.info("获取申请详情，ID: {}", id);
        try {
            InternshipApplicationEntity application = internshipApplicationService.findById(id);
            if (application == null) {
                return Result.error("申请不存在");
            }
            return Result.success(application);
        } catch (Exception e) {
            log.error("获取申请详情失败: {}", e.getMessage());
            return Result.error("获取申请详情失败: " + e.getMessage());
        }
    }

    /**
     * 添加申请
     */
    @Log(operationType = "ADD", module = "APPLICATION_MANAGEMENT", description = "添加实习申请")
    @PostMapping
    public Result addApplication(@RequestBody InternshipApplicationEntity application) {
        log.info("添加实习申请");
        try {
            // 从数据库获取学生完整信息并填充到申请记录中
            if (application.getStudentId() != null) {
                var student = studentUserMapper.findById(application.getStudentId());
                if (student != null) {
                    application.setGender(student.getGender() != null ? student.getGender().toString() : null);
                    application.setGrade(student.getGrade() != null ? student.getGrade().toString() : null);
                    // 获取专业名称
                    if (student.getMajorId() != null) {
                        var major = majorMapper.findById(student.getMajorId());
                        if (major != null) {
                            application.setMajor(major.getName());
                        }
                    }
                                        // 设置学生用户ID
                    application.setStudentUserId(student.getStudentId());
                }
            }
            int result = internshipApplicationService.insert(application);
            if (result > 0) {
                return Result.success("添加成功");
            }
            return Result.error("添加失败");
        } catch (Exception e) {
            log.error("添加申请失败: {}", e.getMessage());
            return Result.error("添加申请失败: " + e.getMessage());
        }
    }

    /**
     * 更新申请状态
     * approved 仅表示获得面试机会；interview_passed 后才进入待确认状态。
     */
    @Log(operationType = "UPDATE", module = "APPLICATION_MANAGEMENT", description = "更新申请状态")
    @PutMapping("/{id}/status")
    @Transactional(rollbackFor = Exception.class)
    public synchronized Result updateApplyStatus(@PathVariable Long id, @RequestBody StatusUpdateRequest statusRequest) {
        log.info("更新申请状态，ID: {}, 状态: {}", id, statusRequest.getStatus());
        try {
            InternshipApplicationEntity application = internshipApplicationService.findById(id);
            if (application == null) {
                return Result.error("申请不存在");
            }

            // 安全校验：非管理员只能操作自己公司的申请
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));
            if (!isAdmin) {
                String userRole = CurrentHolder.getUserRole();
                Long currentCompanyId = CurrentHolder.getUserId();
                if (!"ROLE_COMPANY".equals(userRole) || currentCompanyId == null
                    || !currentCompanyId.equals(application.getCompanyId())) {
                    log.warn("越权操作申请: role={}, userId={}, appCompanyId={}",
                        userRole, currentCompanyId, application.getCompanyId());
                    return Result.error("无权操作其他企业的申请");
                }
            }

            String newStatus = statusRequest.getStatus();
            String rejectReason = com.internship.service.StudentJobApplicationStatuses
                    .normalizeRejectReason(newStatus, statusRequest.getRejectReason());
            if (!com.internship.service.StudentJobApplicationStatuses.isActive(application.getStatus())) {
                return Result.error("该申请已处理，请勿重复操作");
            }

            boolean result = internshipApplicationService.updateStatus(id, newStatus);

            if (!result) {
                return Result.error("更新失败");
            }

            int synced = studentJobApplicationService.updateLatestStatusIfActive(
                    application.getStudentId(), application.getPositionId(), newStatus, rejectReason);
            if (synced <= 0) {
                log.warn("兼容申请记录未匹配到活动主记录: mirrorId={}, studentId={}, positionId={}",
                        id, application.getStudentId(), application.getPositionId());
            }

            if ("rejected".equals(newStatus)) {
                var company = companyUserMapper.findById(application.getCompanyId());
                append(progressRecordService,
                        application.getStudentId(),
                        "job_rejected",
                        "岗位申请未通过",
                        (company == null ? "" : company.getCompanyName() + " · ")
                                + application.getPositionName(),
                        "failed",
                        resolveJobApplicationId(application),
                        new Date());
            }

            // 同意申请只创建或保留面试邀请，不提前写入待确认状态
            if ("approved".equals(newStatus)) {
                createInterviewInvitationIfNotExists(application);
            }

            // 如果是面试结果（interview_passed 或 interview_failed），更新面试邀请状态并推送WebSocket通知
            if ("interview_passed".equals(newStatus) || "interview_failed".equals(newStatus)) {
                if (application.getStudentId() != null && application.getPositionId() != null) {
                    if ("interview_failed".equals(newStatus)) {
                        studentInternshipStatusService.rollbackPendingConfirmationForFailedApplication(
                                application.getStudentId(), application.getPositionId());
                    }
                    InterviewInvitation invitation = interviewService.findByStudentAndPosition(
                            application.getStudentId(), application.getPositionId());
                    if (invitation != null) {
                        interviewService.updateStatus(invitation.getId(), newStatus, null);
                        var company = companyUserMapper.findById(application.getCompanyId());
                        boolean passed = "interview_passed".equals(newStatus);
                        append(progressRecordService,
                                application.getStudentId(),
                                passed ? "interview_passed" : "interview_failed",
                                passed ? "面试已通过" : "面试未通过",
                                (company == null ? "" : company.getCompanyName() + " · ")
                                        + application.getPositionName(),
                                passed ? "success" : "failed",
                                invitation.getId(),
                                new Date());
                        webSocketHandler.sendInterviewStatusUpdateToUser(
                                application.getStudentId(), invitation.getId(), newStatus);
                        log.info("更新面试邀请状态并推送WebSocket，学生ID: {}, 状态: {}",
                                application.getStudentId(), newStatus);
                    }
                }
            }

            Long emailApplicationId = resolveJobApplicationId(application);
            var emailCompany = companyUserMapper.findById(application.getCompanyId());
            jobApplicationResultNotifier.afterCommit(new JobApplicationResultNotification(
                    emailApplicationId == null ? application.getId() : emailApplicationId,
                    application.getStudentId(), application.getCompanyId(),
                    emailCompany == null ? "" : emailCompany.getCompanyName(),
                    application.getPositionId(), application.getPositionName(),
                    newStatus, false, rejectReason));

            return Result.success("更新成功");
        } catch (Exception e) {
            rollbackCurrentTransaction();
            log.error("更新申请状态失败: {}", e.getMessage(), e);
            return Result.error("更新申请状态失败: " + e.getMessage());
        }
    }

    /**
     * 标记已读
     */
    @Log(operationType = "UPDATE", module = "APPLICATION_MANAGEMENT", description = "标记申请已读")
    @PutMapping("/{id}/view")
    public Result markAsViewed(@PathVariable Long id) {
        log.info("标记申请已读，ID: {}", id);
        try {
            boolean result = internshipApplicationService.markAsViewed(id);
            if (result) {
                return Result.success("标记成功");
            }
            return Result.error("标记失败");
        } catch (Exception e) {
            log.error("标记已读失败: {}", e.getMessage());
            return Result.error("标记已读失败: " + e.getMessage());
        }
    }

    /**
     * 删除申请
     */
    @Log(operationType = "DELETE", module = "APPLICATION_MANAGEMENT", description = "删除实习申请")
    @DeleteMapping("/{id}")
    public Result deleteApplication(@PathVariable Long id) {
        log.info("删除申请，ID: {}", id);
        try {
            boolean result = internshipApplicationService.deleteById(id);
            if (result) {
                return Result.success("删除成功");
            }
            return Result.error("删除失败");
        } catch (Exception e) {
            log.error("删除申请失败: {}", e.getMessage());
            return Result.error("删除申请失败: " + e.getMessage());
        }
    }

    /**
     * 根据岗位ID获取申请列表
     */
    @GetMapping("/position/{positionId}")
    public Result getApplicationsByPosition(@PathVariable Long positionId) {
        log.info("根据岗位ID获取申请列表，岗位ID: {}", positionId);
        try {
            List<InternshipApplicationEntity> applications = internshipApplicationService.findByPositionId(positionId);
            return Result.success(applications);
        } catch (Exception e) {
            log.error("获取岗位申请列表失败: {}", e.getMessage());
            return Result.error("获取岗位申请列表失败: " + e.getMessage());
        }
    }

    /**
     * 按状态获取申请
     */
    @GetMapping
    public Result getApplicationsByStatus(@RequestParam(required = false) String status) {
        log.info("按状态获取申请，状态: {}", status);
        try {
            List<InternshipApplicationEntity> applications;
            if (status != null) {
                applications = internshipApplicationService.findByStatus(status);
            } else {
                applications = internshipApplicationService.findAll();
            }
            return Result.success(applications);
        } catch (Exception e) {
            log.error("按状态获取申请失败: {}", e.getMessage());
            return Result.error("按状态获取申请失败: " + e.getMessage());
        }
    }

    private void createInterviewInvitationIfNotExists(InternshipApplicationEntity application) {
        if (application.getStudentId() == null || application.getPositionId() == null) {
            return;
        }
        InterviewInvitation existing = interviewService.findByStudentAndPosition(
                application.getStudentId(), application.getPositionId());
        if (existing != null
                && !com.internship.service.StudentJobApplicationStatuses.canRetry(existing.getStatus())) {
            return;
        }

        Position position = positionMapper.findById(application.getPositionId());
        if (position == null) {
            log.warn("岗位不存在，无法创建面试邀请，岗位ID: {}", application.getPositionId());
            return;
        }

        String companyName = "";
        if (application.getCompanyId() != null) {
            var company = companyUserMapper.findById(application.getCompanyId());
            if (company != null && company.getCompanyName() != null) {
                companyName = company.getCompanyName();
            }
        }

        InterviewInvitation invitation = new InterviewInvitation();
        invitation.setStudentId(application.getStudentId());
        invitation.setPositionId(application.getPositionId());
        invitation.setCompanyId(application.getCompanyId());
        invitation.setPositionName(position.getPositionName());
        invitation.setCompanyName(companyName);
        if (position.getInterviewTime() != null) {
            invitation.setInterviewTime(position.getInterviewTime().toInstant()
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
        }
        invitation.setInterviewLocation(position.getInterviewLocation());
        invitation.setInterviewMethod(position.getInterviewMethod());
        invitation.setRemark(position.getInterviewRemark());
        invitation.setStatus("pending_interview");
        interviewService.create(invitation);
        append(progressRecordService,
                application.getStudentId(),
                "interview_invited",
                "收到面试邀请",
                companyName + " · " + position.getPositionName(),
                "pending",
                invitation.getId(),
                new Date());
    }

    /**
     * 状态更新请求参数
     */
    private static class StatusUpdateRequest {
        private String status;
        private String rejectReason;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getRejectReason() {
            return rejectReason;
        }

        public void setRejectReason(String rejectReason) {
            this.rejectReason = rejectReason;
        }
    }

    private Long resolveJobApplicationId(InternshipApplicationEntity application) {
        List<com.internship.entity.StudentJobApplication> applications = studentJobApplicationService
                .findByStudentId(application.getStudentId());
        if (applications == null) return null;
        return applications.stream()
                .filter(item -> application.getPositionId().equals(item.getPositionId()))
                .filter(item -> application.getStageNo() == null || item.getStageNo() == null
                        || application.getStageNo().equals(item.getStageNo()))
                .map(com.internship.entity.StudentJobApplication::getId)
                .filter(java.util.Objects::nonNull)
                .max(Long::compareTo)
                .orElse(null);
    }

    private void rollbackCurrentTransaction() {
        try {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        } catch (NoTransactionException ignored) {
            // Direct controller unit tests do not run through a transactional proxy.
        }
    }
}
