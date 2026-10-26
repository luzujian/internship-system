package com.internship.controller;

import com.internship.anno.Log;
import com.internship.entity.InternshipConfirmationRecord;
import com.internship.entity.InterviewInvitation;
import com.internship.entity.CompanyUser;
import com.internship.entity.Position;
import com.internship.entity.Result;
import com.internship.entity.StudentInternshipStatus;
import com.internship.entity.StudentUser;
import com.internship.entity.User;
import com.internship.exception.BusinessException;
import com.internship.entity.InternshipStageAssignment;
import com.internship.entity.StudentJobApplication;
import com.internship.service.InternshipConfirmationRecordService;
import com.internship.service.StudentJobApplicationService;
import com.internship.service.StudentInternshipStatusService;
import com.internship.service.StudentUserService;
import com.internship.service.UserService;
import com.internship.service.InterviewInvitationService;
import com.internship.service.CompanyUserService;
import com.internship.service.PositionService;
import com.internship.service.InternshipStageAssignmentService;
import com.internship.service.InternshipConfirmationWorkflowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static com.internship.service.InternshipProgressEvents.append;

@Slf4j
@RestController
@RequestMapping("/api/student/internship-confirmation")
@PreAuthorize("hasRole('STUDENT')")
public class StudentInternshipConfirmationController {

    @Autowired
    private UserService userService;

    @Autowired
    private StudentUserService studentUserService;

    @Autowired
    private StudentInternshipStatusService internshipStatusService;

    @Autowired
    private InternshipConfirmationRecordService confirmationRecordService;

    @Autowired
    private StudentJobApplicationService studentJobApplicationService;

    @Autowired
    private InterviewInvitationService interviewInvitationService;

    @Autowired
    private CompanyUserService companyUserService;

    @Autowired
    private PositionService positionService;

    @Autowired
    private InternshipStageAssignmentService internshipStageAssignmentService;

    @Autowired
    private InternshipConfirmationWorkflowService confirmationWorkflowService;

    @Autowired
    private com.internship.websocket.AnnouncementWebSocketHandler webSocketHandler;

    /**
     * 将Object安全转换为String，处理Integer、Number等类型
     */
    private String objectToString(Object obj) {
        if (obj == null) return null;
        if (obj instanceof String) return (String) obj;
        if (obj instanceof Number) return String.valueOf(obj);
        return obj.toString();
    }



    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof org.springframework.security.core.userdetails.User)) {
            return null;
        }
        String username = ((org.springframework.security.core.userdetails.User) auth.getPrincipal()).getUsername();
        return userService.findByUsername(username);
    }

    @GetMapping("/offers")
    public Result getConfirmableOffers() {
        try {
            User user = getCurrentUser();
            if (user == null) return Result.error("未登录");
            Long studentId = user.getId();

            boolean hasSubmittedPending = confirmationRecordService.findByStudentId(studentId).stream()
                    .anyMatch(this::isSubmittedPending);
            if (hasSubmittedPending) return Result.success(List.of());

            List<InternshipStageAssignment> assignments = internshipStageAssignmentService
                    .findByStudentId(studentId);
            if (assignments == null) assignments = List.of();
            final List<InternshipStageAssignment> studentAssignments = assignments;
            Map<String, InterviewInvitation> latestByPlacement = new LinkedHashMap<>();
            List<InterviewInvitation> invitations = interviewInvitationService
                    .findByStudentIdAndStatus(studentId, "interview_passed");
            if (invitations == null) invitations = List.of();
            invitations.stream()
                    .filter(item -> item.getId() != null && item.getCompanyId() != null
                            && item.getPositionId() != null)
                    .sorted(Comparator.comparing(InterviewInvitation::getId).reversed())
                    .forEach(item -> latestByPlacement.putIfAbsent(
                            item.getCompanyId() + ":" + item.getPositionId(), item));

            StudentInternshipStatus currentStatus = internshipStatusService.findByStudentId(studentId);
            List<StudentJobApplication> applications = studentJobApplicationService.findByStudentId(studentId);
            if (applications == null) applications = List.of();

            List<Map<String, Object>> offers = new ArrayList<>();
            for (InterviewInvitation invitation : latestByPlacement.values()) {
                boolean currentSamePlacement = currentStatus != null
                        && (Integer.valueOf(2).equals(currentStatus.getStatus())
                        || Integer.valueOf(3).equals(currentStatus.getStatus()))
                        && Objects.equals(currentStatus.getCompanyId(), invitation.getCompanyId())
                        && Objects.equals(currentStatus.getPositionId(), invitation.getPositionId());
                boolean currentAssignment = studentAssignments.stream().anyMatch(item ->
                        Integer.valueOf(1).equals(item.getCurrentFlag())
                                && Objects.equals(item.getCompanyId(), invitation.getCompanyId())
                                && Objects.equals(item.getPositionId(), invitation.getPositionId()));
                boolean offerUsed = studentAssignments.stream().anyMatch(item ->
                        InternshipConfirmationWorkflowService.OFFER_SOURCE.equals(item.getSourceType())
                                && Objects.equals(item.getSourceAssignmentId(), invitation.getId())
                                && item.getConfirmationRecordId() != null);
                if (currentSamePlacement || currentAssignment || offerUsed) continue;

                Position position = positionService.findById(invitation.getPositionId());
                if (position == null || !Objects.equals(position.getCompanyId(), invitation.getCompanyId())) continue;
                CompanyUser company = companyUserService.findById(invitation.getCompanyId());
                Long applicationId = latestApplicationId(applications, invitation);

                Map<String, Object> offer = new LinkedHashMap<>();
                offer.put("offerId", invitation.getId());
                offer.put("invitationId", invitation.getId());
                offer.put("applicationId", applicationId);
                offer.put("sourceType", InternshipConfirmationWorkflowService.OFFER_SOURCE);
                offer.put("sourceId", invitation.getId());
                offer.put("companyId", invitation.getCompanyId());
                offer.put("companyName", company == null ? invitation.getCompanyName() : company.getCompanyName());
                offer.put("companyAddress", company == null ? invitation.getCompanyAddress() : company.getAddress());
                offer.put("companyPhone", com.internship.entity.dto.InternshipContact.resolve(invitation.getCompanyId(), company, position).phone());
                offer.put("positionId", position.getId());
                offer.put("positionName", position.getPositionName());
                offer.put("directHire", "direct_hire".equals(invitation.getInterviewMethod()));
                offer.put("offerTime", invitation.getUpdateTime() == null
                        ? invitation.getCreateTime() : invitation.getUpdateTime());
                offers.add(offer);
            }
            return Result.success(offers);
        } catch (Exception e) {
            log.error("获取可确认 Offer 失败: {}", e.getMessage(), e);
            return Result.error("获取失败");
        }
    }

    @PostMapping({"", "/submit-offer"})
    @Transactional(rollbackFor = Exception.class)
    @Log(operationType = "UPDATE", module = "INTERNSHIP_MANAGEMENT", description = "提交Offer处理", riskLevel = "MEDIUM")
    public Result submitOffer(@RequestBody Map<String, Object> body) {
        try {
            User user = getCurrentUser();
            if (user == null) return Result.error("未登录");
            if (!(body.get("offerId") instanceof Number)) {
                return Result.error("offerId不能为空");
            }
            Long offerId = ((Number) body.get("offerId")).longValue();
            InterviewInvitation invitation = requireConfirmableOffer(user.getId(), offerId);
            Position position = positionService.findById(invitation.getPositionId());
            CompanyUser company = companyUserService.findById(invitation.getCompanyId());
            StudentUser student = studentUserService.findById(user.getId());
            if (position == null || company == null
                    || !Objects.equals(position.getCompanyId(), company.getId())) {
                return Result.error("Offer 对应的企业或岗位不存在");
            }

            InternshipConfirmationRecord record = new InternshipConfirmationRecord();
            record.setStudentId(user.getId());
            if (student != null) {
                record.setStudentName(student.getName());
                record.setStudentUserId(student.getStudentUserId());
                record.setGender(student.getGender() == null ? null
                        : (student.getGender() == 1 ? "男" : "女"));
                record.setGrade(student.getGrade() == null ? null : String.valueOf(student.getGrade()));
                record.setMajor(student.getMajor());
                record.setClassName(student.getClassName());
                record.setContactPhone(student.getPhone());
                record.setEmail(student.getEmail());
            }
            record.setCompanyId(company.getId());
            record.setCompanyName(company.getCompanyName());
            record.setCompanyAddress(company.getAddress());
            record.setCompanyPhone(com.internship.entity.dto.InternshipContact.resolve(company.getId(), company, position).phone());
            record.setPositionId(position.getId());
            record.setPositionName(position.getPositionName());
            record.setRemark(objectToString(body.get("remark")));
            record.setAgreementFileIds(parseAgreementFileIds(body.get("agreementFileIds")));

            List<StudentJobApplication> applications = studentJobApplicationService
                    .findByStudentId(user.getId());
            Long applicationId = latestApplicationId(
                    applications == null ? List.of() : applications, invitation);
            confirmationWorkflowService.submitOffer(record, offerId, applicationId);
            InternshipStageAssignment placement = internshipStageAssignmentService
                    .findByConfirmationRecordId(record.getId());
            runAfterCommit(() -> pushTodoUpdate(record.getCompanyId()));
            Map<String, Object> data = new HashMap<>();
            data.put("id", record.getId());
            data.put("recordId", record.getId());
            data.put("placementId", placement == null ? null : placement.getId());
            data.put("offerId", offerId);
            data.put("invitationId", offerId);
            data.put("applicationId", applicationId);
            data.put("sourceType", InternshipConfirmationWorkflowService.OFFER_SOURCE);
            data.put("sourceId", offerId);
            return Result.success(data);
        } catch (Exception e) {
            rollbackCurrentTransaction();
            log.error("提交 Offer 确认失败: {}", e.getMessage(), e);
            return Result.error(e.getMessage());
        }
    }

    private InterviewInvitation requireConfirmableOffer(Long studentId, Long offerId) {
        InterviewInvitation invitation = interviewInvitationService.findById(offerId);
        if (invitation == null || !Objects.equals(studentId, invitation.getStudentId())
                || !"interview_passed".equals(invitation.getStatus())) {
            throw new BusinessException("Offer 不存在、已失效或不属于当前学生");
        }
        if (invitation.getCompanyId() == null || invitation.getPositionId() == null) {
            throw new BusinessException("Offer 企业或岗位信息不完整");
        }
        return invitation;
    }

    private List<Long> parseAgreementFileIds(Object value) {
        if (!(value instanceof List<?> values)) {
            throw new BusinessException("请至少上传一份已盖章的实习协议");
        }
        List<Long> ids = new ArrayList<>();
        for (Object item : values) {
            if (!(item instanceof Number number) || number.doubleValue() != number.longValue()) {
                throw new BusinessException("协议文件编号无效");
            }
            ids.add(number.longValue());
        }
        return com.internship.service.InternshipAgreementService.requireFileIds(ids);
    }

    private Long latestApplicationId(List<StudentJobApplication> applications,
                                     InterviewInvitation invitation) {
        return applications.stream()
                .filter(item -> "interview_passed".equals(item.getStatus()))
                .filter(item -> Objects.equals(item.getCompanyId(), invitation.getCompanyId()))
                .filter(item -> Objects.equals(item.getPositionId(), invitation.getPositionId()))
                .map(StudentJobApplication::getId)
                .filter(Objects::nonNull)
                .max(Long::compareTo)
                .orElse(null);
    }

    private boolean isSubmittedPending(InternshipConfirmationRecord record) {
        return Integer.valueOf(0).equals(record.getStatus())
                && Integer.valueOf(0).equals(record.getRecallStatus());
    }

    @GetMapping
    public Result get() {
        try {
            User user = getCurrentUser();
            if (user == null) return Result.error("未登录");
            Long studentId = user.getId();

            StudentUser studentUser = studentUserService.findById(studentId);
            StudentInternshipStatus status = internshipStatusService.findByStudentId(studentId);

            Map<String, Object> data = new HashMap<>();
            if (studentUser != null) {
                data.put("studentName", studentUser.getName());
                data.put("studentId", studentUser.getStudentUserId());
                data.put("gender", studentUser.getGender() != null
                        ? (studentUser.getGender() == 1 ? "男" : "女") : "");
                data.put("grade", studentUser.getGrade());
                data.put("className", studentUser.getClassName());
            }
            if (status != null) {
                data.put("internshipStatusId", status.getId());
                data.put("internshipStatus", status.getStatus());
                data.put("companyId", status.getCompanyId());
                data.put("companyName", status.getCompanyName());
                data.put("companyAddress", status.getCompanyAddress());
                data.put("companyPhone", status.getCompanyPhone());
                data.put("positionId", status.getPositionId());
                data.put("positionName", status.getPositionName());
                data.put("internshipStartTime", status.getInternshipStartTime());
                data.put("internshipEndTime", status.getInternshipEndTime());
                data.put("internshipDuration", status.getInternshipDuration());
                data.put("feedback", status.getFeedback());
                data.put("remark", status.getRemark());
            }
            // 企业Logo（企业在宣传资料中上传），供学生端"当前实习"板块展示企业头像
            if (status != null && status.getCompanyId() != null) {
                try {
                    CompanyUser currentCompany = companyUserService.findById(status.getCompanyId());
                    if (currentCompany != null && currentCompany.getLogo() != null
                            && !currentCompany.getLogo().trim().isEmpty()) {
                        data.put("companyLogo", currentCompany.getLogo());
                    }
                } catch (Exception ex) {
                    log.warn("获取当前实习企业Logo失败: companyId={}, {}", status.getCompanyId(), ex.getMessage());
                }
            }
            InternshipStageAssignment currentPlacement = findCurrentAssignmentForStatus(status);
            data.put("placementId", currentPlacement == null ? null : currentPlacement.getId());
            data.put("isCurrent", currentPlacement != null);
            // 若实习状态中企业/岗位名称缺失（导入阶段未写入），从当前分配记录补全
            if (status != null && currentPlacement != null) {
                if ((status.getCompanyName() == null || status.getCompanyName().isEmpty())
                        && currentPlacement.getCompanyName() != null) {
                    data.put("companyName", currentPlacement.getCompanyName());
                }
                if ((status.getPositionName() == null || status.getPositionName().isEmpty())
                        && currentPlacement.getPositionName() != null) {
                    data.put("positionName", currentPlacement.getPositionName());
                }
            }
            InternshipConfirmationRecord pending = confirmationRecordService.findByStudentId(studentId).stream()
                    .filter(this::isSubmittedPending)
                    .findFirst()
                    .orElse(null);
            data.put("pendingConfirmationId", pending == null ? null : pending.getId());
            data.put("pendingCompanyId", pending == null ? null : pending.getCompanyId());
            data.put("pendingPositionId", pending == null ? null : pending.getPositionId());
            return Result.success(data);
        } catch (Exception e) {
            log.error("获取实习确认表失败: {}", e.getMessage(), e);
            return Result.error("获取失败");
        }
    }

    @PostMapping("/save")
    @Log(operationType = "CREATE", module = "INTERNSHIP_MANAGEMENT", description = "保存实习确认草稿", riskLevel = "LOW")
    public Result save(@RequestBody(required = false) Map<String, Object> body) {
        return Result.error("草稿确认入口已停用，请从可确认 Offer 列表选择并提交 offerId");
    }

    @PostMapping("/submit")
    public Result submit(@RequestBody(required = false) Map<String, Object> body) {
        return Result.error("旧确认提交入口已停用，请提交 offerId");
    }


    /**
     * 推送企业待办数据更新
     */
    private void pushTodoUpdate(Long companyId) {
        try {
            // 待处理申请数
            List<com.internship.entity.StudentJobApplication> applications = studentJobApplicationService.findByCompanyId(companyId);
            long pendingApplications = applications.stream()
                    .filter(a -> "pending".equals(a.getStatus()))
                    .count();

            // 待确认实习表数 - 从学生实习状态表查询（company_confirm_status=0表示待确认）
            long pendingConfirmations = confirmationRecordService
                    .findByCompanyIdAndStatus(companyId, 0).size();

            webSocketHandler.sendCompanyTodoUpdate(companyId, pendingApplications, pendingConfirmations);
            log.info("推送待办数据更新成功：待处理申请={}, 待确认实习表={}", pendingApplications, pendingConfirmations);
        } catch (Exception e) {
            log.error("推送待办数据失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取确认历史记录
     */
    @GetMapping("/history")
    public Result getHistory() {
        try {
            User user = getCurrentUser();
            if (user == null) return Result.error("未登录");
            Long studentId = user.getId();

            // 从确认记录表查询所有记录
            List<InternshipConfirmationRecord> records = confirmationRecordService.findByStudentIdOrderByCreateTimeDesc(studentId);

            // 获取学生的实习状态
            StudentInternshipStatus internshipStatus = internshipStatusService.findByStudentId(studentId);
            int studentInternshipStatus = internshipStatus != null ? internshipStatus.getStatus() : 0;

            java.util.List<Map<String, Object>> history = new java.util.ArrayList<>();

            // 检查是否需要从student_internship_status补充当前记录
            // 如果status >= 2（已确定/实习中/已结束）但确认记录表中没有已确认的记录，则补充一条
            boolean hasConfirmedRecord = records.stream().anyMatch(r -> r.getStatus() == 1);
            if (internshipStatus != null && internshipStatus.getStatus() >= 2 && !hasConfirmedRecord
                    && internshipStatus.getCompanyName() != null && !internshipStatus.getCompanyName().isEmpty()) {
                // 从student_internship_status补充当前记录
                Map<String, Object> currentItem = new java.util.HashMap<>();
                currentItem.put("id", internshipStatus.getId());
                currentItem.put("studentName", internshipStatus.getStudentName());
                currentItem.put("studentUserId", user.getUsername());
                currentItem.put("studentNumber", user.getUsername());
                currentItem.put("gender", internshipStatus.getGender());
                currentItem.put("grade", internshipStatus.getGrade());
                currentItem.put("major", internshipStatus.getMajorName());
                currentItem.put("className", internshipStatus.getClassName());
                currentItem.put("contactPhone", internshipStatus.getContactPhone());
                currentItem.put("email", internshipStatus.getEmail());
                currentItem.put("companyName", internshipStatus.getCompanyName());
                currentItem.put("positionName", internshipStatus.getPositionName());
                currentItem.put("companyAddress", internshipStatus.getCompanyAddress());
                currentItem.put("companyPhone", internshipStatus.getCompanyPhone());
                currentItem.put("companyId", internshipStatus.getCompanyId());
                currentItem.put("positionId", internshipStatus.getPositionId());
                currentItem.put("internshipStartTime", internshipStatus.getInternshipStartTime());
                currentItem.put("internshipEndTime", internshipStatus.getInternshipEndTime());
                currentItem.put("internshipDuration", internshipStatus.getInternshipDuration());
                currentItem.put("remark", internshipStatus.getRemark());
                currentItem.put("rejectionReason", null);
                // status=1表示已确认（从student_internship_status补充的）
                currentItem.put("status", 1);
                currentItem.put("internshipStatus", studentInternshipStatus);
                InternshipStageAssignment currentPlacement = findCurrentAssignmentForStatus(internshipStatus);
                currentItem.put("placementId", currentPlacement == null ? null : currentPlacement.getId());
                currentItem.put("isCurrent", true);
                currentItem.put("lifecycleStatus", lifecycleStatusForCurrent(studentInternshipStatus));
                currentItem.put("statusText", lifecycleStatusText(
                        lifecycleStatusForCurrent(studentInternshipStatus)));
                // 使用updateTime确保当前记录时间最新，排在最前
                currentItem.put("createTime", internshipStatus.getUpdateTime());
                currentItem.put("isFromStatus", true); // 标记为从status表补充
                history.add(currentItem);
            }

            // 添加确认记录表中的记录
            for (InternshipConfirmationRecord record : records) {
                Map<String, Object> item = new java.util.HashMap<>();
                item.put("id", record.getId());
                // 学生信息
                item.put("studentName", record.getStudentName());
                item.put("studentUserId", record.getStudentUserId());
                item.put("studentNumber", record.getStudentUserId());
                item.put("gender", record.getGender());
                item.put("grade", record.getGrade());
                item.put("major", record.getMajor());
                item.put("className", record.getClassName());
                item.put("contactPhone", record.getContactPhone());
                item.put("email", record.getEmail());
                // 企业信息
                item.put("companyName", record.getCompanyName());
                item.put("positionName", record.getPositionName());
                item.put("companyAddress", record.getCompanyAddress());
                item.put("companyPhone", record.getCompanyPhone());
                item.put("companyId", record.getCompanyId());
                item.put("positionId", record.getPositionId());
                // 实习时间
                item.put("internshipStartTime", record.getInternshipStartTime());
                item.put("internshipEndTime", record.getInternshipEndTime());
                item.put("internshipDuration", record.getInternshipDuration());
                item.put("remark", record.getRemark());
                item.put("rejectionReason", record.getRemark());
                // status: 确认记录状态 0=待确认, 1=已确认, 2=已拒绝
                item.put("status", record.getStatus());
                item.put("recallStatus", record.getRecallStatus());
                // internshipStatus: 学生实习状态：0=待就业，1=待确认，2=已确定，3=实习中，4=已结束，5=已中断
                item.put("internshipStatus", studentInternshipStatus);
                InternshipStageAssignment placement = internshipStageAssignmentService
                        .findByConfirmationRecordId(record.getId());
                boolean isCurrent = placement != null
                        && Integer.valueOf(1).equals(placement.getCurrentFlag())
                        && Integer.valueOf(1).equals(record.getStatus());
                String lifecycleStatus = lifecycleStatusForRecord(
                        record, placement, isCurrent ? studentInternshipStatus : null);
                item.put("placementId", placement == null ? null : placement.getId());
                boolean selfPractice = placement != null && "SELF_PRACTICE".equals(placement.getSourceType());
                item.put("agreementSourceType", selfPractice ? "SELF_PRACTICE" : "CONFIRMATION");
                item.put("agreementSourceId", selfPractice ? placement.getSourceAssignmentId() : record.getId());
                item.put("isCurrent", isCurrent);
                item.put("lifecycleStatus", lifecycleStatus);
                item.put("statusText", lifecycleStatusText(lifecycleStatus));
                item.put("createTime", record.getCreateTime());
                item.put("isFromStatus", false);
                history.add(item);
            }

            // 按isFromStatus降序（true在前）、createTime降序排序，确保当前实习状态记录优先
            history.sort((a, b) -> {
                Boolean isFromStatusA = (Boolean) a.get("isFromStatus");
                Boolean isFromStatusB = (Boolean) b.get("isFromStatus");
                if (isFromStatusA != isFromStatusB) {
                    return isFromStatusA ? -1 : 1;
                }
                Object createTimeA = a.get("createTime");
                Object createTimeB = b.get("createTime");
                if (createTimeA == null && createTimeB == null) return 0;
                if (createTimeA == null) return 1;
                if (createTimeB == null) return -1;
                // 兼容 LocalDateTime 和 java.util.Date
                if (createTimeA instanceof java.util.Date && createTimeB instanceof java.util.Date) {
                    return ((java.util.Date) createTimeB).compareTo((java.util.Date) createTimeA);
                } else if (createTimeA instanceof java.time.LocalDateTime && createTimeB instanceof java.time.LocalDateTime) {
                    return ((java.time.LocalDateTime) createTimeB).compareTo((java.time.LocalDateTime) createTimeA);
                }
                return 0;
            });

            return Result.success(history);
        } catch (Exception e) {
            log.error("获取确认历史失败: {}", e.getMessage(), e);
            return Result.error("获取失败");
        }
    }

    /** 旧单位变更审批查询已退役，换司统一走 Offer 确认记录。 */
    @GetMapping("/unit-change/status")
    public Result getUnitChangeStatus() {
        return Result.error("单位变更审批流程已停用，请申请新岗位并提交 offerId");
    }

    /**
     * 提交单位变更申请
     */
    @PostMapping("/unit-change/submit")
    @Log(operationType = "UPDATE", module = "INTERNSHIP_MANAGEMENT", description = "提交单位变更", riskLevel = "MEDIUM")
    public Result submitUnitChange(@RequestBody Map<String, Object> body) {
        return Result.error("单位变更审批流程已停用，请申请新岗位并提交新的实习确认表");
    }


    /**
     * 再次申请（更新被驳回的申请）
     */
    @PutMapping("/unit-change/resubmit/{id}")
    @Log(operationType = "UPDATE", module = "INTERNSHIP_MANAGEMENT", description = "重新提交单位变更", riskLevel = "MEDIUM")
    public Result resubmitUnitChange(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.error("单位变更审批流程已停用，请申请新岗位并提交新的实习确认表");
    }


    /**
     * 撤回实习确认申请
     */
    @PostMapping("/{id}/recall")
    @Transactional(rollbackFor = Exception.class)
    @Log(operationType = "UPDATE", module = "INTERNSHIP_MANAGEMENT", description = "撤回实习确认申请", riskLevel = "MEDIUM")
    public Result recall(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            User user = getCurrentUser();
            if (user == null) return Result.error("未登录");
            String reason = body.get("recallReason");
            if (reason == null || reason.isBlank()) return Result.error("请填写撤回原因");
            InternshipConfirmationWorkflowService.ConfirmationOutcome outcome =
                    confirmationWorkflowService.withdraw(user.getId(), id, reason.trim());
            runAfterCommit(() -> pushTodoUpdate(outcome.record().getCompanyId()));
            return Result.success("撤回成功");
        } catch (Exception e) {
            rollbackCurrentTransaction();
            log.error("撤回实习确认表失败: {}", e.getMessage(), e);
            return Result.error("撤回失败: " + e.getMessage());
        }
    }

    private void runAfterCommit(Runnable action) {
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


    /**
     * 获取实习确认表撤回记录列表（管理员）
     */
    @GetMapping("/recall/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public Result getConfirmationRecallPendingList(@RequestParam(defaultValue = "1") Integer page,
                                                   @RequestParam(defaultValue = "10") Integer pageSize,
                                                   @RequestParam(required = false) Long studentId,
                                                   @RequestParam(required = false) String name,
                                                   @RequestParam(required = false) String companyName) {
        log.info("获取实习确认表撤回记录列表，页码：{}, 每页条数：{}, 学生ID：{}, 姓名：{}, 企业名称：{}",
                page, pageSize, studentId, name, companyName);
        try {
            List<InternshipConfirmationRecord> allRecords = confirmationRecordService.findPendingRecallList(studentId, name, companyName);
            int total = allRecords.size();
            int start = (page - 1) * pageSize;
            int end = Math.min(start + pageSize, total);
            List<InternshipConfirmationRecord> pageRecords = start < total ? allRecords.subList(start, end) : List.of();
            return Result.success(new java.util.HashMap<String, Object>() {{
                put("rows", pageRecords);
                put("total", total);
            }});
        } catch (Exception e) {
            log.error("获取实习确认表撤回记录列表失败：{}", e.getMessage(), e);
            return Result.error("获取失败");
        }
    }

    private void rollbackCurrentTransaction() {
        try {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        } catch (NoTransactionException ignored) {
            // Direct controller unit tests do not run through a transactional proxy.
        }
    }

    private InternshipStageAssignment findCurrentAssignmentForStatus(StudentInternshipStatus status) {
        if (status == null || status.getStudentId() == null) return null;
        List<InternshipStageAssignment> assignments = internshipStageAssignmentService
                .findByStudentId(status.getStudentId());
        if (assignments == null) return null;
        return assignments.stream()
                .filter(item -> Integer.valueOf(1).equals(item.getCurrentFlag()))
                .filter(item -> java.util.Objects.equals(status.getCompanyId(), item.getCompanyId()))
                .filter(item -> java.util.Objects.equals(status.getPositionId(), item.getPositionId()))
                .max(java.util.Comparator
                        .comparing((InternshipStageAssignment item) ->
                                item.getSequenceNo() == null ? 0 : item.getSequenceNo())
                        .thenComparing(item -> item.getId() == null ? 0L : item.getId()))
                .orElse(null);
    }

    private String lifecycleStatusForCurrent(int status) {
        return switch (status) {
            case 2 -> "CONFIRMED";
            case 3 -> "IN_PROGRESS";
            case 4 -> "COMPLETED";
            case 5 -> "INTERRUPTED";
            default -> "CONFIRMED";
        };
    }

    private String lifecycleStatusForRecord(InternshipConfirmationRecord record,
                                            InternshipStageAssignment placement,
                                            Integer currentStatus) {
        if (Integer.valueOf(2).equals(record.getStatus())) return "REJECTED";
        if (Integer.valueOf(2).equals(record.getRecallStatus())) return "WITHDRAWN";
        if (Integer.valueOf(0).equals(record.getStatus())) {
            return Integer.valueOf(0).equals(record.getRecallStatus()) ? "PENDING_CONFIRMATION" : "DRAFT";
        }
        if (currentStatus != null) return lifecycleStatusForCurrent(currentStatus);
        if (placement != null && placement.getStatus() != null) return placement.getStatus();
        if (record.getInternshipEndTime() != null
                && !record.getInternshipEndTime().isAfter(LocalDateTime.now())) return "COMPLETED";
        return "HISTORICAL";
    }

    private String lifecycleStatusText(String status) {
        return switch (status) {
            case "DRAFT" -> "草稿";
            case "PENDING_CONFIRMATION" -> "待企业确认";
            case "CONFIRMED" -> "已定单位待实习";
            case "IN_PROGRESS" -> "实习进行中";
            case "COMPLETED" -> "实习已结束";
            case "REPLACED" -> "已更换岗位";
            case "INTERRUPTED" -> "实习已中断";
            case "REJECTED" -> "企业已拒绝";
            case "WITHDRAWN" -> "学生已撤回";
            default -> "历史记录";
        };
    }
}
