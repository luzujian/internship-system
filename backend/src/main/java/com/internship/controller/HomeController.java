package com.internship.controller;

import com.internship.entity.Result;
import com.internship.entity.TeacherUser;
import com.internship.entity.dto.HomeStatsDTO;
import com.internship.mapper.TeacherUserMapper;
import com.internship.service.HomeService;
import com.internship.service.SystemConfigService;
import com.internship.utils.CurrentHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/home")
public class HomeController {

    @Autowired
    private HomeService homeService;

    @Autowired
    private TeacherUserMapper teacherUserMapper;

    @Autowired
    private SystemConfigService systemConfigService;
    
    @GetMapping("/ai-config")
    public Result getAiConfig() {
        try {
            com.internship.entity.SystemConfig config = systemConfigService.findByConfigKey("AI_ASSISTANT_ENABLED");
            if (config != null) {
                return Result.success(Boolean.parseBoolean(config.getConfigValue()));
            }
            return Result.success(true); // 默认开启
        } catch (Exception e) {
            log.error("获取AI助手配置失败: {}", e.getMessage());
            return Result.success(true); // 出错时默认开启
        }
    }
    
    @GetMapping("/stats")
    public Result getHomeStats(@RequestParam(required = false) String startDate,
                               @RequestParam(required = false) String endDate) {
        log.info("获取首页统计数据，时间范围：{} - {}", startDate, endDate);

        try {
            Long userId = CurrentHolder.getUserId();
            String userRole = CurrentHolder.getUserRole();

            // 获取用户的实际身份类型（teacher_type字段）
            String teacherType = null;
            if (userId != null) {
                TeacherUser teacher = teacherUserMapper.findById(userId);
                if (teacher != null) {
                    teacherType = teacher.getTeacherType();
                    log.info("用户 {} 的实际身份类型：{}", userId, teacherType);
                }
            }

            // 如果没有查到teacherType，回退到基于role的判断
            if (teacherType == null) {
                teacherType = mapRoleToUserType(userRole);
                log.info("未查到teacherType，回退到role判断：{}", teacherType);
            }

            if (userId == null) {
                userId = 0L;
                teacherType = "TEACHER";
            }

            HomeStatsDTO homeStats = homeService.getHomeStats(userId, teacherType, startDate, endDate);

            return Result.success(homeStats);
        } catch (Exception e) {
            log.error("获取首页统计数据失败: {}", e.getMessage(), e);
            return Result.error("获取首页统计数据失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/announcement/{announcementId}/read")
    public Result markAnnouncementAsRead(@PathVariable Long announcementId) {
        log.info("标记公告为已读，公告ID: {}", announcementId);
        
        try {
            Long userId = CurrentHolder.getUserId();
            String userRole = CurrentHolder.getUserRole();
            
            String userType = mapRoleToUserType(userRole);
            
            if (userId == null) {
                return Result.error("用户未登录，无法标记公告");
            }
            
            homeService.markAnnouncementAsRead(announcementId, userId, userType);
            
            return Result.success("标记成功");
        } catch (Exception e) {
            log.error("标记公告为已读失败: {}", e.getMessage(), e);
            return Result.error("标记失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/announcement/{announcementId}/unread")
    public Result markAnnouncementAsUnread(@PathVariable Long announcementId) {
        log.info("标记公告为未读，公告ID: {}", announcementId);
        
        try {
            Long userId = CurrentHolder.getUserId();
            String userRole = CurrentHolder.getUserRole();
            
            String userType = mapRoleToUserType(userRole);
            
            if (userId == null) {
                return Result.error("用户未登录，无法标记公告");
            }
            
            homeService.markAnnouncementAsUnread(announcementId, userId, userType);
            
            return Result.success("标记成功");
        } catch (Exception e) {
            log.error("标记公告为未读失败: {}", e.getMessage(), e);
            return Result.error("标记失败: " + e.getMessage());
        }
    }
    
    private String mapRoleToUserType(String role) {
        if (role == null) {
            return "TEACHER";
        }

        switch (role) {
            case "ROLE_ADMIN":
                return "ADMIN";
            case "ROLE_TEACHER_COUNSELOR":
                return "COUNSELOR";
            case "ROLE_TEACHER":
            case "ROLE_TEACHER_COLLEGE":
            case "ROLE_TEACHER_DEPARTMENT":
                return "TEACHER";
            case "ROLE_STUDENT":
                return "STUDENT";
            case "ROLE_ENTERPRISE":
            case "ROLE_COMPANY":
                return "ENTERPRISE";
            default:
                return "TEACHER";
        }
    }
}
