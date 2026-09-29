package com.internship.controller;

import com.internship.anno.Log;
import com.internship.entity.Result;
import com.internship.entity.StudentApplication;
import com.internship.entity.StudentUser;
import com.internship.entity.User;
import com.internship.service.StudentApplicationService;
import com.internship.service.StudentUserService;
import com.internship.service.UserService;
import com.internship.utils.AliyunOSSOperator;
import com.internship.utils.RecordAttachmentResponse;
import com.internship.utils.MaterialMetadataRedactor;
import com.internship.utils.StudentProfileCompleteness;
import com.internship.mapper.CompanyUserMapper;
import com.internship.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 学生申请控制器
 * 处理学生自主实习申请、单位变更申请、考研延迟申请等
 */
@Slf4j
@RestController
@RequestMapping("/api/student/applications")
@PreAuthorize("hasRole('STUDENT')")
public class StudentApplicationController {

    @Autowired
    private UserService userService;

    @Autowired
    private StudentUserService studentUserService;

    @Autowired
    private StudentApplicationService studentApplicationService;

    @Autowired
    private CompanyUserMapper companyUserMapper;

    @Autowired
    private AliyunOSSOperator aliyunOSSOperator;

    @GetMapping("/company-matches")
    public Result getCooperativeCompanyMatches(@RequestParam String keyword) {
        if (StringUtils.isBlank(keyword) || keyword.trim().length() < 2) {
            return Result.success(List.of());
        }

        List<Map<String, Object>> matches = companyUserMapper
                .findApprovedCompanyMatches(keyword.trim())
                .stream()
                .map(company -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("companyId", company.getId());
                    item.put("companyName", company.getCompanyName());
                    item.put("tag", "校企合作");
                    return item;
                })
                .toList();
        return Result.success(matches);
    }

    /**
     * 获取当前登录用户
     */
    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof org.springframework.security.core.userdetails.User)) {
            return null;
        }
        String username = ((org.springframework.security.core.userdetails.User) auth.getPrincipal()).getUsername();
        return userService.findByUsername(username);
    }

    /**
     * 创建学生申请（自主实习/单位变更/考研延迟）
     */
    @PostMapping
    @Log(operationType = "ADD", module = "INTERNSHIP_MANAGEMENT", description = "提交自主实习/单位变更/考研延迟申请")
    public Result createApplication(@RequestBody StudentApplication application) {
        try {
            User user = getCurrentUser();
            if (user == null) {
                return Result.error("未登录");
            }

            StudentUser studentUser = studentUserService.findById(user.getId());
            if (studentUser == null) {
                return Result.error("学生信息不存在");
            }

            // 自主实习审核/考研延迟申请前置校验：姓名、学号、电话、邮箱必须全部完善
            if ("selfPractice".equals(application.getApplicationType())
                    || "delay".equals(application.getApplicationType())) {
                List<String> profileMissing = StudentProfileCompleteness.missingRequiredFields(studentUser);
                if (!profileMissing.isEmpty()) {
                    return Result.error(StudentProfileCompleteness.incompleteMessage(profileMissing));
                }
            }

            // 设置申请基本信息
            application.setStudentId(user.getId());
            application.setStudentName(studentUser.getName());
            application.setStudentUserId(studentUser.getStudentId());
            application.setGrade(String.valueOf(studentUser.getGrade()));
            application.setClassName(studentUser.getClassName());
            application.setStatus("pending");
            application.setApplyTime(new Date());

            if (application.getMaterials() != null) {
                for (String storedUrl : application.getMaterials().values()) {
                    aliyunOSSOperator.validateRecordAttachment(storedUrl, user.getId());
                }
            }

            // 根据申请类型设置特定字段
            if ("selfPractice".equals(application.getApplicationType())) {
                // 自主实习申请
                if (StringUtils.isBlank(application.getCompany())) {
                    return Result.error("请填写实习单位");
                }
                if (StringUtils.isBlank(application.getPositionName())) {
                    return Result.error("请填写实习岗位");
                }
                if (StringUtils.isBlank(application.getCompanyCity())) {
                    return Result.error("请填写单位所在城市");
                }
                if (StringUtils.isBlank(application.getHrManagerName())) {
                    return Result.error("请填写人事经理姓名");
                }
                if (StringUtils.isBlank(application.getHrManagerPhone())) {
                    return Result.error("请填写人事经理联系电话");
                }
                application.setCompany(application.getCompany().trim());
                application.setPositionName(application.getPositionName().trim());
                application.setCompanyCity(application.getCompanyCity().trim());
                application.setHrManagerName(application.getHrManagerName().trim());
                application.setHrManagerPhone(application.getHrManagerPhone().trim());
            } else if ("unitChange".equals(application.getApplicationType())) {
                // 单位变更申请
                if (application.getOldCompany() == null || application.getOldCompany().isEmpty()) {
                    return Result.error("请填写原实习单位");
                }
            } else if ("delay".equals(application.getApplicationType())) {
                // 考研延迟申请 - materials字段用于存储考研计划、学习计划、延迟申请书等
                if (application.getMaterials() == null || application.getMaterials().isEmpty()) {
                    return Result.error("请上传相关材料");
                }
            }

            // 验证申请理由
            if (StringUtils.isBlank(application.getReason())) {
                return Result.error("请填写申请理由");
            }
            application.setReason(application.getReason().trim());

            int result = studentApplicationService.insert(application);
            if (result > 0) {
                log.info("学生申请提交成功: studentId={}, type={}", user.getId(), application.getApplicationType());
                return Result.success("申请提交成功");
            } else {
                return Result.error("申请提交失败");
            }
        } catch (BusinessException e) {
            log.warn("提交申请失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("提交申请失败: {}", e.getMessage(), e);
            return Result.error("提交申请失败: " + e.getMessage());
        }
    }

    /**
     * 获取当前学生的申请列表
     */
    @GetMapping
    public Result getMyApplications() {
        try {
            User user = getCurrentUser();
            if (user == null) {
                return Result.error("未登录");
            }

            var applications = studentApplicationService.findByStudentId(user.getId());
            return Result.success(applications == null ? List.of()
                    : applications.stream().map(MaterialMetadataRedactor::studentApplication).toList());
        } catch (Exception e) {
            log.error("获取申请列表失败: {}", e.getMessage(), e);
            return Result.error("获取申请列表失败: " + e.getMessage());
        }
    }

    /**
     * 根据ID获取申请详情
     */
    @GetMapping("/{id}")
    public Result getApplicationById(@PathVariable Long id) {
        try {
            User user = getCurrentUser();
            if (user == null) {
                return Result.error("未登录");
            }

            StudentApplication application = studentApplicationService.findById(id);
            if (application == null) {
                return Result.error("申请不存在");
            }

            // 只能查看自己的申请
            if (!application.getStudentId().equals(user.getId())) {
                throw new AccessDeniedException("无权查看此申请");
            }

            return Result.success(MaterialMetadataRedactor.studentApplication(application));
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取申请详情失败: {}", e.getMessage(), e);
            return Result.error("获取申请详情失败: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/withdraw")
    @Log(operationType = "UPDATE", module = "INTERNSHIP_MANAGEMENT", description = "撤回待审核的自主实习/考研延迟申请")
    public Result withdrawApplication(@PathVariable Long id) {
        try {
            User user = getCurrentUser();
            if (user == null) {
                return Result.error("未登录");
            }
            int result = studentApplicationService.withdrawApplication(id, user.getId());
            if (result > 0) {
                return Result.success("申请已撤回，可重新提交");
            }
            return Result.error("撤回申请失败");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("撤回申请失败: {}", e.getMessage(), e);
            return Result.error("撤回申请失败");
        }
    }

    @PutMapping("/{id}/resubmit")
    @Log(operationType = "UPDATE", module = "INTERNSHIP_MANAGEMENT", description = "重新提交被驳回或已撤回的自主实习/考研延迟申请")
    public Result resubmitApplication(@PathVariable Long id, @RequestBody StudentApplication application) {
        try {
            User user = getCurrentUser();
            if (user == null) {
                return Result.error("未登录");
            }
            // 重新提交同样要求个人资料（姓名、学号、电话、邮箱）已完善
            List<String> profileMissing = StudentProfileCompleteness
                    .missingRequiredFields(studentUserService.findById(user.getId()));
            if (!profileMissing.isEmpty()) {
                return Result.error(StudentProfileCompleteness.incompleteMessage(profileMissing));
            }
            if (application.getMaterials() != null) {
                for (String storedUrl : application.getMaterials().values()) {
                    if (StringUtils.isNotBlank(storedUrl)) {
                        aliyunOSSOperator.validateRecordAttachment(storedUrl, user.getId());
                    }
                }
            }
            int result = studentApplicationService.resubmit(id, user.getId(), application);
            if (result > 0) {
                return Result.success("申请已重新提交，等待教师审核");
            }
            return Result.error("重新提交申请失败");
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("重新提交申请失败: {}", e.getMessage(), e);
            return Result.error("重新提交申请失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}/materials/{key}")
    @Log(operationType = "DOWNLOAD", module = "INTERNSHIP_MANAGEMENT", description = "下载本人申请材料", riskLevel = "MEDIUM")
    public ResponseEntity<byte[]> downloadMyApplicationMaterial(@PathVariable Long id, @PathVariable String key) {
        User actor = getCurrentUser();
        if (actor == null || actor.getId() == null) return ResponseEntity.status(401).build();
        StudentApplication application = studentApplicationService.findById(id);
        if (application == null) return ResponseEntity.notFound().build();
        if (!actor.getId().equals(application.getStudentId())) return ResponseEntity.status(403).build();
        String storedUrl = application.getMaterials() == null ? null : application.getMaterials().get(key);
        if (storedUrl == null || storedUrl.isBlank()) return ResponseEntity.notFound().build();
        try {
            return RecordAttachmentResponse.ok(aliyunOSSOperator.downloadRecordAttachment(storedUrl, actor.getId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(403).build();
        } catch (Exception e) {
            log.error("读取学生申请材料失败: applicationId={}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
