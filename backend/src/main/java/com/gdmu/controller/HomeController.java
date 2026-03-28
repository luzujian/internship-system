package com.gdmu.controller;

import com.gdmu.entity.Result;
import com.gdmu.entity.dto.HomeStatsDTO;
import com.gdmu.service.HomeService;
import com.gdmu.utils.CurrentHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/home")
public class HomeController {
    
    @Autowired
    private HomeService homeService;
    
    @GetMapping("/stats")
    public Result getHomeStats() {
        log.info("获取首页统计数据");
        
        try {
            Long userId = CurrentHolder.getUserId();
            String userRole = CurrentHolder.getUserRole();
            
            String userType = mapRoleToUserType(userRole);
            
            if (userId == null) {
                userId = 0L;
                userType = "TEACHER";
            }
            
            HomeStatsDTO homeStats = homeService.getHomeStats(userId, userType);
            
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
