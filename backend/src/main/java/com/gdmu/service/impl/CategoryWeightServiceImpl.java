package com.gdmu.service.impl;

import com.gdmu.entity.CategoryWeight;
import com.gdmu.exception.BusinessException;
import com.gdmu.mapper.CategoryWeightMapper;
import com.gdmu.service.CategoryWeightService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class CategoryWeightServiceImpl implements CategoryWeightService {
    
    private final CategoryWeightMapper categoryWeightMapper;
    
    @Autowired
    public CategoryWeightServiceImpl(CategoryWeightMapper categoryWeightMapper) {
        this.categoryWeightMapper = categoryWeightMapper;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insert(CategoryWeight categoryWeight) {
        log.debug("插入类别权重");
        
        if (categoryWeight == null) {
            throw new BusinessException("类别权重不能为空");
        }
        
        if (categoryWeight.getCategoryCode() == null || categoryWeight.getCategoryCode().trim().isEmpty()) {
            throw new BusinessException("类别代码不能为空");
        }
        
        if (categoryWeight.getCategoryName() == null || categoryWeight.getCategoryName().trim().isEmpty()) {
            throw new BusinessException("类别名称不能为空");
        }
        
        Integer weight = categoryWeight.getWeight();
        if (weight == null || weight < 0 || weight > 100) {
            throw new BusinessException("权重值必须在0-100之间");
        }
        
        Integer currentTotalWeight = categoryWeightMapper.getTotalWeight();
        if (currentTotalWeight != null) {
            Integer newTotalWeight = currentTotalWeight + weight;
            if (newTotalWeight > 100) {
                throw new BusinessException("添加后总权重为" + newTotalWeight + "，不能超过100");
            }
        }
        
        int result = categoryWeightMapper.insert(categoryWeight);
        log.info("类别权重插入成功，ID: {}", categoryWeight.getId());
        return result;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchUpdate(List<CategoryWeight> categoryWeights) {
        log.debug("批量更新类别权重，数量: {}", categoryWeights.size());
        
        if (categoryWeights == null || categoryWeights.isEmpty()) {
            throw new BusinessException("类别权重列表不能为空");
        }
        
        for (CategoryWeight cw : categoryWeights) {
            if (cw.getCategoryCode() == null || cw.getCategoryCode().trim().isEmpty()) {
                throw new BusinessException("类别代码不能为空");
            }
            
            if (cw.getCategoryName() == null || cw.getCategoryName().trim().isEmpty()) {
                throw new BusinessException("类别名称不能为空");
            }
            
            Integer weight = cw.getWeight();
            if (weight == null || weight < 0 || weight > 100) {
                throw new BusinessException("权重值必须在0-100之间");
            }
        }
        
        Integer totalWeight = categoryWeights.stream().mapToInt(CategoryWeight::getWeight).sum();
        if (totalWeight != 100) {
            throw new BusinessException("总权重必须为100，当前为" + totalWeight);
        }
        
        List<CategoryWeight> allExistingWeights = categoryWeightMapper.findAll();
        Set<String> existingCategoryCodes = new HashSet<>();
        for (CategoryWeight existing : allExistingWeights) {
            existingCategoryCodes.add(existing.getCategoryCode());
        }
        
        Set<String> newCategoryCodes = new HashSet<>();
        for (CategoryWeight cw : categoryWeights) {
            newCategoryCodes.add(cw.getCategoryCode());
        }
        
        for (CategoryWeight existing : allExistingWeights) {
            if (!newCategoryCodes.contains(existing.getCategoryCode())) {
                categoryWeightMapper.deleteById(existing.getId());
                log.debug("删除旧的类别权重记录: {}", existing.getCategoryCode());
            }
        }
        
        for (CategoryWeight cw : categoryWeights) {
            CategoryWeight existing = categoryWeightMapper.findByCategoryCodeIncludingDeleted(cw.getCategoryCode());
            if (existing != null) {
                cw.setId(existing.getId());
                cw.setDeleted(0);
                categoryWeightMapper.update(cw);
            } else {
                categoryWeightMapper.insert(cw);
            }
        }
        
        log.info("批量更新类别权重成功");
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByCategoryCode(String categoryCode) {
        log.debug("根据类别代码删除权重，代码: {}", categoryCode);
        
        if (categoryCode == null || categoryCode.trim().isEmpty()) {
            throw new BusinessException("类别代码不能为空");
        }
        
        int result = categoryWeightMapper.deleteByCategoryCode(categoryCode);
        log.info("根据类别代码删除权重成功，代码: {}, 删除数量: {}", categoryCode, result);
        return result;
    }
    
    @Override
    public List<CategoryWeight> findAll() {
        log.debug("查询所有类别权重");
        return categoryWeightMapper.findAll();
    }
    
    @Override
    public CategoryWeight findById(Long id) {
        log.debug("根据ID查询类别权重，ID: {}", id);
        
        if (id == null || id <= 0) {
            throw new BusinessException("类别权重ID无效");
        }
        
        return categoryWeightMapper.findById(id);
    }
    
    @Override
    public CategoryWeight findByCategoryCode(String categoryCode) {
        log.debug("根据类别代码查询权重，代码: {}", categoryCode);
        return categoryWeightMapper.findByCategoryCode(categoryCode);
    }
    
    @Override
    public List<CategoryWeight> findActive() {
        log.debug("查询所有启用的类别权重");
        return categoryWeightMapper.findActive();
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int update(CategoryWeight categoryWeight) {
        log.debug("更新类别权重，ID: {}", categoryWeight.getId());
        
        if (categoryWeight == null || categoryWeight.getId() == null) {
            throw new BusinessException("类别权重或ID不能为空");
        }
        
        CategoryWeight existing = categoryWeightMapper.findById(categoryWeight.getId());
        if (existing == null) {
            throw new BusinessException("类别权重不存在");
        }
        
        Integer newWeight = categoryWeight.getWeight();
        if (newWeight == null || newWeight < 0 || newWeight > 100) {
            throw new BusinessException("权重值必须在0-100之间");
        }
        
        int result = categoryWeightMapper.update(categoryWeight);
        log.info("类别权重更新成功，ID: {}", categoryWeight.getId());
        return result;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteById(Long id) {
        log.debug("删除类别权重，ID: {}", id);
        
        if (id == null || id <= 0) {
            throw new BusinessException("类别权重ID无效");
        }
        
        int result = categoryWeightMapper.deleteById(id);
        log.info("类别权重删除成功，ID: {}", id);
        return result;
    }
    
    @Override
    public Integer getTotalWeight() {
        log.debug("获取总权重");
        return categoryWeightMapper.getTotalWeight();
    }
}
