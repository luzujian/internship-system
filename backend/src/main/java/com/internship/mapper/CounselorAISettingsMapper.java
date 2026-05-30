package com.internship.mapper;

import com.internship.entity.CounselorAISettings;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CounselorAISettingsMapper {
    
    int insert(CounselorAISettings settings);
    
    CounselorAISettings findById(@Param("id") Long id);
    
    CounselorAISettings findByCounselorId(@Param("counselorId") Long counselorId);
    
    int update(CounselorAISettings settings);
    
    int deleteById(@Param("id") Long id);
    
    int deleteByCounselorId(@Param("counselorId") Long counselorId);

    @Update("UPDATE counselor_ai_settings SET ai_model_code = #{modelCode}, update_time = NOW()")
    int updateAllModelCode(@Param("modelCode") String modelCode);
}
