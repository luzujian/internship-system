package com.gdmu.mapper;

import com.gdmu.entity.CategoryWeight;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface CategoryWeightMapper {
    
    @Insert("INSERT INTO category_weight (category_code, category_name, weight, status, creator) " +
            "VALUES (#{categoryCode}, #{categoryName}, #{weight}, #{status}, #{creator})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(CategoryWeight categoryWeight);
    
    @Select("SELECT * FROM category_weight WHERE deleted = 0 ORDER BY create_time DESC")
    List<CategoryWeight> findAll();
    
    @Select("SELECT * FROM category_weight WHERE id = #{id} AND deleted = 0")
    CategoryWeight findById(Long id);
    
    @Select("SELECT * FROM category_weight WHERE category_code = #{categoryCode} AND deleted = 0 ORDER BY create_time DESC LIMIT 1")
    CategoryWeight findByCategoryCode(String categoryCode);
    
    @Select("SELECT * FROM category_weight WHERE category_code = #{categoryCode} ORDER BY create_time DESC LIMIT 1")
    CategoryWeight findByCategoryCodeIncludingDeleted(String categoryCode);
    
    @Select("SELECT * FROM category_weight WHERE status = 1 AND deleted = 0 ORDER BY create_time DESC")
    List<CategoryWeight> findActive();
    
    @Update("UPDATE category_weight SET category_name = #{categoryName}, weight = #{weight}, status = #{status}, " +
            "updater = #{updater}, update_time = NOW(), deleted = #{deleted} WHERE id = #{id}")
    int update(CategoryWeight categoryWeight);
    
    @Update("UPDATE category_weight SET deleted = 1, updater = #{updater}, update_time = NOW() WHERE id = #{id}")
    int deleteById(Long id);
    
    @Update("UPDATE category_weight SET deleted = 1, updater = 'system', update_time = NOW() WHERE category_code = #{categoryCode}")
    int deleteByCategoryCode(String categoryCode);
    
    @Select("SELECT SUM(weight) FROM category_weight WHERE status = 1 AND deleted = 0")
    Integer getTotalWeight();
}
