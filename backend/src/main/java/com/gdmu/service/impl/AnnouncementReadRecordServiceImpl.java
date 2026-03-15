package com.gdmu.service.impl;

import com.gdmu.entity.AnnouncementReadRecord;
import com.gdmu.exception.BusinessException;
import com.gdmu.mapper.AnnouncementReadRecordMapper;
import com.gdmu.service.AnnouncementReadRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * 公告阅读记录服务实现类
 */
@Slf4j
@Service
public class AnnouncementReadRecordServiceImpl implements AnnouncementReadRecordService {
    
    private final AnnouncementReadRecordMapper announcementReadRecordMapper;
    
    @Autowired
    public AnnouncementReadRecordServiceImpl(AnnouncementReadRecordMapper announcementReadRecordMapper) {
        this.announcementReadRecordMapper = announcementReadRecordMapper;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insert(AnnouncementReadRecord record) {
        log.debug("插入公告阅读记录: 公告ID: {}, 用户ID: {}", record.getAnnouncementId(), record.getUserId());
        
        // 参数校验
        validateRecordInfo(record);
        
        // 检查是否已存在阅读记录
        AnnouncementReadRecord existingRecord = announcementReadRecordMapper.findByAnnouncementAndUser(
                record.getAnnouncementId(), record.getUserId(), record.getUserType());
        if (existingRecord != null) {
            log.info("公告阅读记录已存在，无需重复插入");
            return 0;
        }
        
        int result = announcementReadRecordMapper.insert(record);
        log.info("公告阅读记录插入成功");
        return result;
    }
    
    @Override
    public AnnouncementReadRecord findById(Long id) {
        log.debug("根据ID查询公告阅读记录，ID: {}", id);
        
        // 参数校验
        if (id == null || id <= 0) {
            throw new BusinessException("记录ID无效");
        }
        
        return announcementReadRecordMapper.findById(id);
    }
    
    @Override
    public AnnouncementReadRecord findByAnnouncementAndUser(Long announcementId, String userId, String userType) {
        log.info("根据公告ID和用户查询阅读记录，公告ID: {}, 用户ID: {}, 用户类型: {}", 
                announcementId, userId, userType);
        
        // 参数校验
        if (announcementId == null || announcementId <= 0) {
            throw new BusinessException("公告ID无效");
        }
        if (userId == null || userId.isEmpty()) {
            throw new BusinessException("用户ID不能为空");
        }
        if (userType == null || userType.isEmpty()) {
            throw new BusinessException("用户类型不能为空");
        }
        
        AnnouncementReadRecord record = announcementReadRecordMapper.findByAnnouncementAndUser(announcementId, userId, userType);
        log.info("查询结果: {}", record != null ? "找到记录" : "未找到记录");
        return record;
    }
    
    @Override
    public Long countUnreadByAnnouncementId(Long announcementId) {
        log.debug("查询公告的未读人数，公告ID: {}", announcementId);
        
        // 参数校验
        if (announcementId == null || announcementId <= 0) {
            throw new BusinessException("公告ID无效");
        }
        
        // 这里需要根据实际业务逻辑实现，暂时返回0
        return 0L;
    }
    
    @Override
    public Long countReadByAnnouncementId(Long announcementId) {
        log.debug("查询公告的已读人数，公告ID: {}", announcementId);
        
        // 参数校验
        if (announcementId == null || announcementId <= 0) {
            throw new BusinessException("公告ID无效");
        }
        
        // 使用现有的countByAnnouncementId方法
        return announcementReadRecordMapper.countByAnnouncementId(announcementId);
    }
    
    @Override
    public Long countUnreadByUserId(Long userId, String userType) {
        log.debug("查询用户的未读公告数量，用户ID: {}, 用户类型: {}", userId, userType);
        
        // 参数校验
        if (userId == null || userId <= 0) {
            throw new BusinessException("用户ID无效");
        }
        if (userType == null || userType.isEmpty()) {
            throw new BusinessException("用户类型不能为空");
        }
        
        // 这里需要根据实际业务逻辑实现，暂时返回0
        return 0L;
    }
    
    @Override
    public List<AnnouncementReadRecord> findByAnnouncementId(Long announcementId) {
        log.debug("查询公告的阅读记录列表，公告ID: {}", announcementId);
        
        // 参数校验
        if (announcementId == null || announcementId <= 0) {
            throw new BusinessException("公告ID无效");
        }
        
        List<AnnouncementReadRecord> records = announcementReadRecordMapper.findByAnnouncementId(announcementId);
        return records != null ? records : Collections.emptyList();
    }
    
    /**
     * 验证公告阅读记录信息
     */
    private void validateRecordInfo(AnnouncementReadRecord record) {
        if (record == null) {
            throw new BusinessException("阅读记录信息不能为空");
        }
        
        if (record.getAnnouncementId() == null || record.getAnnouncementId() <= 0) {
            throw new BusinessException("公告ID不能为空");
        }
        
        if (record.getUserId() == null || record.getUserId().isEmpty()) {
            throw new BusinessException("用户ID不能为空");
        }
        
        if (record.getUserType() == null || record.getUserType().isEmpty()) {
            throw new BusinessException("用户类型不能为空");
        }
    }
}
