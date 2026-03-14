package com.gdmu.service;

import com.gdmu.entity.AnnouncementReadRecord;
import java.util.List;

/**
 * 公告阅读记录服务接口
 */
public interface AnnouncementReadRecordService {
    /**
     * 插入公告阅读记录
     * @param record 公告阅读记录
     * @return 插入的记录数
     */
    int insert(AnnouncementReadRecord record);
    
    /**
     * 根据ID查询公告阅读记录
     * @param id 记录ID
     * @return 公告阅读记录
     */
    AnnouncementReadRecord findById(Long id);
    
    /**
     * 根据公告ID和用户查询阅读记录
     * @param announcementId 公告ID
     * @param userId 用户ID
     * @param userType 用户类型
     * @return 公告阅读记录
     */
    AnnouncementReadRecord findByAnnouncementAndUser(Long announcementId, Long userId, String userType);
    
    /**
     * 查询公告的未读人数
     * @param announcementId 公告ID
     * @return 未读人数
     */
    Long countUnreadByAnnouncementId(Long announcementId);
    
    /**
     * 查询公告的已读人数
     * @param announcementId 公告ID
     * @return 已读人数
     */
    Long countReadByAnnouncementId(Long announcementId);
    
    /**
     * 查询用户的未读公告数量
     * @param userId 用户ID
     * @param userType 用户类型
     * @return 未读公告数量
     */
    Long countUnreadByUserId(Long userId, String userType);
    
    /**
     * 查询公告的阅读记录列表
     * @param announcementId 公告ID
     * @return 阅读记录列表
     */
    List<AnnouncementReadRecord> findByAnnouncementId(Long announcementId);
}
