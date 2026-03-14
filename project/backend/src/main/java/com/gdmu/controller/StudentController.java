package com.gdmu.controller;

import com.gdmu.entity.*;
import com.gdmu.entity.Result;
import com.gdmu.service.*;
import com.gdmu.utils.AliyunOSSOperator;
import com.gdmu.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 学生控制器
 * 处理学生相关的所有接口请求，包括作业查看、作业提交、小组管理、成绩查看等功能
 */
@Slf4j
@RestController
@RequestMapping("/api/student")
@PreAuthorize("hasRole('STUDENT')")
@Validated
public class StudentController {

    @Autowired
    private UserService userService;

    @Autowired
    private AliyunOSSOperator aliyunOSSOperator;

    @Autowired
    private StudentInternshipStatusService studentInternshipStatusService;

    @Autowired
    private AIRecallAuditService aiRecallAuditService;

    // 获取学生信息
    @GetMapping("/info")
    @PreAuthorize("hasRole('STUDENT')")
    public Result getStudentInfo(@RequestParam @NotNull(message = "学生ID不能为空") Long studentId) {
        log.info("获取学生信息: {}", studentId);
        try {
            User student = userService.findById(studentId);
            if (student == null) {
                return Result.error("学生不存在");
            }
            
            // 验证是否为学生角色
            if (!"ROLE_STUDENT".equals(student.getRole())) {
                return Result.error("该用户不是学生角色");
            }
            
            // 返回学生基本信息
            Map<String, Object> studentInfo = new HashMap<>();
            studentInfo.put("id", student.getId());
            studentInfo.put("name", student.getName());
            studentInfo.put("username", student.getUsername());
            studentInfo.put("role", student.getRole());
            
            return Result.success(studentInfo);
        } catch (Exception e) {
            log.error("获取学生信息失败: {}", e.getMessage(), e);
            return Result.error("获取学生信息失败: " + e.getMessage());
        }
    }

    @GetMapping("/internship-status")
    @PreAuthorize("hasRole('STUDENT')")
    public Result getMyInternshipStatus() {
        log.info("获取当前学生的实习状态");
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !(authentication.getPrincipal() instanceof org.springframework.security.core.userdetails.User)) {
                return Result.error("未登录或登录状态异常");
            }
            
            String username = ((org.springframework.security.core.userdetails.User) authentication.getPrincipal()).getUsername();
            User user = userService.findByUsername(username);
            if (user == null) {
                return Result.error("用户不存在");
            }
            
            StudentInternshipStatus status = studentInternshipStatusService.findByStudentId(user.getId());
            return Result.success(status);
        } catch (Exception e) {
            log.error("获取实习状态失败: {}", e.getMessage(), e);
            return Result.error("获取实习状态失败: " + e.getMessage());
        }
    }

    @PutMapping("/internship-status/{id}/recall")
    @PreAuthorize("hasRole('STUDENT')")
    public Result submitRecallApplication(@PathVariable Long id, @RequestBody Map<String, String> params) {
        log.info("学生提交撤回申请，ID: {}", id);
        try {
            String recallReason = params.get("recallReason");
            if (recallReason == null || recallReason.trim().isEmpty()) {
                return Result.error("撤回原因不能为空");
            }
            
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !(authentication.getPrincipal() instanceof org.springframework.security.core.userdetails.User)) {
                return Result.error("未登录或登录状态异常");
            }
            
            String username = ((org.springframework.security.core.userdetails.User) authentication.getPrincipal()).getUsername();
            User user = userService.findByUsername(username);
            if (user == null) {
                return Result.error("用户不存在");
            }
            
            StudentInternshipStatus status = studentInternshipStatusService.findById(id);
            if (status == null) {
                return Result.error("实习状态不存在");
            }
            
            if (!status.getStudentId().equals(user.getId())) {
                return Result.error("只能撤回自己的实习确认");
            }
            
            int result = studentInternshipStatusService.submitRecallApplication(id, recallReason);
            if (result > 0) {
                if (aiRecallAuditService.isAIStudentRecallAuditEnabled()) {
                    final Long statusId = id;
                    final String finalRecallReason = recallReason;
                    
                    CompletableFuture.runAsync(() -> {
                        try {
                            log.info("开始异步AI审核学生撤回申请，实习状态ID: {}", statusId);
                            Map<String, Object> auditResult = aiRecallAuditService.autoAuditStudentRecall(statusId, finalRecallReason);
                            
                            String auditDecision = (String) auditResult.get("auditDecision");
                            Boolean approved = (Boolean) auditResult.get("approved");
                            
                            if ("APPROVED".equals(auditDecision) && Boolean.TRUE.equals(approved)) {
                                log.info("AI自动审核通过学生撤回申请，实习状态ID: {}", statusId);
                                studentInternshipStatusService.auditRecallApplication(statusId, 2, null, null);
                            } else if ("REJECTED".equals(auditDecision) && Boolean.FALSE.equals(approved)) {
                                log.info("AI自动审核拒绝学生撤回申请，实习状态ID: {}", statusId);
                                studentInternshipStatusService.auditRecallApplication(statusId, 3, null, null);
                            } else {
                                StudentInternshipStatus statusToUpdate = studentInternshipStatusService.findById(statusId);
                                if (statusToUpdate != null) {
                                    statusToUpdate.setRecallReviewerId(-1L);
                                    studentInternshipStatusService.update(statusToUpdate);
                                }
                                log.info("AI审核转人工审核，实习状态ID: {}", statusId);
                            }
                        } catch (Exception e) {
                            log.error("AI审核学生撤回申请失败，转人工审核，实习状态ID: {}", statusId, e);
                        }
                    });
                    
                    return Result.success("撤回申请已提交，AI正在审核中，请稍后查看结果");
                }
                return Result.success("撤回申请已提交，请等待管理员审核");
            } else {
                return Result.error("提交撤回申请失败");
            }
        } catch (BusinessException e) {
            log.warn("提交撤回申请失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("提交撤回申请时发生异常: {}", e.getMessage(), e);
            return Result.error("提交撤回申请失败: " + e.getMessage());
        }
    }
}