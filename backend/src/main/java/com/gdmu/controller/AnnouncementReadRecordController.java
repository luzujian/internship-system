package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.entity.Result;
import com.gdmu.entity.AnnouncementReadRecord;
import com.gdmu.service.AnnouncementReadRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/announcement-read-records")
public class AnnouncementReadRecordController {
    
    @Autowired
    private AnnouncementReadRecordService announcementReadRecordService;
    
    // 创建公告阅读记录
    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_TEACHER', 'ROLE_STUDENT')")
    @Log(operationType = "INSERT", module = "ANNOUNCEMENT_MANAGEMENT", description = "创建公告阅读记录")
    public Result createReadRecord(@RequestBody AnnouncementReadRecord record) {
        log.info("创建公告阅读记录，公告ID: {}, 用户ID: {}", record.getAnnouncementId(), record.getUserId());
        try {
            int result = announcementReadRecordService.insert(record);
            return Result.success("创建阅读记录成功", result);
        } catch (Exception e) {
            log.error("创建阅读记录失败: {}", e.getMessage(), e);
            return Result.error("创建阅读记录失败: " + e.getMessage());
        }
    }
    
    // 根据ID获取阅读记录
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Result getReadRecordById(@PathVariable Long id) {
        log.info("根据ID获取阅读记录: {}", id);
        try {
            AnnouncementReadRecord record = announcementReadRecordService.findById(id);
            if (record == null) {
                return Result.error("阅读记录不存在");
            }
            return Result.success(record);
        } catch (Exception e) {
            log.error("获取阅读记录失败: {}", e.getMessage(), e);
            return Result.error("获取阅读记录失败: " + e.getMessage());
        }
    }
    
    // 根据公告ID和用户查询阅读记录
    @GetMapping("/check")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_TEACHER', 'ROLE_STUDENT')")
    public Result checkReadStatus(@RequestParam Long announcementId, 
                                 @RequestParam String userId, 
                                 @RequestParam String userType) {
        log.info("检查公告阅读状态，公告ID: {}, 用户ID: {}, 用户类型: {}", announcementId, userId, userType);
        try {
            AnnouncementReadRecord record = announcementReadRecordService.findByAnnouncementAndUser(
                    announcementId, userId, userType);
            return Result.success(record != null);
        } catch (Exception e) {
            log.error("检查阅读状态失败: {}", e.getMessage(), e);
            return Result.error("检查阅读状态失败: " + e.getMessage());
        }
    }
    
    // 获取公告的未读人数
    @GetMapping("/unread-count/{announcementId}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Result getUnreadCount(@PathVariable Long announcementId) {
        log.info("获取公告未读人数，公告ID: {}", announcementId);
        try {
            Long count = announcementReadRecordService.countUnreadByAnnouncementId(announcementId);
            return Result.success(count);
        } catch (Exception e) {
            log.error("获取未读人数失败: {}", e.getMessage(), e);
            return Result.error("获取未读人数失败: " + e.getMessage());
        }
    }
    
    // 获取公告的已读人数
    @GetMapping("/read-count/{announcementId}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Result getReadCount(@PathVariable Long announcementId) {
        log.info("获取公告已读人数，公告ID: {}", announcementId);
        try {
            Long count = announcementReadRecordService.countReadByAnnouncementId(announcementId);
            return Result.success(count);
        } catch (Exception e) {
            log.error("获取已读人数失败: {}", e.getMessage(), e);
            return Result.error("获取已读人数失败: " + e.getMessage());
        }
    }
    
    // 获取用户的未读公告数量
    @GetMapping("/user-unread-count")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_TEACHER', 'ROLE_STUDENT')")
    public Result getUserUnreadCount(@RequestParam Long userId, @RequestParam String userType) {
        log.info("获取用户未读公告数量，用户ID: {}, 用户类型: {}", userId, userType);
        try {
            Long count = announcementReadRecordService.countUnreadByUserId(userId, userType);
            return Result.success(count);
        } catch (Exception e) {
            log.error("获取用户未读公告数量失败: {}", e.getMessage(), e);
            return Result.error("获取用户未读公告数量失败: " + e.getMessage());
        }
    }
    
    // 获取公告的阅读记录列表
    @GetMapping("/by-announcement/{announcementId}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Result getReadRecordsByAnnouncement(@PathVariable Long announcementId) {
        log.info("获取公告阅读记录列表，公告ID: {}", announcementId);
        try {
            List<AnnouncementReadRecord> records = announcementReadRecordService.findByAnnouncementId(announcementId);
            return Result.success(records);
        } catch (Exception e) {
            log.error("获取阅读记录列表失败: {}", e.getMessage(), e);
            return Result.error("获取阅读记录列表失败: " + e.getMessage());
        }
    }
}
