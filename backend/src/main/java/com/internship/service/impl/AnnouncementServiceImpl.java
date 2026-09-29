package com.internship.service.impl;

import com.internship.entity.Announcement;
import com.internship.entity.PageResult;
import com.internship.exception.BusinessException;
import com.internship.mapper.AnnouncementMapper;
import com.internship.service.AnnouncementService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 通知公告服务实现类
 */
@Slf4j
@Service
public class AnnouncementServiceImpl implements AnnouncementService {
    
    private final AnnouncementMapper announcementMapper;
    @Autowired
    private com.internship.service.AnnouncementAttachmentPolicy attachmentPolicy;

    private void validateAttachments(Announcement next, Announcement previous) {
        if (next.getAttachments() != null && !next.getAttachments().isBlank() && !"[]".equals(next.getAttachments()))
            attachmentPolicy.validate(next.getAttachments(), previous == null ? null : previous.getAttachments(), previous == null ? null : previous.getId());
    }
    
    @Autowired
    public AnnouncementServiceImpl(AnnouncementMapper announcementMapper) {
        this.announcementMapper = announcementMapper;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insert(Announcement announcement) {
        log.debug("插入通知公告: {}", announcement.getTitle());
        com.internship.service.AnnouncementOwnership.bindCreator(announcement);
        validateAttachments(announcement, null);
        
        // 发布时间由系统在真正发布时写入，不接受前端指定
        Date now = new Date();
        announcement.setPriority("normal");
        announcement.setValidTo(null);
        announcement.setValidFrom(now);
        announcement.setPublishTime("PUBLISHED".equals(announcement.getStatus()) ? now : null);

        // 参数校验
        validateAnnouncementInfo(announcement);
        
        int result = announcementMapper.insert(announcement);
        log.info("通知公告插入成功，标题: {}", announcement.getTitle());
        return result;
    }
    
    @Override
    public Announcement findById(Long id) {
        log.debug("根据ID查询通知公告，ID: {}", id);
        
        // 参数校验
        if (id == null || id <= 0) {
            throw new BusinessException("公告ID无效");
        }
        
        return announcementMapper.findById(id);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int update(Announcement announcement) {
        log.debug("更新通知公告，ID: {}", announcement.getId());
        
        // 参数校验
        if (announcement.getId() == null || announcement.getId() <= 0) {
            throw new BusinessException("公告ID无效");
        }
        
        // 检查公告是否存在
        Announcement existingAnnouncement = announcementMapper.findById(announcement.getId());
        if (existingAnnouncement == null) {
            throw new BusinessException("公告不存在");
        }
        announcement.setPublisherUserId(existingAnnouncement.getPublisherUserId());
        announcement.setPublisherUserType(existingAnnouncement.getPublisherUserType());
        validateAttachments(announcement, existingAnnouncement);
        
        // 编辑时不接受前端指定发布时间；发布时间只在草稿转发布时更新
        announcement.setPriority("normal");
        announcement.setValidTo(null);
        boolean newlyPublished = "PUBLISHED".equals(announcement.getStatus())
                && !"PUBLISHED".equals(existingAnnouncement.getStatus());
        Date publishAt = new Date();
        announcement.setPublishTime(newlyPublished ? publishAt : existingAnnouncement.getPublishTime());
        announcement.setValidFrom(newlyPublished ? publishAt : existingAnnouncement.getValidFrom());

        // 参数校验
        validateAnnouncementInfo(announcement);
        
        int result = announcementMapper.update(announcement);
        log.info("通知公告更新成功，标题: {}", announcement.getTitle());
        return result;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        log.debug("删除通知公告，ID: {}", id);
        
        // 参数校验
        if (id == null || id <= 0) {
            throw new BusinessException("公告ID无效");
        }
        
        // 检查公告是否存在
        Announcement announcement = announcementMapper.findById(id);
        if (announcement == null) {
            throw new BusinessException("公告不存在");
        }
        
        int result = announcementMapper.deleteById(id);
        log.info("通知公告删除成功，标题: {}", announcement.getTitle());
        return result;
    }
    
    @Override
    public List<Announcement> findAll() {
        log.debug("查询所有通知公告");
        return announcementMapper.findAll();
    }
    
    @Override
    public List<Announcement> list(String title, String status) {
        log.debug("动态条件查询通知公告，标题: {}, 状态: {}", title, status);
        return announcementMapper.listByTitleAndStatus(title, status);
    }
    
    @Override
    public List<Announcement> findByIds(List<Long> ids) {
        log.debug("根据ID列表查询通知公告，ID列表: {}", ids);
        
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        
        return announcementMapper.selectByIds(ids);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchDelete(List<Long> ids) {
        log.debug("批量删除通知公告，ID列表: {}", ids);
        
        // 参数校验
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("公告ID列表不能为空");
        }
        
        int result = announcementMapper.batchDeleteByIds(ids);
        log.info("批量删除通知公告成功，删除数量: {}", result);
        return result;
    }
    
    @Override
    public Long count() {
        log.debug("查询通知公告总数");
        return announcementMapper.count();
    }
    
    @Override
    public PageResult<Announcement> findPage(Integer page, Integer pageSize) {
        log.debug("分页查询通知公告，页码: {}, 每页大小: {}", page, pageSize);
        return findPage(page, pageSize, null, null);
    }
    
    @Override
    public PageResult<Announcement> findPage(Integer page, Integer pageSize, String title, String status) {
        log.debug("分页查询通知公告，页码: {}, 每页大小: {}, 标题: {}, 状态: {}", 
                page, pageSize, title, status);
        
        return findPage(page, pageSize, title, status, null, null);
    }
    
    @Override
    public PageResult<Announcement> findPage(Integer page, Integer pageSize, String title, String status, String publisher, String excludePublisherRole) {
        return findPage(page, pageSize, title, status, publisher, excludePublisherRole, null, null);
    }

    @Override
    public PageResult<Announcement> findPage(Integer page, Integer pageSize, String title, String status,
                                            String publisher, String excludePublisherRole, String userId,
                                            String userType) {
        log.debug("分页查询通知公告，页码: {}, 每页大小: {}, 标题: {}, 状态: {}, 发布人: {}, 排除角色: {}, 用户ID: {}",
                page, pageSize, title, status, publisher, excludePublisherRole, userId, userType);

        PageHelper.startPage(page, pageSize);
        List<Announcement> announcements = announcementMapper.list(
                title, status, publisher, excludePublisherRole, userId, userType);

        PageInfo<Announcement> pageInfo = new PageInfo<>(announcements);
        return PageResult.build(pageInfo.getTotal(), pageInfo.getList(),
                pageInfo.getPages(), pageInfo.getPageNum(), pageInfo.getPageSize());
    }
    
    /**
     * 验证通知公告信息
     */
    private void validateAnnouncementInfo(Announcement announcement) {
        if (announcement == null) {
            throw new BusinessException("公告信息不能为空");
        }
        
        if (StringUtils.isBlank(announcement.getTitle())) {
            throw new BusinessException("公告标题不能为空");
        }
        
        if (StringUtils.isBlank(announcement.getContent())) {
            throw new BusinessException("公告内容不能为空");
        }
        
        if (StringUtils.isBlank(announcement.getPublisher())) {
            throw new BusinessException("发布人不能为空");
        }
        
        if (StringUtils.isBlank(announcement.getStatus())) {
            throw new BusinessException("公告状态不能为空");
        }
        
        // 验证目标群体必填
        if (StringUtils.isBlank(announcement.getTargetType())) {
            throw new BusinessException("目标群体不能为空");
        }
    }
    
    @Override
    public List<Announcement> getAnnouncementsForUser(String userType, String userInfo, String userId,
                                                     String readerType) {
        log.debug("根据用户类型获取公告列表：userType={}, readerType={}, hasUserId={}",
                userType, readerType, userId != null && !userId.isBlank());
        return announcementMapper.getAnnouncementsForUser(userType, userInfo, userId, readerType);
    }

    @Override
    public List<Announcement> listForViewer(String title, String status, String publisher,
                                           String excludePublisherRole, String userId, String userType) {
        return announcementMapper.list(title, status, publisher, excludePublisherRole, userId, userType);
    }
    
    @Override
    public List<Announcement> findAll(String title, String status) {
        log.debug("查询所有满足条件的公告，标题: {}, 状态: {}", title, status);
        return announcementMapper.listByTitleAndStatus(title, status);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importFromExcel(List<Map<String, Object>> announcementDataList) {
        log.info("开始从Excel导入公告数据，总数据量: {}", announcementDataList != null ? announcementDataList.size() : 0);
        
        int successCount = 0;
        int failCount = 0;
        List<Map<String, Object>> failList = new ArrayList<>();
        
        if (announcementDataList == null || announcementDataList.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("successCount", successCount);
            result.put("failCount", failCount);
            result.put("failList", failList);
            return result;
        }
        
        for (int i = 0; i < announcementDataList.size(); i++) {
            Map<String, Object> rowData = announcementDataList.get(i);
            Map<String, Object> failInfo = new HashMap<>();
            failInfo.put("rowNum", i + 2);
            failInfo.putAll(rowData);
            
            try {
                Announcement announcement = convertToAnnouncement(rowData);
                validateAnnouncementInfo(announcement);
                
                com.internship.service.AnnouncementOwnership.bindCreator(announcement);
                validateAttachments(announcement, null);
                int result = announcementMapper.insert(announcement);
                if (result > 0) {
                    successCount++;
                } else {
                    failInfo.put("errorMsg", "插入失败");
                    failList.add(failInfo);
                    failCount++;
                }
            } catch (Exception e) {
                log.error("导入公告数据失败，行号: {}", i + 2, e);
                failInfo.put("errorMsg", e.getMessage());
                failList.add(failInfo);
                failCount++;
            }
        }
        
        log.info("Excel公告数据导入完成，成功: {}, 失败: {}", successCount, failCount);
        
        Map<String, Object> result = new HashMap<>();
        result.put("successCount", successCount);
        result.put("failCount", failCount);
        result.put("failList", failList);
        
        return result;
    }
    
    private Announcement convertToAnnouncement(Map<String, Object> rowData) {
        log.debug("处理Excel行数据: {}", rowData);
        Announcement announcement = new Announcement();
        
        String title = getStringValue(rowData, "标题");
        String content = getStringValue(rowData, "内容");
        String publisher = getStringValue(rowData, "发布人");
        String publisherRole = getStringValue(rowData, "发布人身份");
        String status = getStringValue(rowData, "状态");
        String targetType = getStringValue(rowData, "目标群体");
        String targetValue = getStringValue(rowData, "目标值");
        
        announcement.setTitle(title);
        announcement.setContent(content);
        announcement.setPublisher(publisher);
        announcement.setPublisherRole(convertPublisherRoleToEnglish(publisherRole));
        announcement.setStatus(convertStatusToEnglish(status));
        announcement.setPriority("normal");
        announcement.setTargetType(convertTargetTypeToEnglish(targetType));
        announcement.setTargetValue(targetValue);
        announcement.setPublishTime(new Date());
        announcement.setValidFrom(new Date());
        announcement.setReadCount(0);
        
        return announcement;
    }
    
    private String convertPublisherRoleToEnglish(String publisherRole) {
        if (StringUtils.isBlank(publisherRole)) {
            return null;
        }
        if ("管理员".equals(publisherRole)) {
            return "ADMIN";
        } else if ("学院教师".equals(publisherRole)) {
            return "COLLEGE";
        } else if ("系室教师".equals(publisherRole)) {
            return "DEPARTMENT";
        } else if ("辅导员".equals(publisherRole)) {
            return "COUNSELOR";
        } else {
            return publisherRole;
        }
    }
    
    private String convertStatusToEnglish(String status) {
        if (StringUtils.isBlank(status)) {
            return null;
        }
        if ("草稿".equals(status)) {
            return "DRAFT";
        } else if ("已发布".equals(status)) {
            return "PUBLISHED";
        } else if ("已过期".equals(status)) {
            return "EXPIRED";
        } else {
            return status;
        }
    }
    
    private String convertTargetTypeToEnglish(String targetType) {
        if (StringUtils.isBlank(targetType)) {
            return null;
        }
        if ("全体师生".equals(targetType)) {
            return "ALL";
        } else if ("全体学生".equals(targetType)) {
            return "STUDENT";
        } else if ("全体教师".equals(targetType)) {
            return "TEACHER";
        } else if ("特定教师类别".equals(targetType)) {
            return "TEACHER_TYPE";
        } else if ("特定专业学生".equals(targetType)) {
            return "MAJOR";
        } else if ("企业用户".equals(targetType)) {
            return "COMPANY";
        } else {
            return targetType;
        }
    }

    /**
     * 从 JSON 数组中转换目标类型（用于 Excel 导入）
     */
    private String convertTargetTypesToEnglish(List<String> targetTypes) {
        if (targetTypes == null || targetTypes.isEmpty()) {
            return null;
        }
        List<String> englishTypes = new ArrayList<>();
        for (String type : targetTypes) {
            String english = convertTargetTypeToEnglish(type);
            if (english != null) {
                englishTypes.add(english);
            }
        }
        if (englishTypes.isEmpty()) {
            return null;
        }
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writeValueAsString(englishTypes);
        } catch (JsonProcessingException e) {
            log.error("序列化 JSON 失败：{}", e.getMessage());
            return null;
        }
    }
    
    private String getStringValue(Map<String, Object> rowData, String key) {
        Object value = rowData.get(key);
        if (value == null) {
            return null;
        }
        return value.toString().trim();
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int incrementReadCount(Long id) {
        log.debug("增加公告阅读次数，ID: {}", id);
        
        // 参数校验
        if (id == null || id <= 0) {
            throw new BusinessException("公告ID无效");
        }
        
        // 检查公告是否存在
        Announcement announcement = announcementMapper.findById(id);
        if (announcement == null) {
            throw new BusinessException("公告不存在");
        }
        
        // 增加阅读次数
        int result = announcementMapper.incrementReadCount(id);
        log.info("公告阅读次数增加成功，ID: {}", id);
        return result;
    }
}
