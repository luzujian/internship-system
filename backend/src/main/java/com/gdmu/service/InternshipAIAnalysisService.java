package com.gdmu.service;

import com.gdmu.entity.InternshipAIAnalysis;

import java.util.List;

public interface InternshipAIAnalysisService {
    int insert(InternshipAIAnalysis analysis);
    
    InternshipAIAnalysis findById(Long id);
    
    InternshipAIAnalysis findByStudentId(Long studentId);
    
    int update(InternshipAIAnalysis analysis);
    
    int deleteById(Long id);
    
    int deleteByStudentId(Long studentId);
    
    List<InternshipAIAnalysis> findAll();
}
