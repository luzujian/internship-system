package com.gdmu.mapper;

import com.gdmu.entity.InternshipAIAnalysis;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface InternshipAIAnalysisMapper {
    int insert(InternshipAIAnalysis analysis);
    
    InternshipAIAnalysis findById(Long id);
    
    InternshipAIAnalysis findByStudentId(Long studentId);
    
    int update(InternshipAIAnalysis analysis);
    
    int deleteById(Long id);
    
    int deleteByStudentId(Long studentId);
    
    List<InternshipAIAnalysis> findAll();
}
