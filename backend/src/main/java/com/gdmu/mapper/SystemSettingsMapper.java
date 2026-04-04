package com.gdmu.mapper;

import com.gdmu.entity.SystemSettings;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SystemSettingsMapper {
    
    SystemSettings findById(Long id);
    
    SystemSettings findLatest();
    
    SystemSettings getLatestSettings();
    
    int insert(SystemSettings systemSettings);
    
    int update(SystemSettings systemSettings);
    
    int deleteById(Long id);
}
