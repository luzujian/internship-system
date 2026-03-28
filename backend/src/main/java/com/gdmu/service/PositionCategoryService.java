package com.gdmu.service;

import com.gdmu.entity.PositionCategory;
import com.gdmu.entity.Position;

import java.util.List;
import java.util.Map;

public interface PositionCategoryService {
    PositionCategory findById(Long id);

    List<PositionCategory> findAll();

    List<PositionCategory> list(String name);

    int insert(PositionCategory positionCategory);

    int update(PositionCategory positionCategory);

    int delete(Long id);

    Long count();

    List<Position> getPositionsByCategoryId(Long categoryId);

    List<Position> getAllPositions();

    Map<String, Object> getCategoryStatistics();
}
