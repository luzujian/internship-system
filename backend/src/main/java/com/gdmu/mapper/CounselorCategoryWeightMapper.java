package com.gdmu.mapper;

import com.gdmu.entity.CounselorCategoryWeight;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface CounselorCategoryWeightMapper {
    
    int insert(CounselorCategoryWeight weight);
    
    int batchInsert(@Param("weights") List<CounselorCategoryWeight> weights);
    
    CounselorCategoryWeight findById(@Param("id") Long id);
    
    List<CounselorCategoryWeight> findByCounselorId(@Param("counselorId") Long counselorId);
    
    List<CounselorCategoryWeight> findActiveByCounselorId(@Param("counselorId") Long counselorId);
    
    CounselorCategoryWeight findByCounselorIdAndCategoryCode(@Param("counselorId") Long counselorId, @Param("categoryCode") String categoryCode);
    
    int update(CounselorCategoryWeight weight);
    
    int deleteById(@Param("id") Long id);
    
    int deleteByCounselorId(@Param("counselorId") Long counselorId);
    
    int deleteByCounselorIdAndCategoryCode(@Param("counselorId") Long counselorId, @Param("categoryCode") String categoryCode);
    
    Integer getTotalWeightByCounselorId(@Param("counselorId") Long counselorId);
}
