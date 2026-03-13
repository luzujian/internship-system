package com.gdmu.service.impl;

import com.gdmu.entity.SystemConfig;
import com.gdmu.exception.BusinessException;
import com.gdmu.mapper.SystemConfigMapper;
import com.gdmu.service.SystemConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class SystemConfigServiceImpl implements SystemConfigService {
    
    private final SystemConfigMapper systemConfigMapper;
    
    @Autowired
    public SystemConfigServiceImpl(SystemConfigMapper systemConfigMapper) {
        this.systemConfigMapper = systemConfigMapper;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insert(SystemConfig systemConfig) {
        log.debug("插入系统配置");
        
        if (systemConfig == null) {
            throw new BusinessException("配置信息不能为空");
        }
        
        if (systemConfig.getConfigKey() == null || systemConfig.getConfigKey().trim().isEmpty()) {
            throw new BusinessException("配置键不能为空");
        }
        
        if (systemConfig.getConfigValue() == null || systemConfig.getConfigValue().trim().isEmpty()) {
            throw new BusinessException("配置值不能为空");
        }
        
        if (systemConfig.getConfigName() == null || systemConfig.getConfigName().trim().isEmpty()) {
            throw new BusinessException("配置名称不能为空");
        }
        
        if (systemConfig.getConfigType() == null) {
            systemConfig.setConfigType("string");
        }
        
        if (systemConfig.getStatus() == null) {
            systemConfig.setStatus(1);
        }
        
        if (systemConfig.getCreateTime() == null) {
            systemConfig.setCreateTime(new Date());
        }
        
        if (systemConfig.getUpdateTime() == null) {
            systemConfig.setUpdateTime(new Date());
        }
        
        if (systemConfig.getDeleted() == null) {
            systemConfig.setDeleted(0);
        }
        
        int result = systemConfigMapper.insert(systemConfig);
        log.info("系统配置插入成功，ID: {}", systemConfig.getId());
        return result;
    }
    
    @Override
    public List<SystemConfig> findAll() {
        log.debug("查询所有系统配置");
        return systemConfigMapper.findAll();
    }
    
    @Override
    public SystemConfig findById(Long id) {
        log.debug("根据ID查询系统配置，ID: {}", id);
        
        if (id == null || id <= 0) {
            throw new BusinessException("配置ID无效");
        }
        
        return systemConfigMapper.findById(id);
    }
    
    @Override
    public SystemConfig findByConfigKey(String configKey) {
        log.debug("根据配置键查询系统配置，键: {}", configKey);
        return systemConfigMapper.findByConfigKey(configKey);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int update(SystemConfig systemConfig) {
        log.debug("更新系统配置，ID: {}", systemConfig.getId());
        
        if (systemConfig == null || systemConfig.getId() == null) {
            throw new BusinessException("配置信息或ID不能为空");
        }
        
        SystemConfig existing = systemConfigMapper.findById(systemConfig.getId());
        if (existing == null) {
            throw new BusinessException("配置不存在");
        }
        
        systemConfig.setUpdateTime(new Date());
        
        int result = systemConfigMapper.update(systemConfig);
        log.info("系统配置更新成功，ID: {}", systemConfig.getId());
        return result;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteById(Long id) {
        log.debug("删除系统配置，ID: {}", id);
        
        if (id == null || id <= 0) {
            throw new BusinessException("配置ID无效");
        }
        
        int result = systemConfigMapper.deleteById(id);
        log.info("系统配置删除成功，ID: {}", id);
        return result;
    }
    
    @Override
    public List<SystemConfig> findByStatus(Integer status) {
        log.debug("根据状态查询系统配置，状态: {}", status);
        return systemConfigMapper.findByStatus(status);
    }
    
    @Override
    public List<SystemConfig> searchByConfigName(String configName) {
        log.debug("搜索系统配置，配置名称: {}", configName);
        return systemConfigMapper.searchByConfigName(configName);
    }
    
    @Override
    public String getConfigValue(String configKey) {
        log.debug("获取配置值，键: {}", configKey);
        
        SystemConfig config = systemConfigMapper.findByConfigKey(configKey);
        if (config == null) {
            log.warn("配置不存在，键: {}", configKey);
            return null;
        }
        
        if (config.getStatus() == null || config.getStatus() != 1) {
            log.warn("配置已禁用，键: {}", configKey);
            return null;
        }
        
        return config.getConfigValue();
    }
    
    @Override
    public Integer getConfigValueAsInt(String configKey) {
        String value = getConfigValue(configKey);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.error("配置值转换为整数失败，键: {}, 值: {}", configKey, value);
            return null;
        }
    }
}
