package com.gdmu.service;

import com.gdmu.entity.StudentReflectionAIAnalysis;
import java.util.List;
import java.util.Map;

public interface StudentReflectionAIAnalysisService {
    
    int insert(StudentReflectionAIAnalysis analysis);
    
    StudentReflectionAIAnalysis findById(Long id);
    
    StudentReflectionAIAnalysis findByReflectionId(Long reflectionId);
    
    StudentReflectionAIAnalysis findByStudentId(Long studentId);
    
    List<StudentReflectionAIAnalysis> findByCounselorId(Long counselorId);
    
    int update(StudentReflectionAIAnalysis analysis);
    
    int deleteById(Long id);
    
    int deleteByReflectionId(Long reflectionId);
    
    Map<String, Object> analyzeReflection(Long reflectionId, Long counselorId);
    
    Map<String, Object> analyzeReflectionContent(String content, Long counselorId, Long studentId, Long reflectionId);
}
