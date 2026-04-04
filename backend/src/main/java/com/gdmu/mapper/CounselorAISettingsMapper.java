package com.gdmu.mapper;

import com.gdmu.entity.CounselorAISettings;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CounselorAISettingsMapper {
    
    int insert(CounselorAISettings settings);
    
    CounselorAISettings findById(@Param("id") Long id);
    
    CounselorAISettings findByCounselorId(@Param("counselorId") Long counselorId);
    
    int update(CounselorAISettings settings);
    
    int deleteById(@Param("id") Long id);
    
    int deleteByCounselorId(@Param("counselorId") Long counselorId);
}
