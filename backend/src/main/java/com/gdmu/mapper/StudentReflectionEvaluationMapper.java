package com.gdmu.mapper;

import com.gdmu.entity.StudentReflectionEvaluation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface StudentReflectionEvaluationMapper {
    
    int insert(StudentReflectionEvaluation evaluation);
    
    StudentReflectionEvaluation findById(@Param("id") Long id);
    
    StudentReflectionEvaluation findByReflectionId(@Param("reflectionId") Long reflectionId);
    
    StudentReflectionEvaluation findByStudentId(@Param("studentId") Long studentId);
    
    List<StudentReflectionEvaluation> findByCounselorId(@Param("counselorId") Long counselorId);
    
    int update(StudentReflectionEvaluation evaluation);
    
    int deleteById(@Param("id") Long id);
    
    int deleteByReflectionId(@Param("reflectionId") Long reflectionId);
    
    int countByCounselorId(@Param("counselorId") Long counselorId);
}
