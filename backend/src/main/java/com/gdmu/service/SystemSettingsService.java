package com.gdmu.service;

import com.gdmu.entity.SystemSettings;

public interface SystemSettingsService {
    
    SystemSettings findById(Long id);
    
    SystemSettings findLatest();
    
    int insert(SystemSettings systemSettings);
    
    int update(SystemSettings systemSettings);
    
    int delete(Long id);
}
