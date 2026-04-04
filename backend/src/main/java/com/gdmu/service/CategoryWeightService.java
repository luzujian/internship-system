package com.gdmu.service;

import com.gdmu.entity.CategoryWeight;
import java.util.List;

public interface CategoryWeightService {
    
    int insert(CategoryWeight categoryWeight);
    
    void batchUpdate(List<CategoryWeight> categoryWeights);
    
    List<CategoryWeight> findAll();
    
    CategoryWeight findById(Long id);
    
    CategoryWeight findByCategoryCode(String categoryCode);
    
    List<CategoryWeight> findActive();
    
    int update(CategoryWeight categoryWeight);
    
    int deleteById(Long id);
    
    int deleteByCategoryCode(String categoryCode);
    
    Integer getTotalWeight();
}
