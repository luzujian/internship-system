package com.gdmu.mapper;

import com.gdmu.entity.Announcement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 通知公告Mapper接口
 */
@Mapper
public interface AnnouncementMapper {
    /**
     * 插入通知公告
     * @param announcement 通知公告
     * @return 插入的记录数
     */
    int insert(Announcement announcement);
    
    /**
     * 根据ID查询通知公告
     * @param id 公告ID
     * @return 通知公告
     */
    Announcement findById(Long id);
    
    /**
     * 更新通知公告
     * @param announcement 通知公告
     * @return 更新的记录数
     */
    int update(Announcement announcement);
    
    /**
     * 删除通知公告
     * @param id 公告ID
     * @return 删除的记录数
     */
    int deleteById(Long id);
    
    /**
     * 查询所有通知公告
     * @return 通知公告列表
     */
    List<Announcement> findAll();
    
    /**
     * 动态条件查询通知公告（根据标题和状态）
     * @param title 公告标题
     * @param status 公告状态
     * @return 通知公告列表
     */
    List<Announcement> listByTitleAndStatus(@Param("title") String title, 
                           @Param("status") String status);
    
    /**
     * 动态条件查询通知公告（根据标题、状态和发布者）
     * @param title 公告标题
     * @param status 公告状态
     * @param publisher 发布者
     * @param excludePublisherRole 排除的发布者角色
     * @return 通知公告列表
     */
    List<Announcement> list(@Param("title") String title,
                           @Param("status") String status,
                           @Param("publisher") String publisher,
                           @Param("excludePublisherRole") String excludePublisherRole);
    
    /**
     * 根据ID列表查询通知公告
     * @param ids 公告ID列表
     * @return 通知公告列表
     */
    List<Announcement> selectByIds(List<Long> ids);
    
    /**
     * 批量删除通知公告
     * @param ids 公告ID列表
     * @return 删除的记录数
     */
    int batchDeleteByIds(@Param("ids") List<Long> ids);
    
    /**
     * 查询通知公告总数
     * @return 通知公告总数
     */
    Long count();
    
    /**
     * 分页查询通知公告
     * @param offset 偏移量
     * @param pageSize 每页大小
     * @return 通知公告列表
     */
    List<Announcement> findPage(@Param("offset") Integer offset, @Param("pageSize") Integer pageSize);
    
    /**
     * 根据用户类型和用户信息获取公告列表
     * @param userType 用户类型：STUDENT/TEACHER
     * @param userInfo 用户信息（学生：majorId；教师：teacherType）
     * @param userId 当前用户ID，用于判断已读状态
     * @return 公告列表
     */
    List<Announcement> getAnnouncementsForUser(@Param("userType") String userType, @Param("userInfo") String userInfo, @Param("userId") String userId);
    
    /**
     * 增加公告阅读次数
     * @param id 公告ID
     * @return 更新的记录数
     */
    int incrementReadCount(@Param("id") Long id);
}