package com.gdmu.service;

import com.gdmu.entity.SystemConfig;
import java.util.List;

public interface SystemConfigService {
    
    int insert(SystemConfig systemConfig);
    
    List<SystemConfig> findAll();
    
    SystemConfig findById(Long id);
    
    SystemConfig findByConfigKey(String configKey);
    
    int update(SystemConfig systemConfig);
    
    int deleteById(Long id);
    
    List<SystemConfig> findByStatus(Integer status);
    
    List<SystemConfig> searchByConfigName(String configName);
    
    String getConfigValue(String configKey);
    
    Integer getConfigValueAsInt(String configKey);
}
