package com.gdmu.service.impl;

import com.gdmu.entity.SystemSettings;
import com.gdmu.exception.BusinessException;
import com.gdmu.mapper.SystemSettingsMapper;
import com.gdmu.service.SystemSettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class SystemSettingsServiceImpl implements SystemSettingsService {
    
    private final SystemSettingsMapper systemSettingsMapper;
    
    @Autowired
    public SystemSettingsServiceImpl(SystemSettingsMapper systemSettingsMapper) {
        this.systemSettingsMapper = systemSettingsMapper;
    }
    
    @Override
    public SystemSettings findById(Long id) {
        log.debug("根据ID查询系统设置，ID: {}", id);
        
        if (id == null || id <= 0) {
            throw new BusinessException("设置ID无效");
        }
        
        return systemSettingsMapper.findById(id);
    }
    
    @Override
    public SystemSettings findLatest() {
        log.debug("查询最新的系统设置");
        return systemSettingsMapper.findLatest();
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insert(SystemSettings systemSettings) {
        log.debug("插入系统设置");
        
        if (systemSettings == null) {
            throw new BusinessException("系统设置不能为空");
        }
        
        int result = systemSettingsMapper.insert(systemSettings);
        log.info("系统设置插入成功，ID: {}", systemSettings.getId());
        return result;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int update(SystemSettings systemSettings) {
        log.debug("更新系统设置，ID: {}", systemSettings.getId());
        
        if (systemSettings == null || systemSettings.getId() == null) {
            throw new BusinessException("系统设置或ID不能为空");
        }
        
        SystemSettings existing = systemSettingsMapper.findById(systemSettings.getId());
        if (existing == null) {
            throw new BusinessException("系统设置不存在");
        }
        
        int result = systemSettingsMapper.update(systemSettings);
        log.info("系统设置更新成功，ID: {}", systemSettings.getId());
        return result;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        log.debug("删除系统设置，ID: {}", id);
        
        if (id == null || id <= 0) {
            throw new BusinessException("设置ID无效");
        }
        
        int result = systemSettingsMapper.deleteById(id);
        log.info("系统设置删除成功，ID: {}", id);
        return result;
    }
}
