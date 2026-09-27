package com.internship.controller;

import com.internship.anno.Log;
import com.internship.entity.Result;
import com.internship.entity.User;
import com.internship.entity.StudentInternshipStatus;
import com.internship.entity.Announcement;
import com.internship.entity.dto.JobSearchStatusDTO;
import com.internship.entity.dto.RecentActivityPageDTO;
import com.internship.mapper.AnnouncementMapper;
import com.internship.mapper.StudentJobApplicationMapper;
import com.internship.service.InternshipProgressRecordService;
import com.internship.service.StudentHomeTodoService;
import com.internship.service.StudentInternshipStatusService;
import com.internship.service.JobSearchStatusService;
import com.internship.service.SystemConfigService;
import com.internship.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/student/home")
@PreAuthorize("hasRole('STUDENT')")
public class StudentHomeController {

    @Autowired
    private UserService userService;

    @Autowired
    private InternshipProgressRecordService progressRecordService;

    @Autowired
    private StudentJobApplicationMapper applicationMapper;

    @Autowired
    private StudentInternshipStatusService internshipStatusService;

    @Autowired
    private JobSearchStatusService jobSearchStatusService;

    @Autowired
    private AnnouncementMapper announcementMapper;

    @Autowired
    private StudentHomeTodoService studentHomeTodoService;

    @Autowired
    private com.internship.mapper.StudentUserMapper studentUserMapper;

    @Autowired
    private com.internship.service.AnnouncementReadRecordService readRecordService;

    @Autowired
    private SystemConfigService systemConfigService;

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof org.springframework.security.core.userdetails.User)) {
            return null;
        }
        String username = ((org.springframework.security.core.userdetails.User) auth.getPrincipal()).getUsername();
        return userService.findByUsername(username);
    }

    /**
     * 解析学生ID，支持两种格式：
     * 1. 纯数字字符串如 "1" -> 直接转换为 Long
     * 2. 学号字符串如 "s001" -> 通过用户服务查找对应的 ID
     */
    private Long parseStudentId(String studentId) {
        if (studentId == null || studentId.isBlank()) {
            throw new IllegalArgumentException("studentId不能为空");
        }
        try {
            return Long.parseLong(studentId);
        } catch (NumberFormatException e) {
            User user = userService.findByUsername(studentId);
            if (user != null) {
                return user.getId();
            }
            throw new IllegalArgumentException("无效的学生ID: " + studentId);
        }
    }

    @GetMapping("/menu-config")
    public Result getStudentMenuConfig() {
        try {
            com.internship.entity.SystemConfig config = systemConfigService.findByConfigKey("STUDENT_MENU_CONFIG");
            if (config != null) {
                return Result.success(config.getConfigValue());
            }
            return Result.success("[]");
        } catch (Exception e) {
            log.error("获取学生菜单配置失败: {}", e.getMessage(), e);
            return Result.error("获取菜单配置失败");
        }
    }

    @GetMapping("/stats")
    public Result getStats() {
        try {
            User user = getCurrentUser();
            if (user == null) return Result.error("未登录");
            Long studentId = user.getId();

            // 使用聚合查询一次性获取所有统计数据，减少DB访问次数（原来4次查询现在1次）
            Map<String, Object> statsData = applicationMapper.getStudentHomeStats(studentId);

            Map<String, Object> stats = new HashMap<>();
            stats.put("applied", statsData.getOrDefault("applied", 0));
            stats.put("interviewInvites", statsData.getOrDefault("interviewInvites", 0));
            stats.put("current", statsData.getOrDefault("confirmedRecords", 0));
            stats.put("pendingReports", statsData.getOrDefault("submittedReflections", 0));
            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取首页统计失败: {}", e.getMessage(), e);
            return Result.error("获取统计失败");
        }
    }

    @GetMapping("/todo-list")
    public Result getTodoList() {
        try {
            User user = getCurrentUser();
            if (user == null) return Result.error("未登录");
            return Result.success(studentHomeTodoService.getActionableTodos(user.getId()));
        } catch (Exception e) {
            log.error("获取待办列表失败: {}", e.getMessage(), e);
            return Result.error("获取待办列表失败");
        }
    }

    @GetMapping("/progress-list")
    public Result getProgressList(
            @RequestParam(defaultValue = "10") String limit,
            @RequestParam(required = false) String cursor) {
        try {
            User user = getCurrentUser();
            if (user == null) return Result.error("未登录");
            int parsedLimit;
            try {
                parsedLimit = Integer.parseInt(limit);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("limit必须是整数");
            }
            RecentActivityPageDTO page = progressRecordService.getRecentActivities(user.getId(), parsedLimit, cursor);
            return Result.success(page);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取进展列表失败: {}", e.getMessage(), e);
            return Result.error("获取进展列表失败");
        }
    }

    @GetMapping("/notifications")
    public Result getNotifications(@RequestParam(required = false) String studentId) {
        try {
            User user = getCurrentUser();
            if (user == null) return Result.error("未登录");

            // 获取学生专业ID
            String majorId = null;
            try {
                com.internship.entity.StudentUser studentUser = studentUserMapper.findById(user.getId());
                if (studentUser != null && studentUser.getMajorId() != null) {
                    majorId = String.valueOf(studentUser.getMajorId());
                }
            } catch (Exception e) {
                log.warn("获取学生专业信息失败: {}", e.getMessage());
            }

            // 获取学生已读公告ID集合
            java.util.Set<Long> readIds = readRecordService
                    .findByUserId(String.valueOf(user.getId()), "STUDENT")
                    .stream()
                    .map(com.internship.entity.AnnouncementReadRecord::getAnnouncementId)
                    .collect(Collectors.toSet());

            // 使用带目标群体过滤的查询（SQL 层已过滤 status=PUBLISHED 和有效期限）
            List<Announcement> announcements = announcementMapper.getAnnouncementsForUser(
                    "STUDENT", majorId, String.valueOf(user.getId()), "STUDENT");

            // 转换为通知格式（SQL 已过滤，无需 Java 侧再过滤 status）
            List<Map<String, Object>> notifications = announcements.stream()
                    .map(a -> {
                        Map<String, Object> notification = new HashMap<>();
                        notification.put("id", a.getId());
                        notification.put("title", a.getTitle());
                        notification.put("content", a.getContent());
                        notification.put("notificationTime", a.getPublishTime());
                        notification.put("publishTime", a.getPublishTime());
                        notification.put("publisher", a.getPublisher());
                        notification.put("publisherRole", a.getPublisherRole());
                        notification.put("isRead", readIds.contains(a.getId()));
                        return notification;
                    })
                    .collect(Collectors.toList());

            return Result.success(notifications);
        } catch (Exception e) {
            log.error("获取通知列表失败: {}", e.getMessage(), e);
            return Result.error("获取通知列表失败");
        }
    }

    @GetMapping("/unread-count")
    public Result getUnreadCount(@RequestParam(required = false) String studentId) {
        try {
            User user = getCurrentUser();
            if (user == null) return Result.error("未登录");

            // 获取学生专业ID
            String majorId = null;
            try {
                com.internship.entity.StudentUser studentUser = studentUserMapper.findById(user.getId());
                if (studentUser != null && studentUser.getMajorId() != null) {
                    majorId = String.valueOf(studentUser.getMajorId());
                }
            } catch (Exception e) {
                log.warn("获取学生专业信息失败: {}", e.getMessage());
            }

            // 获取学生已读公告ID集合
            java.util.Set<Long> readIds2 = readRecordService
                    .findByUserId(String.valueOf(user.getId()), "STUDENT")
                    .stream()
                    .map(com.internship.entity.AnnouncementReadRecord::getAnnouncementId)
                    .collect(Collectors.toSet());

            // 使用带目标群体过滤的查询（SQL 层已过滤 status=PUBLISHED 和有效期限）
            List<Announcement> announcements2 = announcementMapper.getAnnouncementsForUser(
                    "STUDENT", majorId, String.valueOf(user.getId()), "STUDENT");
            long totalPublished = announcements2.size();
            long readCount = announcements2.stream()
                    .filter(a -> readIds2.contains(a.getId()))
                    .count();

            return Result.success(Map.of("count", totalPublished - readCount));
        } catch (Exception e) {
            log.error("获取未读通知数量失败: {}", e.getMessage(), e);
            return Result.error("获取未读通知数量失败");
        }
    }

    /**
     * 获取当前学生的实习状态（系统自动判断）
     * 判断逻辑（优先级从高到低）：
     * 1. 已确认(status>=2)且在实习期间内 → 进行中
     * 2. 已确认(status>=2)且结束时间已过 → 已结束
     * 3. 已确认(status>=2) → 进行中
     * 4. 已有offer(status=1) → 已有Offer
     * 5. 有pending的申请 → 已申请
     * 6. 否则 → 未找到
     */
    @GetMapping("/internship-status")
    public Result getInternshipStatus() {
        try {
            User user = getCurrentUser();
            if (user == null) return Result.error("未登录");

            Long studentId = user.getId();
            StudentInternshipStatus status = internshipStatusService.findByStudentId(studentId);

            // 检查是否有pending的申请
            boolean hasPendingApplication = applicationMapper.findByStudentId(studentId).stream()
                    .anyMatch(a -> "pending".equals(a.getStatus()));

            // 计算当前状态
            int currentStatus = calculateCurrentStatus(status, studentId, hasPendingApplication);
            JobSearchStatusDTO jobSearchStatus = jobSearchStatusService.resolve(studentId, currentStatus);

            // 返回完整状态信息，包含计算后的状态值
            Map<String, Object> result = new HashMap<>();
            if (status != null) {
                result.put("id", status.getId());
                result.put("studentId", status.getStudentId());
                result.put("status", status.getStatus());
                result.put("companyName", status.getCompanyName());
                result.put("positionName", status.getPositionName());
                result.put("internshipStartTime", status.getInternshipStartTime());
                result.put("internshipEndTime", status.getInternshipEndTime());
                result.put("isDelayed", status.getIsDelayed());
                result.put("isInterrupted", status.getIsInterrupted());
                result.put("otherReason", status.getOtherReason());
                result.put("remark", status.getRemark());
                result.put("companyAddress", status.getCompanyAddress());
                result.put("companyPhone", status.getCompanyPhone());
                result.put("hasRecord", true);
            } else {
                result.put("hasRecord", false);
            }
            // 添加计算后的状态值
            result.put("currentStatus", currentStatus);
            result.put("internshipStatus", currentStatus);
            result.put("jobSearchStatus", jobSearchStatus.jobSearchStatus());
            result.put("jobSearchStatusText", jobSearchStatus.jobSearchStatusText());
            result.put("hasPendingApplication", hasPendingApplication);

            return Result.success(result);
        } catch (Exception e) {
            log.error("获取实习状态失败：{}", e.getMessage(), e);
            return Result.error("获取实习状态失败");
        }
    }

    /**
     * 计算当前实习状态
     */
    private int calculateCurrentStatus(StudentInternshipStatus status, Long studentId, boolean hasPendingApplication) {
        // 如果有实习确认记录且已确认(status >= 2)
        if (status != null && status.getStatus() != null && status.getStatus() >= 2) {
            Date now = new Date();
            Date startTime = status.getInternshipStartTime();
            Date endTime = status.getInternshipEndTime();

            // 如果有中断标记
            if (Boolean.TRUE.equals(status.getIsInterrupted())) {
                return 5; // 已中断
            }

            // 如果有延期标记
            if (Boolean.TRUE.equals(status.getIsDelayed())) {
                return 6; // 延期
            }

            // 判断是否在实习期间内
            if (startTime != null && endTime != null) {
                if (now.before(startTime)) {
                    // 尚未开始
                    return 2; // 已确认（等待开始）
                } else if (now.after(endTime)) {
                    // 已结束
                    return 4; // 已结束
                } else {
                    // 进行中
                    return 3; // 实习中
                }
            } else if (startTime != null && now.after(startTime)) {
                // 没有结束时间但有开始时间，且已过开始时间，视为进行中
                return 3;
            } else if (startTime == null && Integer.valueOf(3).equals(status.getStatus())) {
                // 开始时间缺失但数据库状态已是"实习中"（如批量导入未同步时间），
                // 不再降级为"已确认"，保持与实际状态一致
                return 3;
            } else if (startTime == null && Integer.valueOf(4).equals(status.getStatus())) {
                // 同理，已结束状态不因时间缺失而降级
                return 4;
            } else {
                // 其他情况（状态为 2 的"已确定"、或仅有开始时间但尚未开始）→ 按"已确认（等待开始）"显示
                return 2;
            }
        }

        // 如果有offer(status = 1)
        if (status != null && status.getStatus() != null && status.getStatus() == 1) {
            return 1; // 已有Offer
        }

        // 有pending的申请
        if (hasPendingApplication) {
            return 0; // 已申请
        }

        // 默认未找到
        return 0;
    }

    /**
     * 提交或更新实习状态
     */
    @PostMapping("/internship-status")
    @PreAuthorize("denyAll()")
    @Log(operationType = "CREATE", module = "STUDENT_INTERNSHIP", description = "提交实习状态", riskLevel = "MEDIUM")
    public Result submitInternshipStatus(@RequestBody StudentInternshipStatus status) {
        try {
            User user = getCurrentUser();
            if (user == null) return Result.error("未登录");

            // 检查是否已存在实习状态
            StudentInternshipStatus existingStatus = internshipStatusService.findByStudentId(user.getId());

            if (existingStatus != null) {
                // 更新现有状态
                existingStatus.setStatus(status.getStatus());
                existingStatus.setStudentName(status.getStudentName());
                existingStatus.setGender(status.getGender());
                existingStatus.setSchool(status.getSchool());
                existingStatus.setGrade(status.getGrade());
                existingStatus.setEducation(status.getEducation());
                existingStatus.setCollege(status.getCollege());
                existingStatus.setMajorName(status.getMajorName());
                existingStatus.setClassName(status.getClassName());
                existingStatus.setContactPhone(status.getContactPhone());
                existingStatus.setEmail(status.getEmail());
                existingStatus.setCompanyName(status.getCompanyName());
                existingStatus.setPositionName(status.getPositionName());
                existingStatus.setCompanyAddress(status.getCompanyAddress());
                existingStatus.setCompanyPhone(status.getCompanyPhone());
                existingStatus.setInternshipStartTime(status.getInternshipStartTime());
                existingStatus.setInternshipEndTime(status.getInternshipEndTime());
                existingStatus.setInternshipDuration(status.getInternshipDuration());
                existingStatus.setGraduateSchool(status.getGraduateSchool());
                existingStatus.setGraduateMajor(status.getGraduateMajor());
                existingStatus.setOtherReason(status.getOtherReason());
                existingStatus.setRemark(status.getRemark());

                int result = internshipStatusService.update(existingStatus);
                if (result > 0) {
                    return Result.success("更新成功", existingStatus);
                }
                return Result.error("更新失败");
            } else {
                // 创建新状态
                status.setStudentId(user.getId());
                int result = internshipStatusService.insert(status);
                if (result > 0) {
                    return Result.success("提交成功", status);
                }
                return Result.error("提交失败");
            }
        } catch (Exception e) {
            log.error("提交实习状态失败：{}", e.getMessage(), e);
            return Result.error("提交实习状态失败");
        }
    }
}
