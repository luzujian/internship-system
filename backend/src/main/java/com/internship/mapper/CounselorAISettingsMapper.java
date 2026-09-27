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

    /**
     * 管理端修改“实际请求模型”（model_code）时，把引用旧代码的辅导员配置同步到新代码，
     * 避免改名后辅导员链路找不到模型。
     */
    @Update("UPDATE counselor_ai_settings SET ai_model_code = #{newCode}, update_time = NOW() WHERE ai_model_code = #{oldCode}")
    int updateModelCode(@Param("oldCode") String oldCode, @Param("newCode") String newCode);
}
