package com.gdmu.service.impl;

import com.gdmu.mapper.PositionCategoryMapper;
import com.gdmu.mapper.PositionMapper;
import com.gdmu.entity.PositionCategory;
import com.gdmu.entity.Position;
import com.gdmu.service.PositionCategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PositionCategoryServiceImpl implements PositionCategoryService {

    @Autowired
    private PositionCategoryMapper positionCategoryMapper;

    @Autowired
    private PositionMapper positionMapper;

    @Override
    public PositionCategory findById(Long id) {
        if (id == null || id <= 0) {
            throw new RuntimeException("类别ID无效");
        }
        return positionCategoryMapper.findById(id);
    }

    @Override
    public List<PositionCategory> findAll() {
        return positionCategoryMapper.findAll();
    }

    @Override
    public List<PositionCategory> list(String name) {
        Map<String, Object> params = new HashMap<>();
        if (name != null && !name.trim().isEmpty()) {
            params.put("name", name);
        }
        return positionCategoryMapper.list(params);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insert(PositionCategory positionCategory) {
        if (positionCategory == null) {
            throw new RuntimeException("岗位类别信息不能为空");
        }
        if (positionCategory.getName() == null || positionCategory.getName().trim().isEmpty()) {
            throw new RuntimeException("类别名称不能为空");
        }
        
        positionCategory.setCreateTime(new Date());
        positionCategory.setUpdateTime(new Date());
        positionCategory.setPositionCount(0);
        
        return positionCategoryMapper.insert(positionCategory);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int update(PositionCategory positionCategory) {
        if (positionCategory == null || positionCategory.getId() == null || positionCategory.getId() <= 0) {
            throw new RuntimeException("岗位类别信息无效");
        }
        
        PositionCategory existing = positionCategoryMapper.findById(positionCategory.getId());
        if (existing == null) {
            throw new RuntimeException("岗位类别不存在");
        }
        
        positionCategory.setUpdateTime(new Date());
        
        return positionCategoryMapper.update(positionCategory);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        if (id == null || id <= 0) {
            throw new RuntimeException("类别ID无效");
        }
        
        PositionCategory existing = positionCategoryMapper.findById(id);
        if (existing == null) {
            throw new RuntimeException("岗位类别不存在");
        }
        
        List<Position> positions = positionMapper.findByCategoryId(id);
        if (positions != null && !positions.isEmpty()) {
            for (Position position : positions) {
                positionMapper.deleteById(position.getId());
            }
        }
        
        return positionCategoryMapper.deleteById(id);
    }

    @Override
    public Long count() {
        return positionCategoryMapper.count();
    }

    @Override
    public List<Position> getPositionsByCategoryId(Long categoryId) {
        if (categoryId == null || categoryId <= 0) {
            throw new RuntimeException("类别ID无效");
        }

        List<Position> positions = positionMapper.findByCategoryId(categoryId);

        for (Position position : positions) {
            Integer count = positionCategoryMapper.getPositionCountByCategoryId(categoryId);
            if (count != null) {
                positionCategoryMapper.updateCategoryPositionCount(categoryId, count);
            }
        }

        return positions;
    }

    @Override
    public List<Position> getAllPositions() {
        return positionMapper.findAll();
    }

    @Override
    public Map<String, Object> getCategoryStatistics() {
        Map<String, Object> statistics = new HashMap<>();
        
        List<PositionCategory> categories = findAll();
        statistics.put("totalCategories", categories.size());
        
        int totalPositions = 0;
        for (PositionCategory category : categories) {
            Integer count = positionCategoryMapper.getPositionCountByCategoryId(category.getId());
            if (count != null) {
                totalPositions += count;
            }
        }
        
        statistics.put("totalPositions", totalPositions);
        statistics.put("categories", categories);
        
        return statistics;
    }
}
