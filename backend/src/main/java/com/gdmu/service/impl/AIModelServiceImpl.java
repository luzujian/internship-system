package com.gdmu.service.impl;

import com.gdmu.entity.AIModel;
import com.gdmu.exception.BusinessException;
import com.gdmu.mapper.AIModelMapper;
import com.gdmu.service.AIConfigRefreshService;
import com.gdmu.service.AIModelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class AIModelServiceImpl implements AIModelService {
    
    private final AIModelMapper aiModelMapper;
    
    @Autowired
    private AIConfigRefreshService aiConfigRefreshService;
    
    @Autowired
    public AIModelServiceImpl(AIModelMapper aiModelMapper) {
        this.aiModelMapper = aiModelMapper;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insert(AIModel aiModel) {
        log.debug("插入AI模型配置");
        
        if (aiModel == null) {
            throw new BusinessException("AI模型信息不能为空");
        }
        
        if (aiModel.getModelName() == null || aiModel.getModelName().trim().isEmpty()) {
            throw new BusinessException("模型名称不能为空");
        }
        
        if (aiModel.getModelCode() == null || aiModel.getModelCode().trim().isEmpty()) {
            throw new BusinessException("模型代码不能为空");
        }
        
        AIModel existingModel = aiModelMapper.findByModelCode(aiModel.getModelCode());
        if (existingModel != null) {
            throw new BusinessException("模型代码已存在");
        }
        
        if (aiModel.getProvider() == null || aiModel.getProvider().trim().isEmpty()) {
            throw new BusinessException("提供商不能为空");
        }
        
        String provider = aiModel.getProvider().toLowerCase().trim();
        String[] allowedProviders = {"deepseek", "wenxin", "qwen", "zhipu", "moonshot", "other"};
        boolean isAllowed = false;
        for (String allowedProvider : allowedProviders) {
            if (provider.equals(allowedProvider)) {
                isAllowed = true;
                break;
            }
        }
        if (!isAllowed) {
            throw new BusinessException("只允许选择国内的AI模型提供商");
        }
        
        if (aiModel.getStatus() == null) {
            aiModel.setStatus(1);
        }
        
        if (aiModel.getIsDefault() == null) {
            aiModel.setIsDefault(0);
        }
        
        if (aiModel.getMaxTokens() == null) {
            aiModel.setMaxTokens(4096);
        } else if (aiModel.getMaxTokens() < 1 || aiModel.getMaxTokens() > 8192) {
            throw new BusinessException("最大Token数必须在 [1, 8192] 范围内");
        }
        
        if (aiModel.getTemperature() == null) {
            aiModel.setTemperature(new java.math.BigDecimal("0.70"));
        }
        
        int result = aiModelMapper.insert(aiModel);
        log.info("AI模型配置插入成功，ID: {}", aiModel.getId());
        
        try {
            aiConfigRefreshService.refreshAIConfig();
            log.info("AI模型配置已刷新，新配置将立即生效");
        } catch (Exception e) {
            log.error("刷新AI模型配置失败，但数据已更新", e);
        }
        
        return result;
    }
    
    @Override
    public List<AIModel> findAll() {
        log.debug("查询所有AI模型配置");
        return aiModelMapper.findAll();
    }
    
    @Override
    public AIModel findById(Long id) {
        log.debug("根据ID查询AI模型配置，ID: {}", id);
        
        if (id == null || id <= 0) {
            throw new BusinessException("AI模型ID无效");
        }
        
        return aiModelMapper.findById(id);
    }
    
    @Override
    public AIModel findByModelCode(String modelCode) {
        log.debug("根据模型代码查询AI模型配置，模型代码: {}", modelCode);
        return aiModelMapper.findByModelCode(modelCode);
    }
    
    @Override
    public List<AIModel> findEnabledModels() {
        log.debug("查询启用的AI模型配置");
        return aiModelMapper.findEnabledModels();
    }
    
    @Override
    public AIModel findDefaultModel() {
        log.debug("查询默认AI模型配置");
        return aiModelMapper.findDefaultModel();
    }
    
    @Override
    public List<AIModel> findByProvider(String provider) {
        log.debug("根据提供商查询AI模型配置，提供商: {}", provider);
        return aiModelMapper.findByProvider(provider);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int update(AIModel aiModel) {
        log.debug("更新AI模型配置，ID: {}", aiModel.getId());
        
        if (aiModel == null || aiModel.getId() == null) {
            throw new BusinessException("AI模型信息或ID不能为空");
        }
        
        AIModel existing = aiModelMapper.findById(aiModel.getId());
        if (existing == null) {
            throw new BusinessException("AI模型不存在");
        }
        
        if (aiModel.getModelCode() != null && !aiModel.getModelCode().equals(existing.getModelCode())) {
            AIModel modelWithSameCode = aiModelMapper.findByModelCode(aiModel.getModelCode());
            if (modelWithSameCode != null && !modelWithSameCode.getId().equals(aiModel.getId())) {
                throw new BusinessException("模型代码已存在");
            }
        }
        
        if (aiModel.getProvider() != null && !aiModel.getProvider().equals(existing.getProvider())) {
            String provider = aiModel.getProvider().toLowerCase().trim();
            String[] allowedProviders = {"deepseek", "wenxin", "qwen", "zhipu", "moonshot", "other"};
            boolean isAllowed = false;
            for (String allowedProvider : allowedProviders) {
                if (provider.equals(allowedProvider)) {
                    isAllowed = true;
                    break;
                }
            }
            if (!isAllowed) {
                throw new BusinessException("只允许选择国内的AI模型提供商");
            }
        }
        
        if (aiModel.getMaxTokens() != null && (aiModel.getMaxTokens() < 1 || aiModel.getMaxTokens() > 8192)) {
            throw new BusinessException("最大Token数必须在 [1, 8192] 范围内");
        }
        
        int result = aiModelMapper.update(aiModel);
        log.info("AI模型配置更新成功，ID: {}", aiModel.getId());
        
        try {
            aiConfigRefreshService.refreshAIConfig();
            log.info("AI模型配置已刷新，新配置将立即生效");
        } catch (Exception e) {
            log.error("刷新AI模型配置失败，但数据已更新", e);
        }
        
        return result;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int setAsDefault(Long id, String updater) {
        log.debug("设置默认AI模型，ID: {}", id);
        
        if (id == null || id <= 0) {
            throw new BusinessException("AI模型ID无效");
        }
        
        AIModel existing = aiModelMapper.findById(id);
        if (existing == null) {
            throw new BusinessException("AI模型不存在");
        }
        
        if (existing.getStatus() == 0) {
            throw new BusinessException("无法将禁用的模型设置为默认");
        }
        
        aiModelMapper.clearDefaultModel(updater);
        int result = aiModelMapper.setAsDefault(id, updater);
        log.info("AI模型设置为默认成功，ID: {}", id);
        
        try {
            aiConfigRefreshService.refreshAIConfig();
            log.info("AI模型配置已刷新，新配置将立即生效");
        } catch (Exception e) {
            log.error("刷新AI模型配置失败，但数据已更新", e);
        }
        
        return result;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteById(Long id) {
        log.debug("删除AI模型配置，ID: {}", id);
        
        if (id == null || id <= 0) {
            throw new BusinessException("AI模型ID无效");
        }
        
        AIModel existing = aiModelMapper.findById(id);
        if (existing == null) {
            throw new BusinessException("AI模型不存在");
        }
        
        if (existing.getIsDefault() == 1) {
            throw new BusinessException("无法删除默认模型，请先设置其他模型为默认");
        }
        
        int result = aiModelMapper.deleteById(id, "admin");
        log.info("AI模型配置删除成功，ID: {}", id);
        
        try {
            aiConfigRefreshService.refreshAIConfig();
            log.info("AI模型配置已刷新，新配置将立即生效");
        } catch (Exception e) {
            log.error("刷新AI模型配置失败，但数据已更新", e);
        }
        
        return result;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteAll(String updater) {
        log.debug("删除所有AI模型配置");
        
        List<AIModel> allModels = aiModelMapper.findAll();
        if (allModels == null || allModels.isEmpty()) {
            log.info("没有AI模型需要删除");
            return 0;
        }
        
        int deletedCount = 0;
        for (AIModel model : allModels) {
            try {
                aiModelMapper.deleteById(model.getId(), updater);
                deletedCount++;
                log.debug("删除AI模型配置成功，ID: {}", model.getId());
            } catch (Exception e) {
                log.error("删除AI模型配置失败，ID: {}, 错误: {}", model.getId(), e.getMessage());
            }
        }
        
        log.info("批量删除AI模型配置完成，共删除 {} 个模型", deletedCount);
        
        try {
            aiConfigRefreshService.refreshAIConfig();
            log.info("AI模型配置已刷新，新配置将立即生效");
        } catch (Exception e) {
            log.error("刷新AI模型配置失败，但数据已更新", e);
        }
        
        return deletedCount;
    }
    
    @Override
    public List<AIModel> searchByKeyword(String keyword) {
        log.debug("搜索AI模型配置，关键词: {}", keyword);
        return aiModelMapper.searchByKeyword(keyword);
    }
    
    @Override
    public List<AIModel> findByStatus(Integer status) {
        log.debug("根据状态查询AI模型配置，状态: {}", status);
        return aiModelMapper.findByStatus(status);
    }
}
