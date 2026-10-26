package com.internship.controller;

import com.internship.anno.Log;
import com.internship.config.EmailNotifyScope;
import com.internship.config.SystemSettingsConfig;
import com.internship.exception.BusinessException;
import com.internship.entity.CompanyUser;
import com.internship.entity.PageResult;
import com.internship.entity.Result;
import com.internship.entity.StudentApplication;
import com.internship.entity.TeacherUser;
import com.internship.entity.User;
import com.internship.mapper.TeacherAutomationScopeMapper;
import com.internship.service.CompanyUserService;
import com.internship.service.EmailService;
import com.internship.service.StudentApplicationService;
import com.internship.service.TeacherUserService;
import com.internship.service.TeacherAutomationScopeService;
import com.internship.utils.CurrentHolder;
import com.internship.utils.AliyunOSSOperator;
import com.internship.utils.CompanyQualificationMaterial;
import com.internship.utils.RecordAttachmentResponse;
import com.internship.utils.MaterialMetadataRedactor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 审核管理控制器
 * 处理学生申请和企业注册申请审核的所有接口请求
 */
@Slf4j
@RestController
@RequestMapping("/api/approval")
@PreAuthorize("hasAnyRole('TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR')")
public class ApprovalController {

    @Autowired
    private StudentApplicationService studentApplicationService;

    @Autowired
    private CompanyUserService companyUserService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private TeacherUserService teacherUserService;

    @Autowired
    private TeacherAutomationScopeMapper teacherScopeMapper;

    @Autowired
    private AliyunOSSOperator aliyunOSSOperator;

    private static final Set<String> COLLEGE_APPLICATION_TYPES = Set.of("delay", "unitChange");

    private TeacherUser requireTeacher() {
        User actor = CurrentHolder.getUser();
        if (actor == null || actor.getId() == null || actor.getUsername() == null) {
            throw new AccessDeniedException("教师身份不可用");
        }
        TeacherUser teacher;
        try {
            teacher = teacherUserService.findById(actor.getId());
            TeacherAutomationScopeService.requireTeacher(teacher);
        } catch (Exception e) {
            throw new AccessDeniedException("教师身份或管辖范围不可用", e);
        }
        String expectedRole = "ROLE_TEACHER_" + teacher.getTeacherType();
        if (!actor.getUsername().equals(teacher.getTeacherUserId())
                || !expectedRole.equals(actor.getRole())) {
            throw new AccessDeniedException("教师身份不匹配");
        }
        return teacher;
    }

    private List<Long> scopedStudentIds(TeacherUser teacher) {
        return scopedStudentIds(teacher, null, null);
    }

    private List<Long> scopedStudentIds(TeacherUser teacher, Long majorId, Long classId) {
        try {
            List<Long> ids = majorId == null && classId == null
                    ? teacherScopeMapper.studentIds(teacher)
                    : teacherScopeMapper.filteredStudentIds(teacher, majorId, classId);
            return ids == null ? List.of() : ids.stream().filter(id -> id != null && id > 0).distinct().toList();
        } catch (Exception e) {
            throw new AccessDeniedException("教师管辖范围不可用", e);
        }
    }

    private void requireStudentInScope(TeacherUser teacher, Long studentId) {
        try {
            if (studentId != null && teacherScopeMapper.contains(teacher, studentId) > 0) return;
        } catch (Exception e) {
            throw new AccessDeniedException("教师管辖范围不可用", e);
        }
        throw new AccessDeniedException("无权访问该学生的申请");
    }

    private boolean canHandleApplicationType(TeacherUser teacher, String type) {
        if (type == null) return false;
        return "COUNSELOR".equals(teacher.getTeacherType())
                ? "selfPractice".equals(type) : COLLEGE_APPLICATION_TYPES.contains(type);
    }

    private void requireApplicationAccess(TeacherUser teacher, StudentApplication application) {
        if (!canHandleApplicationType(teacher, application.getApplicationType())) {
            throw new AccessDeniedException("无权访问此类申请");
        }
        requireStudentInScope(teacher, application.getStudentId());
    }

    private PageResult<StudentApplication> emptyPage(Integer page, Integer pageSize) {
        return PageResult.build(0L, List.of(), 0, page, pageSize);
    }

    private boolean hasCompanyAuditPermission() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(authority -> "user:company:audit".equals(authority.getAuthority()));
    }

    private TeacherUser requireCompanyAuditor() {
        TeacherUser teacher = requireTeacher();
        if (!hasCompanyAuditPermission()) throw new AccessDeniedException("无企业审核权限");
        return teacher;
    }

    /**
     * 获取审核统计数据
     */
    @GetMapping("/stats")
    public Result getStats() {
        log.info("获取审核统计数据");
        TeacherUser teacher = requireTeacher();
        try {
            Map<String, Object> stats = new HashMap<>();
            List<Long> studentIds = scopedStudentIds(teacher);
            boolean counselor = "COUNSELOR".equals(teacher.getTeacherType());
            stats.put("selfPracticePending", counselor ? pendingCount("selfPractice", studentIds) : 0L);
            stats.put("unitChangePending", counselor ? 0L : pendingCount("unitChange", studentIds));
            stats.put("delayPending", counselor ? 0L : pendingCount("delay", studentIds));
            stats.put("companyQualificationPending", hasCompanyAuditPermission()
                    ? companyUserService.countByAuditStatus(0) : 0);

            return Result.success(stats);
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取审核统计数据失败: {}", e.getMessage(), e);
            return Result.error("获取审核统计数据失败: " + e.getMessage());
        }
    }

    /**
     * 获取学生申请列表
     */
    @GetMapping("/student-applications")
    public Result getStudentApplications(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String applicationType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String studentName,
            @RequestParam(required = false) String studentUserId,
            @RequestParam(required = false) Long majorId,
            @RequestParam(required = false) Long classId) {
        log.info("获取学生申请列表: page={}, pageSize={}, type={}, status={}, name={}, userId={}, majorId={}, classId={}",
                page, pageSize, applicationType, status, studentName, studentUserId, majorId, classId);
        TeacherUser teacher = requireTeacher();
        try {
            if (!canHandleApplicationType(teacher, applicationType)) {
                return Result.success(emptyPage(page, pageSize));
            }
            PageResult<StudentApplication> result = studentApplicationService.findPageWithinStudentIds(
                    page, pageSize, applicationType, status, studentName, studentUserId,
                    scopedStudentIds(teacher, majorId, classId));
            return Result.success(MaterialMetadataRedactor.studentPage(result));
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取学生申请列表失败: {}", e.getMessage(), e);
            return Result.error("获取学生申请列表失败: " + e.getMessage());
        }
    }

    /**
     * 根据ID获取学生申请详情
     */
    @GetMapping("/student-applications/{id}")
    public Result getStudentApplicationById(@PathVariable Long id) {
        log.info("获取学生申请详情: id={}", id);
        TeacherUser teacher = requireTeacher();
        try {
            StudentApplication application = studentApplicationService.findById(id);
            if (application == null) {
                return Result.error("申请不存在");
            }
            requireApplicationAccess(teacher, application);
            return Result.success(MaterialMetadataRedactor.studentApplication(application));
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取学生申请详情失败: {}", e.getMessage(), e);
            return Result.error("获取学生申请详情失败: " + e.getMessage());
        }
    }

    @GetMapping("/student-applications/{id}/materials/{key}")
    @Log(operationType = "DOWNLOAD", module = "INTERNSHIP_MANAGEMENT", description = "下载管辖学生申请材料", riskLevel = "MEDIUM")
    public ResponseEntity<byte[]> downloadStudentApplicationMaterial(@PathVariable Long id, @PathVariable String key) {
        TeacherUser teacher = requireTeacher();
        StudentApplication application = studentApplicationService.findById(id);
        if (application == null) return ResponseEntity.notFound().build();
        requireApplicationAccess(teacher, application);
        String storedUrl = application.getMaterials() == null ? null : application.getMaterials().get(key);
        if (storedUrl == null || storedUrl.isBlank()) return ResponseEntity.notFound().build();
        try {
            return RecordAttachmentResponse.ok(aliyunOSSOperator.downloadRecordAttachment(storedUrl, application.getStudentId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(403).build();
        } catch (Exception e) {
            log.error("读取教师审核申请材料失败: applicationId={}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 批准学生申请
     */
    @PostMapping("/student-applications/{id}/approve")
    @Log(operationType = "AUDIT", module = "INTERNSHIP_MANAGEMENT", description = "批准学生实习申请")
    public Result approveStudentApplication(@PathVariable Long id, @RequestParam @NotNull Long reviewerId) {
        TeacherUser teacher = requireTeacher();
        log.info("批准学生申请: id={}, reviewerId={}", id, teacher.getId());
        try {
            checkApplicationAuditPermission(teacher, id);
            int result = studentApplicationService.approve(id, teacher.getId());
            if (result > 0) {
                return Result.success("申请已成功通过");
            }
            return Result.error("批准申请失败");
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("批准学生申请失败: {}", e.getMessage(), e);
            return Result.error("批准申请失败: " + e.getMessage());
        }
    }

    /**
     * 驳回学生申请
     */
    @PostMapping("/student-applications/{id}/reject")
    @Log(operationType = "AUDIT", module = "INTERNSHIP_MANAGEMENT", description = "驳回学生实习申请")
    public Result rejectStudentApplication(
            @PathVariable Long id,
            @RequestParam @NotNull Long reviewerId,
            @RequestParam @NotNull String rejectReason) {
        TeacherUser teacher = requireTeacher();
        log.info("驳回学生申请: id={}, reviewerId={}", id, teacher.getId());
        try {
            checkApplicationAuditPermission(teacher, id);
            int result = studentApplicationService.reject(id, teacher.getId(), rejectReason);
            if (result > 0) {
                return Result.success("申请已成功驳回");
            }
            return Result.error("驳回申请失败");
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("驳回学生申请失败: {}", e.getMessage(), e);
            return Result.error("驳回申请失败: " + e.getMessage());
        }
    }

    /**
     * 校验当前登录教师是否有权审核该申请:
     * 自主实习申请由辅导员负责(且仅限自己负责班级);考研延迟由系室/学院教师负责
     */
    private void checkApplicationAuditPermission(TeacherUser teacher, Long id) {
        StudentApplication application = studentApplicationService.findById(id);
        if (application == null) {
            throw new BusinessException("申请不存在");
        }
        requireApplicationAccess(teacher, application);
    }

    private long pendingCount(String type, List<Long> studentIds) {
        return studentApplicationService.findPageWithinStudentIds(
                1, 1, type, "pending", null, null, studentIds).getTotal();
    }

    /**
     * 获取企业注册申请列表
     */
    @GetMapping("/company-qualifications")
    @PreAuthorize("hasAuthority('user:company:audit')")
    public Result getCompanyQualifications(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String companyName) {
        log.info("获取企业注册申请列表: page={}, pageSize={}, status={}, companyName={}",
                page, pageSize, status, companyName);
        requireCompanyAuditor();
        try {
            // status 参数转换为 Integer (0=待审核, 1=已通过, 2=已拒绝)
            Integer auditStatus = null;
            if (status != null && !status.isEmpty()) {
                if ("pending".equals(status) || "0".equals(status)) {
                    auditStatus = 0;
                } else if ("approved".equals(status) || "1".equals(status)) {
                    auditStatus = 1;
                } else if ("rejected".equals(status) || "2".equals(status)) {
                    auditStatus = 2;
                }
            }
            PageResult<CompanyUser> result = companyUserService.findPendingAuditPage(
                    page, pageSize, companyName, null, null, auditStatus);
            return Result.success(MaterialMetadataRedactor.companyPage(result));
        } catch (Exception e) {
            log.error("获取企业注册申请列表失败: {}", e.getMessage(), e);
            return Result.error("获取企业注册申请列表失败: " + e.getMessage());
        }
    }

    /**
     * 根据ID获取企业注册申请详情
     */
    @GetMapping("/company-qualifications/{id}")
    @PreAuthorize("hasAuthority('user:company:audit')")
    public Result getCompanyQualificationById(@PathVariable Long id) {
        log.info("获取企业注册申请详情: id={}", id);
        requireCompanyAuditor();
        try {
            CompanyUser company = companyUserService.findById(id);
            if (company == null) {
                return Result.error("企业不存在");
            }
            return Result.success(MaterialMetadataRedactor.company(company));
        } catch (Exception e) {
            log.error("获取企业注册申请详情失败: {}", e.getMessage(), e);
            return Result.error("获取企业注册申请详情失败: " + e.getMessage());
        }
    }

    @GetMapping("/company-qualifications/{id}/materials/{kind}/{index}")
    @PreAuthorize("hasAuthority('user:company:audit')")
    @Log(operationType = "DOWNLOAD", module = "COMPANY_MANAGEMENT", description = "下载企业资质材料", riskLevel = "MEDIUM")
    public ResponseEntity<byte[]> downloadCompanyQualificationMaterial(@PathVariable Long id,
                                                                         @PathVariable String kind,
                                                                         @PathVariable int index) {
        requireCompanyAuditor();
        CompanyUser company = companyUserService.findById(id);
        if (company == null) return ResponseEntity.notFound().build();
        String storedUrl = CompanyQualificationMaterial.storedUrl(company, kind, index);
        if (storedUrl == null || storedUrl.isBlank()) return ResponseEntity.notFound().build();
        try {
            return RecordAttachmentResponse.ok(aliyunOSSOperator.downloadRecordAttachment(storedUrl, null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(403).build();
        } catch (Exception e) {
            log.error("读取企业资质材料失败: companyId={}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 批准企业注册申请
     */
    @PostMapping("/company-qualifications/{id}/approve")
    @PreAuthorize("hasAuthority('user:company:audit')")
    @Log(operationType = "AUDIT", module = "COMPANY_MANAGEMENT", description = "批准企业注册申请")
    public Result approveCompanyQualification(@PathVariable Long id, @RequestParam @NotNull Long reviewerId) {
        TeacherUser teacher = requireCompanyAuditor();
        log.info("批准企业注册申请: id={}, reviewerId={}", id, teacher.getId());
        try {
            CompanyUser company = companyUserService.findById(id);
            if (company == null) {
                return Result.error("企业不存在");
            }
            int result = companyUserService.approveQualification(id, teacher.getId());
            if (result > 0) {
                try {
                    companyTagService.updateCompanyTag(id);
                } catch (Exception tagException) {
                    log.error("企业注册申请通过后更新企业标签失败: companyId={}", id, tagException);
                }
                boolean notifyEnabled = SystemSettingsConfig.isEmailNotifyScopeEnabled(EmailNotifyScope.COMPANY_AUDIT);
                boolean emailNotified = notifyEnabled && sendCompanyApprovalEmail(company);
                String message = !notifyEnabled
                        ? "企业注册申请已通过"
                        : emailNotified
                        ? "企业注册申请已通过，审核结果已发送至企业邮箱"
                        : "企业注册申请已通过，但邮件通知未完成，请检查企业邮箱或邮件配置";
                return Result.success(message, Map.of("emailNotified", emailNotified));
            }
            return Result.error("批准失败");
        } catch (Exception e) {
            log.error("批准企业注册申请失败: {}", e.getMessage(), e);
            return Result.error("批准企业注册申请失败: " + e.getMessage());
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.internship.service.CompanyTagService companyTagService;

    /**
     * 驳回企业注册申请
     */
    @PostMapping("/company-qualifications/{id}/reject")
    @PreAuthorize("hasAuthority('user:company:audit')")
    @Log(operationType = "AUDIT", module = "COMPANY_MANAGEMENT", description = "驳回企业注册申请")
    public Result rejectCompanyQualification(
            @PathVariable Long id,
            @RequestParam @NotNull Long reviewerId,
            @RequestParam @NotNull String rejectReason) {
        TeacherUser teacher = requireCompanyAuditor();
        String normalizedRejectReason = rejectReason == null ? "" : rejectReason.trim();
        if (normalizedRejectReason.isEmpty()) {
            return Result.error("请填写驳回理由");
        }
        log.info("驳回企业注册申请: id={}, reviewerId={}", id, teacher.getId());
        try {
            CompanyUser company = companyUserService.findById(id);
            if (company == null) {
                return Result.error("企业不存在");
            }
            int result = companyUserService.rejectQualification(id, teacher.getId(), normalizedRejectReason);
            if (result > 0) {
                boolean notifyEnabled = SystemSettingsConfig.isEmailNotifyScopeEnabled(EmailNotifyScope.COMPANY_AUDIT);
                boolean emailNotified = notifyEnabled && sendCompanyRejectionEmail(company, normalizedRejectReason);
                String message = !notifyEnabled
                        ? "企业注册申请已驳回"
                        : emailNotified
                        ? "企业注册申请已驳回，驳回理由已发送至企业邮箱"
                        : "企业注册申请已驳回，但邮件通知未完成，请检查企业邮箱或邮件配置";
                return Result.success(message, Map.of("emailNotified", emailNotified));
            }
            return Result.error("驳回失败");
        } catch (Exception e) {
            log.error("驳回企业注册申请失败: {}", e.getMessage(), e);
            return Result.error("驳回企业注册申请失败: " + e.getMessage());
        }
    }

    private boolean sendCompanyApprovalEmail(CompanyUser company) {
        String content = company.getCompanyName() + "，您好：\n\n"
                + "您提交的企业注册申请已通过审核。\n\n"
                + "您可以登录系统完善企业信息并发布实习岗位。";
        return sendCompanyReviewEmail(company, content);
    }

    private boolean sendCompanyRejectionEmail(CompanyUser company, String rejectReason) {
        String content = company.getCompanyName() + "，您好：\n\n"
                + "您提交的企业注册申请未通过审核。\n\n"
                + "驳回理由：\n"
                + rejectReason + "\n\n"
                + "请根据以上审核意见补充或修正材料后重新提交申请。";
        return sendCompanyReviewEmail(company, content);
    }

    private boolean sendCompanyReviewEmail(CompanyUser company, String content) {
        String recipient = company.getContactEmail();
        if (recipient == null || recipient.isBlank()) {
            recipient = company.getEmail();
        }
        if (recipient == null || recipient.isBlank()) {
            log.warn("企业注册申请审核完成，但企业未绑定邮箱: companyId={}", company.getId());
            return false;
        }
        String subject = "企业注册申请审核结果通知";
        try {
            boolean sent = emailService.sendEmail(recipient.trim(), subject, content);
            if (!sent) {
                log.warn("企业注册申请审核邮件未发送成功: companyId={}, recipient={}", company.getId(), recipient);
            }
            return sent;
        } catch (Exception e) {
            log.error("企业注册申请审核邮件发送异常: companyId={}, recipient={}", company.getId(), recipient, e);
            return false;
        }
    }

    /**
     * 获取综合审核列表（包含学生申请和企业注册申请）
     */
    @GetMapping("/applications")
    public Result getApplications(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        log.info("获取综合审核列表: page={}, pageSize={}, type={}, status={}, keyword={}",
                page, pageSize, type, status, keyword);
        TeacherUser teacher = requireTeacher();
        try {
            Map<String, Object> result = new HashMap<>();

            if ("companyQualification".equals(type)) {
                requireCompanyAuditor();
                Integer auditStatus = null;
                if (status != null && !status.isEmpty()) {
                    if ("pending".equals(status) || "0".equals(status)) {
                        auditStatus = 0;
                    } else if ("approved".equals(status) || "1".equals(status)) {
                        auditStatus = 1;
                    } else if ("rejected".equals(status) || "2".equals(status)) {
                        auditStatus = 2;
                    }
                }
                PageResult<CompanyUser> companyResult = companyUserService.findPendingAuditPage(
                        page, pageSize, keyword, null, null, auditStatus);
                result.put("type", "companyQualification");
                result.put("data", MaterialMetadataRedactor.companyPage(companyResult));
            } else {
                PageResult<StudentApplication> studentResult = canHandleApplicationType(teacher, type)
                        ? studentApplicationService.findPageWithinStudentIds(
                                page, pageSize, type, status, keyword, null, scopedStudentIds(teacher))
                        : emptyPage(page, pageSize);
                result.put("type", type);
                result.put("data", MaterialMetadataRedactor.studentPage(studentResult));
            }

            return Result.success(result);
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取综合审核列表失败: {}", e.getMessage(), e);
            return Result.error("获取综合审核列表失败: " + e.getMessage());
        }
    }
}
