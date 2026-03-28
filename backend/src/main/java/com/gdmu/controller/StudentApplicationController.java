package com.gdmu.controller;

import com.gdmu.entity.InternshipProgressRecord;
import com.gdmu.entity.Result;
import com.gdmu.entity.StudentApplication;
import com.gdmu.entity.StudentUser;
import com.gdmu.entity.User;
import com.gdmu.service.InternshipProgressRecordService;
import com.gdmu.service.StudentApplicationService;
import com.gdmu.service.StudentUserService;
import com.gdmu.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.HashMap;
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
    private InternshipProgressRecordService progressRecordService;

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

            // 设置申请基本信息
            application.setStudentId(user.getId());
            application.setStudentName(studentUser.getName());
            application.setStudentUserId(studentUser.getStudentId());
            application.setGrade(String.valueOf(studentUser.getGrade()));
            application.setClassName(studentUser.getClassName());
            application.setStatus("pending");
            application.setApplyTime(new Date());

            // 根据申请类型设置特定字段
            if ("selfPractice".equals(application.getApplicationType())) {
                // 自主实习申请
                if (application.getCompany() == null || application.getCompany().isEmpty()) {
                    return Result.error("请填写实习单位");
                }
            } else if ("unitChange".equals(application.getApplicationType())) {
                // 单位变更申请
                if (application.getOldCompany() == null || application.getOldCompany().isEmpty()) {
                    return Result.error("请填写原实习单位");
                }
                if (application.getNewCompany() == null || application.getNewCompany().isEmpty()) {
                    return Result.error("请填写新实习单位");
                }
            } else if ("delay".equals(application.getApplicationType())) {
                // 考研延迟申请 - materials字段用于存储考研计划、学习计划、延迟申请书等
                if (application.getMaterials() == null || application.getMaterials().isEmpty()) {
                    return Result.error("请上传相关材料");
                }
            }

            // 验证申请理由
            if (application.getReason() == null || application.getReason().isEmpty()) {
                return Result.error("请填写申请理由");
            }

            int result = studentApplicationService.insert(application);
            if (result > 0) {
                // 同步写入进度记录
                InternshipProgressRecord record = new InternshipProgressRecord();
                record.setStudentId(user.getId());
                String eventType = application.getApplicationType();
                record.setEventType(eventType);
                // 设置事件标题
                if ("selfPractice".equals(eventType)) {
                    record.setEventTitle("自主实习申请");
                    record.setDescription(application.getCompany());
                } else if ("delay".equals(eventType)) {
                    record.setEventTitle("考研延迟申请");
                    record.setDescription("考研延迟申请材料");
                }
                record.setStatus("pending");
                record.setRelatedId(application.getId());
                record.setEventTime(new Date());
                progressRecordService.saveRecord(record);

                log.info("学生申请提交成功: studentId={}, type={}", user.getId(), application.getApplicationType());
                return Result.success("申请提交成功");
            } else {
                return Result.error("申请提交失败");
            }
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
            return Result.success(applications);
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
                return Result.error("无权查看此申请");
            }

            return Result.success(application);
        } catch (Exception e) {
            log.error("获取申请详情失败: {}", e.getMessage(), e);
            return Result.error("获取申请详情失败: " + e.getMessage());
        }
    }
}
