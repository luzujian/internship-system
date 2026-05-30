package com.internship.service;

import com.internship.entity.dto.HomeStatsDTO;

/**
 * 首页服务接口
 */
public interface HomeService {
    
    /**
     * 获取首页统计数据
     * @param userId 用户ID
     * @param userType 用户类型
     * @param startDate 开始日期（可选）
     * @param endDate 结束日期（可选）
     * @return 首页统计数据
     */
    HomeStatsDTO getHomeStats(Long userId, String userType, String startDate, String endDate);
    
    /**
     * 标记公告为已读
     * @param announcementId 公告ID
     * @param userId 用户ID
     * @param userType 用户类型
     */
    void markAnnouncementAsRead(Long announcementId, Long userId, String userType);
    
    /**
     * 标记公告为未读
     * @param announcementId 公告ID
     * @param userId 用户ID
     * @param userType 用户类型
     */
    void markAnnouncementAsUnread(Long announcementId, Long userId, String userType);
}
