package com.gdmu.mapper;

import com.gdmu.entity.AIAnalysisSettings;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AIAnalysisSettingsMapper {
    
    AIAnalysisSettings findLatest();
    
    int insert(AIAnalysisSettings settings);
    
    int update(AIAnalysisSettings settings);
}
