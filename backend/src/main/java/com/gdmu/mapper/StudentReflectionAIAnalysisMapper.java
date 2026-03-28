package com.gdmu.mapper;

import com.gdmu.entity.StudentReflectionAIAnalysis;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface StudentReflectionAIAnalysisMapper {
    
    int insert(StudentReflectionAIAnalysis analysis);
    
    StudentReflectionAIAnalysis findById(@Param("id") Long id);
    
    StudentReflectionAIAnalysis findByReflectionId(@Param("reflectionId") Long reflectionId);
    
    StudentReflectionAIAnalysis findByStudentId(@Param("studentId") Long studentId);
    
    List<StudentReflectionAIAnalysis> findByCounselorId(@Param("counselorId") Long counselorId);
    
    int update(StudentReflectionAIAnalysis analysis);
    
    int deleteById(@Param("id") Long id);
    
    int deleteByReflectionId(@Param("reflectionId") Long reflectionId);
}
