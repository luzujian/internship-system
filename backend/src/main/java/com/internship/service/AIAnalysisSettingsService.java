package com.internship.service;

import com.internship.entity.AIAnalysisSettings;

public interface AIAnalysisSettingsService {
    
    AIAnalysisSettings findLatest();
    
    int insert(AIAnalysisSettings settings);
    
    int update(AIAnalysisSettings settings);
}
