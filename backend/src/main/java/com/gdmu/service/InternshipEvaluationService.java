package com.gdmu.service;

import com.gdmu.entity.InternshipEvaluation;
import com.gdmu.entity.PageResult;

import java.util.List;

public interface InternshipEvaluationService {
    int insert(InternshipEvaluation evaluation);
    
    InternshipEvaluation findById(Long id);
    
    InternshipEvaluation findByStudentId(Long studentId);
    
    int update(InternshipEvaluation evaluation);
    
    int deleteById(Long id);
    
    int deleteByStudentId(Long studentId);
    
    List<InternshipEvaluation> findAll();
    
    List<InternshipEvaluation> findByEvaluatorId(Long evaluatorId);
    
    int countByEvaluatorId(Long evaluatorId);
    
    List<InternshipEvaluation> findByGrade(String grade);
    
    PageResult<InternshipEvaluation> findPage(Integer page, Integer pageSize, Long evaluatorId);

    int publishGrades(List<Long> studentIds);
}
