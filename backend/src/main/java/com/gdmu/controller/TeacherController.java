package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.entity.User;
import com.gdmu.entity.GroupTable;
import com.gdmu.entity.Assignment;
import com.gdmu.entity.Submission;
import com.gdmu.entity.FinalScore;
import com.gdmu.entity.GroupMember;
import com.gdmu.entity.Class;
import com.gdmu.entity.Course;
import com.gdmu.entity.Result;
import com.gdmu.entity.CompanyUser;
import com.gdmu.entity.PageResult;
import com.gdmu.exception.BusinessException;
import com.gdmu.service.*;
import com.gdmu.service.MajorService;
import com.gdmu.service.CompanyUserService;
import com.gdmu.utils.CurrentHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 教师控制器
 * 处理教师相关的所有接口请求，包括实习管理、成绩管理、学生管理等功能
 */
@Slf4j
@RestController
@RequestMapping("/api/teacher")
@PreAuthorize("hasRole('TEACHER')")
@Validated
public class TeacherController {

    @Autowired
    private UserService userService;

    @Autowired
    private ClassService classService;

    @Autowired
    private MajorService majorService;

    @Autowired
    private CompanyUserService companyUserService;

    @GetMapping("/companies/audit/pending")
    @PreAuthorize("hasAuthority('user:company:audit')")
    public Result getPendingAuditCompanies(@RequestParam(defaultValue = "1") Integer page,
                                           @RequestParam(defaultValue = "10") Integer pageSize,
                                           @RequestParam(required = false) String companyName,
                                           @RequestParam(required = false) String contactPerson,
                                           @RequestParam(required = false) String contactPhone) {
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        log.info("当前用户: {}, 权限列表: {}", authentication.getName(), authentication.getAuthorities());
        
        log.info("获取待审核企业列表，页码: {}, 每页条数: {}, 企业名称: {}, 联系人: {}, 联系电话: {}", 
                page, pageSize, companyName, contactPerson, contactPhone);
        try {
            PageResult<CompanyUser> pageResult = companyUserService.findPendingAuditPage(page, pageSize, companyName, contactPerson, contactPhone);
            return Result.success(pageResult);
        } catch (Exception e) {
            log.error("获取待审核企业列表失败: {}", e.getMessage(), e);
            return Result.error("获取待审核企业列表失败");
        }
    }

    @GetMapping("/companies/statistics")
    @PreAuthorize("hasAuthority('user:company:audit')")
    public Result getCompanyStatistics() {
        log.info("获取企业审核统计数据");
        try {
            Map<String, Object> statistics = new HashMap<>();
            statistics.put("pending", companyUserService.countByAuditStatus(0));
            statistics.put("approved", companyUserService.countByAuditStatus(1));
            statistics.put("rejected", companyUserService.countByAuditStatus(2));
            statistics.put("total", companyUserService.count());
            return Result.success(statistics);
        } catch (Exception e) {
            log.error("获取统计数据失败: {}", e.getMessage(), e);
            return Result.error("获取统计数据失败");
        }
    }

    @PutMapping("/companies/{id}/audit")
    @PreAuthorize("hasAuthority('user:company:audit')")
    @Log(operationType = "AUDIT", module = "COMPANY_MANAGEMENT", description = "审核企业注册申请")
    public Result auditCompany(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        log.info("审核企业注册，ID: {}, 审核参数: {}", id, params);
        try {
            Integer auditStatus = Integer.valueOf(params.get("auditStatus").toString());
            String auditRemark = params.get("auditRemark") != null ? params.get("auditRemark").toString() : "";
            
            CompanyUser company = companyUserService.findById(id);
            if (company == null) {
                return Result.error("企业不存在");
            }
            
            company.setAuditStatus(auditStatus);
            company.setAuditRemark(auditRemark);
            company.setAuditTime(new Date());
            company.setUpdateTime(new Date());
            
            if (auditStatus == 1) {
                company.setStatus(1);
                company.setReviewerId(getCurrentUserId());
                
                String username = company.getCompanyName();
                String password = "123456";
                company.setUsername(username);
                company.setPassword(password);
                company.setRole("ROLE_COMPANY");
                
                companyUserService.update(company);
                
                return Result.success("审核通过");
            } else if (auditStatus == 2) {
                company.setStatus(3);
                company.setReviewerId(getCurrentUserId());
                
                companyUserService.update(company);
                
                return Result.success("审核拒绝");
            } else {
                return Result.error("无效的审核状态");
            }
        } catch (BusinessException e) {
            log.warn("审核企业注册失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("审核企业注册时发生异常: {}", e.getMessage(), e);
            return Result.error("审核失败: " + e.getMessage());
        }
    }

    private Long getCurrentUserId() {
        Long userId = CurrentHolder.getUserId();
        if (userId == null) {
            log.warn("未获取到当前登录用户 ID，返回默认值");
            return 1L; // 默认值，仅用于兼容
        }
        log.debug("获取到当前登录用户 ID: {}", userId);
        return userId;
    }

}