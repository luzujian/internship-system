package com.gdmu.service;

import com.gdmu.entity.StudentReflectionEvaluation;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface StudentReflectionEvaluationService {
    
    int insert(StudentReflectionEvaluation evaluation);
    
    StudentReflectionEvaluation findById(Long id);
    
    StudentReflectionEvaluation findByReflectionId(Long reflectionId);
    
    StudentReflectionEvaluation findByStudentId(Long studentId);
    
    List<StudentReflectionEvaluation> findByCounselorId(Long counselorId);
    
    int update(StudentReflectionEvaluation evaluation);
    
    int deleteById(Long id);
    
    int deleteByReflectionId(Long reflectionId);
    
    int saveEvaluation(StudentReflectionEvaluation evaluation);
    
    Map<String, Object> calculateTotalScoreAndGrade(Map<String, Object> scoreDetails, Long counselorId);
}
