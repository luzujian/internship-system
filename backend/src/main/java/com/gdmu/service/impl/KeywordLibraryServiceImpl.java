package com.gdmu.service.impl;

import com.gdmu.entity.KeywordLibrary;
import com.gdmu.exception.BusinessException;
import com.gdmu.mapper.KeywordLibraryMapper;
import com.gdmu.service.KeywordLibraryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class KeywordLibraryServiceImpl implements KeywordLibraryService {
    
    private final KeywordLibraryMapper keywordLibraryMapper;
    
    @Autowired
    public KeywordLibraryServiceImpl(KeywordLibraryMapper keywordLibraryMapper) {
        this.keywordLibraryMapper = keywordLibraryMapper;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insert(KeywordLibrary keywordLibrary) {
        log.debug("插入关键词库");
        
        if (keywordLibrary == null) {
            throw new BusinessException("关键词信息不能为空");
        }
        
        if (keywordLibrary.getKeyword() == null || keywordLibrary.getKeyword().trim().isEmpty()) {
            throw new BusinessException("关键词不能为空");
        }
        
        if (keywordLibrary.getCategory() == null || keywordLibrary.getCategory().trim().isEmpty()) {
            keywordLibrary.setCategory("internship");
        }
        
        if (keywordLibrary.getUsageType() == null || keywordLibrary.getUsageType().trim().isEmpty()) {
            keywordLibrary.setUsageType("internship");
        }
        
        if (keywordLibrary.getWeight() == null) {
            keywordLibrary.setWeight(80);
        }
        
        if (keywordLibrary.getStatus() == null) {
            keywordLibrary.setStatus(1);
        }
        
        if (keywordLibrary.getCreateTime() == null) {
            keywordLibrary.setCreateTime(new Date());
        }
        
        if (keywordLibrary.getUpdateTime() == null) {
            keywordLibrary.setUpdateTime(new Date());
        }
        
        if (keywordLibrary.getDeleted() == null) {
            keywordLibrary.setDeleted(0);
        }
        
        int result = keywordLibraryMapper.insert(keywordLibrary);
        log.info("关键词库插入成功，ID: {}", keywordLibrary.getId());
        return result;
    }
    
    @Override
    public List<KeywordLibrary> findAll() {
        log.debug("查询所有关键词库");
        return keywordLibraryMapper.findAll();
    }
    
    @Override
    public KeywordLibrary findById(Long id) {
        log.debug("根据ID查询关键词库，ID: {}", id);
        
        if (id == null || id <= 0) {
            throw new BusinessException("关键词ID无效");
        }
        
        return keywordLibraryMapper.findById(id);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int update(KeywordLibrary keywordLibrary) {
        log.debug("更新关键词库，ID: {}", keywordLibrary.getId());
        
        if (keywordLibrary == null || keywordLibrary.getId() == null) {
            throw new BusinessException("关键词信息或ID不能为空");
        }
        
        KeywordLibrary existing = keywordLibraryMapper.findById(keywordLibrary.getId());
        if (existing == null) {
            throw new BusinessException("关键词不存在");
        }
        
        keywordLibrary.setUpdateTime(new Date());
        
        if (keywordLibrary.getCategory() == null || keywordLibrary.getCategory().trim().isEmpty()) {
            keywordLibrary.setCategory(existing.getCategory());
        }
        
        if (keywordLibrary.getUsageType() == null || keywordLibrary.getUsageType().trim().isEmpty()) {
            keywordLibrary.setUsageType(existing.getUsageType());
        }
        
        if (keywordLibrary.getWeight() == null) {
            keywordLibrary.setWeight(existing.getWeight());
        }
        
        if (keywordLibrary.getDescription() == null) {
            keywordLibrary.setDescription(existing.getDescription());
        }
        
        if (keywordLibrary.getRelatedTags() == null) {
            keywordLibrary.setRelatedTags(existing.getRelatedTags());
        }
        
        int result = keywordLibraryMapper.update(keywordLibrary);
        log.info("关键词库更新成功，ID: {}", keywordLibrary.getId());
        return result;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteById(Long id) {
        log.debug("删除关键词库，ID: {}", id);
        
        if (id == null || id <= 0) {
            throw new BusinessException("关键词ID无效");
        }
        
        int result = keywordLibraryMapper.deleteById(id);
        log.info("关键词库删除成功，ID: {}", id);
        return result;
    }
    
    @Override
    public List<KeywordLibrary> findByCategory(String category) {
        log.debug("根据分类查询关键词库，分类: {}", category);
        return keywordLibraryMapper.findByCategory(category);
    }
    
    @Override
    public List<KeywordLibrary> findByUsageType(String usageType) {
        log.debug("根据使用类型查询关键词库，类型: {}", usageType);
        return keywordLibraryMapper.findByUsageType(usageType);
    }
    
    @Override
    public List<KeywordLibrary> findByStatus(Integer status) {
        log.debug("根据状态查询关键词库，状态: {}", status);
        return keywordLibraryMapper.findByStatus(status);
    }
    
    @Override
    public List<KeywordLibrary> searchByKeyword(String keyword) {
        log.debug("搜索关键词库，关键词: {}", keyword);
        return keywordLibraryMapper.searchByKeyword(keyword);
    }
}
