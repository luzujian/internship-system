package com.gdmu.mapper;

import com.gdmu.entity.CounselorScoringRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface CounselorScoringRuleMapper {
    
    int insert(CounselorScoringRule rule);
    
    int batchInsert(@Param("rules") List<CounselorScoringRule> rules);
    
    CounselorScoringRule findById(@Param("id") Long id);
    
    List<CounselorScoringRule> findByCounselorId(@Param("counselorId") Long counselorId);
    
    List<CounselorScoringRule> findByCounselorIdAndCategory(@Param("counselorId") Long counselorId, @Param("category") String category);
    
    List<CounselorScoringRule> findActiveByCounselorId(@Param("counselorId") Long counselorId);
    
    List<String> findCategoriesByCounselorId(@Param("counselorId") Long counselorId);
    
    int update(CounselorScoringRule rule);
    
    int deleteById(@Param("id") Long id);
    
    int deleteByCounselorId(@Param("counselorId") Long counselorId);
    
    int deleteByCounselorIdAndCategory(@Param("counselorId") Long counselorId, @Param("category") String category);
    
    int countByCounselorId(@Param("counselorId") Long counselorId);
}
