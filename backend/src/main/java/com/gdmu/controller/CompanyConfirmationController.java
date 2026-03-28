package com.gdmu.controller;

import com.gdmu.entity.InternshipConfirmationRecord;
import com.gdmu.entity.InternshipProgressRecord;
import com.gdmu.entity.Result;
import com.gdmu.entity.StudentInternshipStatus;
import com.gdmu.entity.User;
import com.gdmu.service.InternshipConfirmationRecordService;
import com.gdmu.service.InternshipProgressRecordService;
import com.gdmu.service.StudentInternshipStatusService;
import com.gdmu.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/company/confirmation")
@PreAuthorize("hasRole('COMPANY')")
public class CompanyConfirmationController {

    @Autowired
    private UserService userService;

    @Autowired
    private InternshipConfirmationRecordService confirmationRecordService;

    @Autowired
    private StudentInternshipStatusService internshipStatusService;

    @Autowired
    private InternshipProgressRecordService progressRecordService;

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof org.springframework.security.core.userdetails.User)) {
            return null;
        }
        String username = ((org.springframework.security.core.userdetails.User) auth.getPrincipal()).getUsername();
        return userService.findByUsername(username);
    }

    /**
     * 获取待确认的实习确认记录列表
     */
    @GetMapping("/pending")
    public Result getPendingConfirmations() {
        try {
            User user = getCurrentUser();
            if (user == null) return Result.error("未登录");

            Long companyId = user.getId();
            // 查询该公司下的所有待确认(status=0)的记录
            List<InternshipConfirmationRecord> records = confirmationRecordService.findByCompanyIdAndStatus(companyId, 0);

            return Result.success(records);
        } catch (Exception e) {
            log.error("获取待确认记录失败: {}", e.getMessage(), e);
            return Result.error("获取失败");
        }
    }

    /**
     * 获取所有实习确认记录（不分状态）
     */
    @GetMapping("/all")
    public Result getAllConfirmations() {
        try {
            User user = getCurrentUser();
            if (user == null) return Result.error("未登录");

            Long companyId = user.getId();
            // 查询该公司下的所有记录
            List<InternshipConfirmationRecord> records = confirmationRecordService.findByCompanyId(companyId);

            return Result.success(records);
        } catch (Exception e) {
            log.error("获取确认记录失败: {}", e.getMessage(), e);
            return Result.error("获取失败");
        }
    }

    /**
     * 确认实习记录
     */
    @PostMapping("/confirm/{id}")
    public Result confirm(@PathVariable Long id) {
        try {
            User user = getCurrentUser();
            if (user == null) return Result.error("未登录");

            InternshipConfirmationRecord record = confirmationRecordService.findById(id);
            if (record == null) {
                return Result.error("记录不存在");
            }

            // 验证是否为该公司的记录
            if (!user.getId().equals(record.getCompanyId())) {
                return Result.error("无权操作此记录");
            }

            // 更新确认记录状态为已确认(1)
            record.setStatus(1);
            confirmationRecordService.update(record);

            // 同步更新 student_internship_status（当前实习状态）
            StudentInternshipStatus status = internshipStatusService.findByStudentId(record.getStudentId());
            if (status == null) {
                status = new StudentInternshipStatus();
                status.setStudentId(record.getStudentId());
                status.setCreateTime(new java.util.Date());
            }
            // 更新实习状态为已确定(2)
            status.setStatus(2);
            // 更新公司信息
            status.setCompanyId(record.getCompanyId());
            status.setCompanyName(record.getCompanyName());
            status.setPositionId(record.getPositionId());
            status.setPositionName(record.getPositionName());
            status.setCompanyAddress(record.getCompanyAddress());
            status.setCompanyPhone(record.getCompanyPhone());
            // 更新实习时间（LocalDateTime转换为Date）
            if (record.getInternshipStartTime() != null) {
                status.setInternshipStartTime(Date.from(record.getInternshipStartTime().atZone(java.time.ZoneId.systemDefault()).toInstant()));
            }
            if (record.getInternshipEndTime() != null) {
                status.setInternshipEndTime(Date.from(record.getInternshipEndTime().atZone(java.time.ZoneId.systemDefault()).toInstant()));
            }
            status.setInternshipDuration(record.getInternshipDuration());
            // 重置企业确认状态
            status.setCompanyConfirmStatus(1);

            if (status.getId() == null) {
                internshipStatusService.insert(status);
            } else {
                internshipStatusService.update(status);
            }

            // 更新实习进展记录状态为已确认
            progressRecordService.updateStatusByRelatedId(record.getId(), "internship_confirmation", "success");

            return Result.success("确认成功");
        } catch (Exception e) {
            log.error("确认失败: {}", e.getMessage(), e);
            return Result.error("确认失败");
        }
    }

    /**
     * 拒绝实习记录
     */
    @PostMapping("/reject/{id}")
    public Result reject(@PathVariable Long id, @RequestParam String reason) {
        try {
            User user = getCurrentUser();
            if (user == null) return Result.error("未登录");

            InternshipConfirmationRecord record = confirmationRecordService.findById(id);
            if (record == null) {
                return Result.error("记录不存在");
            }

            // 验证是否为该公司的记录
            if (!user.getId().equals(record.getCompanyId())) {
                return Result.error("无权操作此记录");
            }

            // 更新状态为已拒绝(2)，备注存储拒绝原因
            record.setStatus(2);
            record.setRemark(reason);
            confirmationRecordService.update(record);

            // 更新实习进展记录状态为已拒绝
            progressRecordService.updateStatusByRelatedId(record.getId(), "internship_confirmation", "failed");

            return Result.success("已拒绝");
        } catch (Exception e) {
            log.error("拒绝失败: {}", e.getMessage(), e);
            return Result.error("拒绝失败");
        }
    }
}
