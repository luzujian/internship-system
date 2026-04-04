package com.gdmu.service.impl;

import com.gdmu.entity.AIAnalysisSettings;
import com.gdmu.exception.BusinessException;
import com.gdmu.mapper.AIAnalysisSettingsMapper;
import com.gdmu.service.AIAnalysisSettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class AIAnalysisSettingsServiceImpl implements AIAnalysisSettingsService {
    
    private final AIAnalysisSettingsMapper aiAnalysisSettingsMapper;
    
    @Autowired
    public AIAnalysisSettingsServiceImpl(AIAnalysisSettingsMapper aiAnalysisSettingsMapper) {
        this.aiAnalysisSettingsMapper = aiAnalysisSettingsMapper;
    }
    
    @Override
    public AIAnalysisSettings findLatest() {
        log.debug("查询最新的AI分析设置");
        return aiAnalysisSettingsMapper.findLatest();
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insert(AIAnalysisSettings settings) {
        log.debug("插入AI分析设置");
        
        if (settings == null) {
            throw new BusinessException("AI分析设置不能为空");
        }
        
        int result = aiAnalysisSettingsMapper.insert(settings);
        log.info("AI分析设置插入成功，ID: {}", settings.getId());
        return result;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int update(AIAnalysisSettings settings) {
        log.debug("更新AI分析设置，ID: {}", settings.getId());
        
        if (settings == null || settings.getId() == null) {
            throw new BusinessException("AI分析设置或ID不能为空");
        }
        
        int result = aiAnalysisSettingsMapper.update(settings);
        log.info("AI分析设置更新成功，ID: {}", settings.getId());
        return result;
    }
}
