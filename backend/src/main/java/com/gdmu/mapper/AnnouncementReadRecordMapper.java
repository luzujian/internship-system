package com.gdmu.mapper;

import com.gdmu.entity.AnnouncementReadRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 公告阅读记录Mapper接口
 */
@Mapper
public interface AnnouncementReadRecordMapper {
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
     * 根据公告ID和用户ID查询阅读记录
     * @param announcementId 公告ID
     * @param userId 用户ID
     * @param userType 用户类型
     * @return 公告阅读记录
     */
    AnnouncementReadRecord findByAnnouncementAndUser(
            @Param("announcementId") Long announcementId,
            @Param("userId") String userId,
            @Param("userType") String userType);
    
    /**
     * 根据公告ID查询阅读记录列表
     * @param announcementId 公告ID
     * @return 公告阅读记录列表
     */
    List<AnnouncementReadRecord> findByAnnouncementId(Long announcementId);
    
    /**
     * 根据用户ID查询阅读记录列表
     * @param userId 用户ID
     * @param userType 用户类型
     * @return 公告阅读记录列表
     */
    List<AnnouncementReadRecord> findByUserId(
            @Param("userId") String userId,
            @Param("userType") String userType);
    
    /**
     * 查询公告的已读人数
     * @param announcementId 公告ID
     * @return 已读人数
     */
    Long countByAnnouncementId(Long announcementId);

    Long countUnreadByAnnouncementId(@Param("announcementId") Long announcementId,
                                      @Param("targetType") String targetType,
                                      @Param("targetValue") String targetValue);
    
    /**
     * 批量插入公告阅读记录
     * @param records 公告阅读记录列表
     * @return 插入的记录数
     */
    int batchInsert(@Param("records") List<AnnouncementReadRecord> records);
}
