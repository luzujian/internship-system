package com.gdmu.controller;

import com.gdmu.entity.CompanyQualification;
import com.gdmu.entity.PageResult;
import com.gdmu.entity.Result;
import com.gdmu.entity.StudentApplication;
import com.gdmu.service.CompanyQualificationService;
import com.gdmu.service.CompanyUserService;
import com.gdmu.service.StudentApplicationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;

/**
 * 审核管理控制器
 * 处理学生申请和企业资质审核的所有接口请求
 */
@Slf4j
@RestController
@RequestMapping("/api/approval")
@PreAuthorize("hasAnyRole('TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR')")
public class ApprovalController {

    @Autowired
    private StudentApplicationService studentApplicationService;

    @Autowired
    private CompanyQualificationService companyQualificationService;

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
     * 获取企业资质审核列表
     */
    @GetMapping("/company-qualifications")
    public Result getCompanyQualifications(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String companyName) {
        log.info("获取企业资质审核列表: page={}, pageSize={}, status={}, companyName={}", 
                page, pageSize, status, companyName);
        try {
            PageResult<CompanyQualification> result = companyQualificationService.findPage(
                    page, pageSize, status, companyName);
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取企业资质审核列表失败: {}", e.getMessage(), e);
            return Result.error("获取企业资质审核列表失败: " + e.getMessage());
        }
    }

    /**
     * 根据ID获取企业资质审核详情
     */
    @GetMapping("/company-qualifications/{id}")
    public Result getCompanyQualificationById(@PathVariable Long id) {
        log.info("获取企业资质审核详情: id={}", id);
        try {
            CompanyQualification qualification = companyQualificationService.findById(id);
            if (qualification == null) {
                return Result.error("资质审核不存在");
            }
            return Result.success(qualification);
        } catch (Exception e) {
            log.error("获取企业资质审核详情失败: {}", e.getMessage(), e);
            return Result.error("获取企业资质审核详情失败: " + e.getMessage());
        }
    }

    /**
     * 批准企业资质审核
     */
    @PostMapping("/company-qualifications/{id}/approve")
    public Result approveCompanyQualification(@PathVariable Long id, @RequestParam @NotNull Long reviewerId) {
        log.info("批准企业资质审核: id={}, reviewerId={}", id, reviewerId);
        try {
            int result = companyQualificationService.approve(id, reviewerId);
            if (result > 0) {
                return Result.success("资质审核已成功通过");
            }
            return Result.error("批准资质审核失败");
        } catch (Exception e) {
            log.error("批准企业资质审核失败: {}", e.getMessage(), e);
            return Result.error("批准资质审核失败: " + e.getMessage());
        }
    }

    /**
     * 驳回企业资质审核
     */
    @PostMapping("/company-qualifications/{id}/reject")
    public Result rejectCompanyQualification(
            @PathVariable Long id,
            @RequestParam @NotNull Long reviewerId,
            @RequestParam @NotNull String rejectReason) {
        log.info("驳回企业资质审核: id={}, reviewerId={}, reason={}", id, reviewerId, rejectReason);
        try {
            int result = companyQualificationService.reject(id, reviewerId, rejectReason);
            if (result > 0) {
                return Result.success("资质审核已成功驳回");
            }
            return Result.error("驳回资质审核失败");
        } catch (Exception e) {
            log.error("驳回企业资质审核失败: {}", e.getMessage(), e);
            return Result.error("驳回资质审核失败: " + e.getMessage());
        }
    }

    /**
     * 获取综合审核列表（包含学生申请和企业资质）
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
                PageResult<CompanyQualification> companyResult = companyQualificationService.findPage(
                        page, pageSize, status, keyword);
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
