package com.internship.mapper;

import com.internship.entity.PositionCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface PositionCategoryMapper {

    int insert(PositionCategory positionCategory);

    PositionCategory findById(Long id);

    List<PositionCategory> findAll();

    List<PositionCategory> list(Map<String, Object> params);

    int update(PositionCategory positionCategory);

    int deleteById(Long id);

    Long count();

    Integer getPositionCountByCategoryId(Long categoryId);

    int updateCategoryPositionCount(@Param("id") Long id, @Param("positionCount") Integer positionCount);

    List<PositionCategory> findByIds(List<Long> ids);
}
