package com.internship.service;

import com.internship.entity.KeywordLibrary;
import java.util.List;

public interface KeywordLibraryService {
    
    int insert(KeywordLibrary keywordLibrary);
    
    List<KeywordLibrary> findAll();
    
    KeywordLibrary findById(Long id);
    
    int update(KeywordLibrary keywordLibrary);
    
    int deleteById(Long id);
    
    List<KeywordLibrary> findByCategory(String category);
    
    List<KeywordLibrary> findByUsageType(String usageType);
    
    List<KeywordLibrary> findByStatus(Integer status);
    
    List<KeywordLibrary> searchByKeyword(String keyword);
}
