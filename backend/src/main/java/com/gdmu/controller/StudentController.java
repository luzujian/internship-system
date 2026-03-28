package com.gdmu.controller;

import com.gdmu.anno.Log;
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
    private StudentUserService studentUserService;

    @Autowired
    private InternshipTimeSettingsService internshipTimeSettingsService;

    @Autowired
    private PositionService positionService;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    // 获取学生信息
    @GetMapping("/info")
    @PreAuthorize("hasRole('STUDENT')")
    public Result getStudentInfo(@RequestParam @NotNull(message = "学生 ID 不能为空") Long studentId) {
        log.info("获取学生信息：{}", studentId);
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
            log.error("获取学生信息失败：{}", e.getMessage(), e);
            return Result.error("获取学生信息失败：" + e.getMessage());
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
            if (status != null) {
                // 优先使用岗位设置的实习时间，其次使用系室默认设置
                String startDateStr = null;
                String endDateStr = null;

                // 1. 先尝试获取岗位设置的实习时间
                if (status.getPositionId() != null) {
                    Position position = positionService.findById(status.getPositionId());
                    if (position != null && position.getInternshipStartDate() != null && position.getInternshipEndDate() != null) {
                        startDateStr = null; // flag that position has dates
                        endDateStr = null;
                    }
                }

                // 如果学生没有设置实习时间，才使用岗位或系室设置
                if (status.getInternshipStartTime() == null || status.getInternshipEndTime() == null) {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");

                    // 1. 先尝试获取岗位设置的实习时间
                    if (status.getPositionId() != null) {
                        Position position = positionService.findById(status.getPositionId());
                        if (position != null && position.getInternshipStartDate() != null && position.getInternshipEndDate() != null) {
                            if (status.getInternshipStartTime() == null) {
                                status.setInternshipStartTime(position.getInternshipStartDate());
                            }
                            if (status.getInternshipEndTime() == null) {
                                status.setInternshipEndTime(position.getInternshipEndDate());
                            }
                        }
                    }

                    // 2. 如果岗位没有设置，使用系室默认设置
                    if (status.getInternshipStartTime() == null || status.getInternshipEndTime() == null) {
                        InternshipTimeSettings defaultSettings = internshipTimeSettingsService.findLatest();
                        if (status.getInternshipStartTime() == null && defaultSettings != null && defaultSettings.getStartDate() != null) {
                            try {
                                status.setInternshipStartTime(sdf.parse(defaultSettings.getStartDate()));
                            } catch (Exception e) {
                                log.warn("解析系室默认开始时间失败: {}", e.getMessage());
                            }
                        }
                        if (status.getInternshipEndTime() == null && defaultSettings != null && defaultSettings.getEndDate() != null) {
                            try {
                                status.setInternshipEndTime(sdf.parse(defaultSettings.getEndDate()));
                            } catch (Exception e) {
                                log.warn("解析系室默认结束时间失败: {}", e.getMessage());
                            }
                        }
                    }
                }
            }
            return Result.success(status);
        } catch (Exception e) {
            log.error("获取实习状态失败：{}", e.getMessage(), e);
            return Result.error("获取实习状态失败：" + e.getMessage());
        }
    }

    @PutMapping("/internship-status/{id}/recall")
    @PreAuthorize("hasRole('STUDENT')")
    @Log(operationType = "UPDATE", module = "INTERNSHIP_MANAGEMENT", description = "学生提交撤回实习确认申请")
    public Result submitRecallApplication(@PathVariable Long id, @RequestBody Map<String, String> params) {
        log.info("学生提交撤回申请（自动撤回），ID: {}", id);
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

            // 使用自动撤回模式，同时创建撤回记录
            int result = studentInternshipStatusService.submitRecallApplicationWithRecord(id, recallReason, user.getId(), "ROLE_STUDENT");
            if (result > 0) {
                return Result.success("撤回成功");
            } else {
                return Result.error("撤回失败");
            }
        } catch (BusinessException e) {
            log.warn("提交撤回申请失败：{}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("提交撤回申请时发生异常：{}", e.getMessage(), e);
            return Result.error("提交撤回申请失败：" + e.getMessage());
        }
    }

    // ==================== 学生个人中心接口 ====================

    @GetMapping("/profile")
    public Result getProfile() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !(authentication.getPrincipal() instanceof org.springframework.security.core.userdetails.User)) {
                return Result.error("未登录");
            }
            String username = ((org.springframework.security.core.userdetails.User) authentication.getPrincipal()).getUsername();
            User user = userService.findByUsername(username);
            if (user == null) return Result.error("用户不存在");

            StudentUser studentUser = studentUserService.findById(user.getId());
            if (studentUser == null) return Result.error("学生信息不存在");

            Map<String, Object> profile = new HashMap<>();
            profile.put("id", studentUser.getId());
            profile.put("name", studentUser.getName());
            profile.put("username", studentUser.getUsername());
            profile.put("studentId", studentUser.getStudentUserId());
            profile.put("gender", studentUser.getGender());
            profile.put("grade", studentUser.getGrade());
            profile.put("majorId", studentUser.getMajorId());
            profile.put("classId", studentUser.getClassId());
            profile.put("className", studentUser.getClassName());
            profile.put("school", studentUser.getSchool());
            profile.put("department", studentUser.getDepartment());
            profile.put("major", studentUser.getMajor());
            profile.put("class", studentUser.getClasses());
            profile.put("phone", studentUser.getPhone());
            profile.put("email", studentUser.getEmail());
            profile.put("avatar", studentUser.getAvatar());
            return Result.success(profile);
        } catch (Exception e) {
            log.error("获取个人信息失败: {}", e.getMessage(), e);
            return Result.error("获取失败");
        }
    }

    @PostMapping("/profile/update")
    public Result updateProfile(@RequestBody Map<String, Object> body) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !(authentication.getPrincipal() instanceof org.springframework.security.core.userdetails.User)) {
                return Result.error("未登录");
            }
            String username = ((org.springframework.security.core.userdetails.User) authentication.getPrincipal()).getUsername();
            User user = userService.findByUsername(username);
            if (user == null) return Result.error("用户不存在");

            StudentUser studentUser = studentUserService.findById(user.getId());
            if (studentUser == null) return Result.error("学生信息不存在");

            if (body.containsKey("name")) studentUser.setName((String) body.get("name"));
            if (body.containsKey("gender")) {
                Object g = body.get("gender");
                if (g instanceof Integer) studentUser.setGender((Integer) g);
                else if (g != null) studentUser.setGender(Integer.parseInt(g.toString()));
            }
            if (body.containsKey("grade")) {
                Object g = body.get("grade");
                if (g instanceof Integer) studentUser.setGrade((Integer) g);
                else if (g != null && !g.toString().isEmpty()) studentUser.setGrade(Integer.parseInt(g.toString()));
            }
            if (body.containsKey("department")) studentUser.setDepartment((String) body.get("department"));
            if (body.containsKey("major")) studentUser.setMajor((String) body.get("major"));
            if (body.containsKey("class")) studentUser.setClasses((String) body.get("class"));
            if (body.containsKey("phone")) studentUser.setPhone((String) body.get("phone"));
            if (body.containsKey("email")) studentUser.setEmail((String) body.get("email"));
            if (body.containsKey("avatar")) studentUser.setAvatar((String) body.get("avatar"));
            studentUserService.update(studentUser);
            return Result.success("更新成功");
        } catch (Exception e) {
            log.error("更新个人信息失败: {}", e.getMessage(), e);
            return Result.error("更新失败");
        }
    }

    @PostMapping("/profile/change-password")
    public Result changePassword(@RequestBody Map<String, String> body) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !(authentication.getPrincipal() instanceof org.springframework.security.core.userdetails.User)) {
                return Result.error("未登录");
            }
            String username = ((org.springframework.security.core.userdetails.User) authentication.getPrincipal()).getUsername();
            User user = userService.findByUsername(username);
            if (user == null) return Result.error("用户不存在");

            String oldPassword = body.get("oldPassword");
            String newPassword = body.get("newPassword");
            if (oldPassword == null || newPassword == null) return Result.error("参数不完整");

            StudentUser studentUser = studentUserService.findById(user.getId());
            if (studentUser == null) return Result.error("用户不存在");

            if (!passwordEncoder.matches(oldPassword, studentUser.getPassword())) {
                return Result.error("旧密码不正确");
            }
            studentUser.setPassword(passwordEncoder.encode(newPassword));
            studentUserService.update(studentUser);
            return Result.success("密码修改成功");
        } catch (Exception e) {
            log.error("修改密码失败: {}", e.getMessage(), e);
            return Result.error("修改失败");
        }
    }

    @GetMapping("/internship-record")
    public Result getInternshipRecord() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !(authentication.getPrincipal() instanceof org.springframework.security.core.userdetails.User)) {
                return Result.error("未登录");
            }
            String username = ((org.springframework.security.core.userdetails.User) authentication.getPrincipal()).getUsername();
            User user = userService.findByUsername(username);
            if (user == null) return Result.error("用户不存在");

            StudentInternshipStatus status = studentInternshipStatusService.findByStudentId(user.getId());
            if (status != null) {
                // 格式化日期
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");

                // 优先使用岗位设置的实习时间，其次使用系室默认设置
                String startDateStr = null;
                String endDateStr = null;

                // 1. 先尝试获取岗位设置的实习时间
                if (status.getPositionId() != null) {
                    Position position = positionService.findById(status.getPositionId());
                    if (position != null && position.getInternshipStartDate() != null && position.getInternshipEndDate() != null) {
                        startDateStr = sdf.format(position.getInternshipStartDate());
                        endDateStr = sdf.format(position.getInternshipEndDate());
                    }
                }

                // 2. 如果岗位没有设置，使用系室默认设置
                if (startDateStr == null || endDateStr == null) {
                    InternshipTimeSettings defaultSettings = internshipTimeSettingsService.findLatest();
                    if (startDateStr == null && defaultSettings != null && defaultSettings.getStartDate() != null) {
                        startDateStr = defaultSettings.getStartDate();
                    }
                    if (endDateStr == null && defaultSettings != null && defaultSettings.getEndDate() != null) {
                        endDateStr = defaultSettings.getEndDate();
                    }
                }

                // 计算实习进度
                int progress = calculateInternshipProgress(status, startDateStr, endDateStr);

                // 返回带progress的Map
                java.util.Map<String, Object> result = new java.util.HashMap<>();
                result.put("id", status.getId());
                result.put("studentId", status.getStudentId());
                result.put("status", status.getStatus());
                result.put("companyId", status.getCompanyId());
                result.put("positionId", status.getPositionId());
                result.put("internshipStartDate", startDateStr != null ? startDateStr : "");
                result.put("internshipEndDate", endDateStr != null ? endDateStr : "");
                result.put("internshipDuration", status.getInternshipDuration());
                result.put("companyName", status.getCompanyName());
                result.put("positionName", status.getPositionName());
                result.put("progress", progress);
                return Result.success(result);
            }
            return Result.success(null);
        } catch (Exception e) {
            log.error("获取实习记录失败: {}", e.getMessage(), e);
            return Result.error("获取失败");
        }
    }

    /**
     * 计算实习进度百分比
     * @param status 学生实习状态
     * @param startDateStr 实习开始日期字符串
     * @param endDateStr 实习结束日期字符串
     */
    private int calculateInternshipProgress(StudentInternshipStatus status, String startDateStr, String endDateStr) {
        if (status == null) {
            return 0;
        }

        Date now = new Date();
        Date startTime = null;
        Date endTime = null;

        // 优先使用学生自己的实习时间设置
        if (status.getInternshipStartTime() != null && status.getInternshipEndTime() != null) {
            startTime = status.getInternshipStartTime();
            endTime = status.getInternshipEndTime();
        } else if (startDateStr != null && endDateStr != null) {
            // 否则使用传入的日期字符串
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                startTime = sdf.parse(startDateStr);
                endTime = sdf.parse(endDateStr);
            } catch (Exception e) {
                log.warn("解析实习时间失败: {}", e.getMessage());
                return 0;
            }
        } else {
            return 0;
        }

        // 如果当前日期在开始日期之前
        if (now.before(startTime)) {
            return 0;
        }

        // 如果当前日期在结束日期之后
        if (now.after(endTime)) {
            return 100;
        }

        // 计算进度
        long totalDuration = endTime.getTime() - startTime.getTime();
        long elapsed = now.getTime() - startTime.getTime();

        if (totalDuration <= 0) {
            return 0;
        }

        return (int) (elapsed * 100 / totalDuration);
    }
}
