package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.entity.CompanyUser;
import com.gdmu.entity.PageResult;
import com.gdmu.entity.Result;
import com.gdmu.entity.StudentApplication;
import com.gdmu.service.CompanyUserService;
import com.gdmu.service.StudentApplicationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 审核管理控制器
 * 处理学生申请和企业注册申请审核的所有接口请求
 */
@Slf4j
@RestController
@RequestMapping("/api/approval")
@PreAuthorize("hasAnyRole('TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR')")
public class ApprovalController {

    @Autowired
    private StudentApplicationService studentApplicationService;

    @Autowired
    private CompanyUserService companyUserService;

    /**
     * 获取审核统计数据
     */
    @GetMapping("/stats")
    public Result getStats() {
        log.info("获取审核统计数据");
        try {
            Map<String, Object> stats = new HashMap<>();

            stats.put("selfPracticePending", studentApplicationService.countByTypeAndStatus("selfPractice", "pending"));
            stats.put("unitChangePending", studentApplicationService.countByTypeAndStatus("unitChange", "pending"));
            stats.put("delayPending", studentApplicationService.countByTypeAndStatus("delay", "pending"));
            stats.put("companyQualificationPending", companyUserService.countByAuditStatus(0));

            return Result.success(stats);
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
            @RequestParam(required = false) String studentUserId) {
        log.info("获取学生申请列表: page={}, pageSize={}, type={}, status={}, name={}, userId={}",
                page, pageSize, applicationType, status, studentName, studentUserId);
        try {
            PageResult<StudentApplication> result = studentApplicationService.findPage(
                    page, pageSize, applicationType, status, studentName, studentUserId);
            return Result.success(result);
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
        try {
            StudentApplication application = studentApplicationService.findById(id);
            if (application == null) {
                return Result.error("申请不存在");
            }
            return Result.success(application);
        } catch (Exception e) {
            log.error("获取学生申请详情失败: {}", e.getMessage(), e);
            return Result.error("获取学生申请详情失败: " + e.getMessage());
        }
    }

    /**
     * 批准学生申请
     */
    @PostMapping("/student-applications/{id}/approve")
    @Log(operationType = "AUDIT", module = "INTERNSHIP_MANAGEMENT", description = "批准学生实习申请")
    public Result approveStudentApplication(@PathVariable Long id, @RequestParam @NotNull Long reviewerId) {
        log.info("批准学生申请: id={}, reviewerId={}", id, reviewerId);
        try {
            int result = studentApplicationService.approve(id, reviewerId);
            if (result > 0) {
                return Result.success("申请已成功通过");
            }
            return Result.error("批准申请失败");
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
        log.info("驳回学生申请: id={}, reviewerId={}, reason={}", id, reviewerId, rejectReason);
        try {
            int result = studentApplicationService.reject(id, reviewerId, rejectReason);
            if (result > 0) {
                return Result.success("申请已成功驳回");
            }
            return Result.error("驳回申请失败");
        } catch (Exception e) {
            log.error("驳回学生申请失败: {}", e.getMessage(), e);
            return Result.error("驳回申请失败: " + e.getMessage());
        }
    }

    /**
     * 获取企业注册申请列表
     */
    @GetMapping("/company-qualifications")
    public Result getCompanyQualifications(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String companyName) {
        log.info("获取企业注册申请列表: page={}, pageSize={}, status={}, companyName={}",
                page, pageSize, status, companyName);
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
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取企业注册申请列表失败: {}", e.getMessage(), e);
            return Result.error("获取企业注册申请列表失败: " + e.getMessage());
        }
    }

    /**
     * 根据ID获取企业注册申请详情
     */
    @GetMapping("/company-qualifications/{id}")
    public Result getCompanyQualificationById(@PathVariable Long id) {
        log.info("获取企业注册申请详情: id={}", id);
        try {
            CompanyUser company = companyUserService.findById(id);
            if (company == null) {
                return Result.error("企业不存在");
            }
            return Result.success(company);
        } catch (Exception e) {
            log.error("获取企业注册申请详情失败: {}", e.getMessage(), e);
            return Result.error("获取企业注册申请详情失败: " + e.getMessage());
        }
    }

    /**
     * 批准企业注册申请
     */
    @PostMapping("/company-qualifications/{id}/approve")
    @Log(operationType = "AUDIT", module = "COMPANY_MANAGEMENT", description = "批准企业注册申请")
    public Result approveCompanyQualification(@PathVariable Long id, @RequestParam @NotNull Long reviewerId) {
        log.info("批准企业注册申请: id={}, reviewerId={}", id, reviewerId);
        try {
            CompanyUser company = companyUserService.findById(id);
            if (company == null) {
                return Result.error("企业不存在");
            }
            company.setAuditStatus(1);
            company.setAuditTime(new Date());
            company.setReviewerId(reviewerId);
            int result = companyUserService.update(company);
            if (result > 0) {
                return Result.success("企业注册申请已通过");
            }
            return Result.error("批准失败");
        } catch (Exception e) {
            log.error("批准企业注册申请失败: {}", e.getMessage(), e);
            return Result.error("批准企业注册申请失败: " + e.getMessage());
        }
    }

    /**
     * 驳回企业注册申请
     */
    @PostMapping("/company-qualifications/{id}/reject")
    @Log(operationType = "AUDIT", module = "COMPANY_MANAGEMENT", description = "驳回企业注册申请")
    public Result rejectCompanyQualification(
            @PathVariable Long id,
            @RequestParam @NotNull Long reviewerId,
            @RequestParam @NotNull String rejectReason) {
        log.info("驳回企业注册申请: id={}, reviewerId={}, reason={}", id, reviewerId, rejectReason);
        try {
            CompanyUser company = companyUserService.findById(id);
            if (company == null) {
                return Result.error("企业不存在");
            }
            company.setAuditStatus(2);
            company.setAuditTime(new Date());
            company.setReviewerId(reviewerId);
            company.setAuditRemark(rejectReason);
            int result = companyUserService.update(company);
            if (result > 0) {
                return Result.success("企业注册申请已驳回");
            }
            return Result.error("驳回失败");
        } catch (Exception e) {
            log.error("驳回企业注册申请失败: {}", e.getMessage(), e);
            return Result.error("驳回企业注册申请失败: " + e.getMessage());
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
        try {
            Map<String, Object> result = new HashMap<>();

            if ("companyQualification".equals(type)) {
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
                result.put("data", companyResult);
            } else {
                PageResult<StudentApplication> studentResult = studentApplicationService.findPage(
                        page, pageSize, type, status, keyword, null);
                result.put("type", type);
                result.put("data", studentResult);
            }

            return Result.success(result);
        } catch (Exception e) {
            log.error("获取综合审核列表失败: {}", e.getMessage(), e);
            return Result.error("获取综合审核列表失败: " + e.getMessage());
        }
    }
}