package com.gdmu.service;

import com.gdmu.entity.Announcement;
import com.gdmu.entity.PageResult;

import java.util.List;
import java.util.Map;

/**
 * 通知公告服务接口
 */
public interface AnnouncementService {
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
    int delete(Long id);
    
    /**
     * 查询所有通知公告
     * @return 通知公告列表
     */
    List<Announcement> findAll();
    
    /**
     * 动态条件查询通知公告
     * @param title 公告标题
     * @param status 公告状态
     * @return 通知公告列表
     */
    List<Announcement> list(String title, String status);
    
    /**
     * 根据ID列表查询通知公告
     * @param ids 公告ID列表
     * @return 通知公告列表
     */
    List<Announcement> findByIds(List<Long> ids);
    
    /**
     * 批量删除通知公告
     * @param ids 公告ID列表
     * @return 删除的记录数
     */
    int batchDelete(List<Long> ids);
    
    /**
     * 查询通知公告总数
     * @return 通知公告总数
     */
    Long count();
    
    /**
     * 分页查询通知公告
     * @param page 页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    PageResult<Announcement> findPage(Integer page, Integer pageSize);
    
    /**
     * 分页查询通知公告
     * @param page 页码
     * @param pageSize 每页大小
     * @param title 公告标题
     * @param status 公告状态
     * @return 分页结果
     */
    PageResult<Announcement> findPage(Integer page, Integer pageSize, String title, String status);
    
    PageResult<Announcement> findPage(Integer page, Integer pageSize, String title, String status, String publisher, String excludePublisherRole);
    
    /**
     * 根据用户类型和用户信息获取公告列表
     * @param userType 用户类型：STUDENT/TEACHER
     * @param userInfo 用户信息（学生：majorId；教师：teacherType）
     * @param userId 当前用户ID，用于判断已读状态
     * @return 公告列表
     */
    List<Announcement> getAnnouncementsForUser(String userType, String userInfo, String userId);
    
    /**
     * 查询所有满足条件的公告（不分页）
     * @param title 公告标题筛选条件
     * @param status 公告状态筛选条件
     * @return 公告列表
     */
    List<Announcement> findAll(String title, String status);
    
    /**
     * 从Excel导入公告数据
     * @param announcementDataList Excel中解析的公告数据列表
     * @return 导入结果，包含成功和失败信息
     */
    Map<String, Object> importFromExcel(List<Map<String, Object>> announcementDataList);
    
    /**
     * 增加公告阅读次数
     * @param id 公告ID
     * @return 更新的记录数
     */
    int incrementReadCount(Long id);
}