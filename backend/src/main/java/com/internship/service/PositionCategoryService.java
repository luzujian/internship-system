package com.internship.service;

import com.internship.entity.PositionCategory;
import com.internship.entity.Position;

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
