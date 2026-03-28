package com.gdmu.service;

import com.gdmu.entity.AIAnalysisSettings;

public interface AIAnalysisSettingsService {
    
    AIAnalysisSettings findLatest();
    
    int insert(AIAnalysisSettings settings);
    
    int update(AIAnalysisSettings settings);
}
