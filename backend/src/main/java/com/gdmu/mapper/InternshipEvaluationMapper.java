package com.gdmu.mapper;

import com.gdmu.entity.InternshipEvaluation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InternshipEvaluationMapper {
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

    int publishGrades(@Param("studentIds") List<Long> studentIds);
}
