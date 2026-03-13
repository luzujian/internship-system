package com.gdmu.service;

import com.gdmu.entity.AIModel;
import java.util.List;

public interface AIModelService {
    
    int insert(AIModel aiModel);
    
    List<AIModel> findAll();
    
    AIModel findById(Long id);
    
    AIModel findByModelCode(String modelCode);
    
    List<AIModel> findEnabledModels();
    
    AIModel findDefaultModel();
    
    List<AIModel> findByProvider(String provider);
    
    int update(AIModel aiModel);
    
    int setAsDefault(Long id, String updater);
    
    int deleteById(Long id);
    
    int deleteAll(String updater);
    
    List<AIModel> searchByKeyword(String keyword);
    
    List<AIModel> findByStatus(Integer status);
}
