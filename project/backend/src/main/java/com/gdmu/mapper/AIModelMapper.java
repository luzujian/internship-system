package com.gdmu.mapper;

import com.gdmu.entity.AIModel;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AIModelMapper {
    
    @Insert("INSERT INTO ai_model (model_name, model_code, provider, api_endpoint, api_key, " +
            "max_tokens, temperature, description, status, is_default, creator) " +
            "VALUES (#{modelName}, #{modelCode}, #{provider}, #{apiEndpoint}, #{apiKey}, " +
            "#{maxTokens}, #{temperature}, #{description}, #{status}, #{isDefault}, #{creator})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AIModel aiModel);
    
    @Select("SELECT * FROM ai_model WHERE deleted = 0 ORDER BY create_time DESC")
    List<AIModel> findAll();
    
    @Select("SELECT * FROM ai_model WHERE id = #{id} AND deleted = 0")
    AIModel findById(Long id);
    
    @Select("SELECT * FROM ai_model WHERE model_code = #{modelCode} AND deleted = 0")
    AIModel findByModelCode(String modelCode);
    
    @Select("SELECT * FROM ai_model WHERE status = 1 AND deleted = 0 ORDER BY create_time DESC")
    List<AIModel> findEnabledModels();
    
    @Select("SELECT * FROM ai_model WHERE is_default = 1 AND status = 1 AND deleted = 0 LIMIT 1")
    AIModel findDefaultModel();
    
    @Select("SELECT * FROM ai_model WHERE provider = #{provider} AND deleted = 0 ORDER BY create_time DESC")
    List<AIModel> findByProvider(String provider);
    
    @Update("UPDATE ai_model SET model_name = #{modelName}, provider = #{provider}, " +
            "api_endpoint = #{apiEndpoint}, api_key = #{apiKey}, max_tokens = #{maxTokens}, " +
            "temperature = #{temperature}, description = #{description}, status = #{status}, " +
            "is_default = #{isDefault}, updater = #{updater}, update_time = NOW() " +
            "WHERE id = #{id}")
    int update(AIModel aiModel);
    
    @Update("UPDATE ai_model SET is_default = 0, updater = #{updater}, update_time = NOW() WHERE deleted = 0")
    int clearDefaultModel(String updater);
    
    @Update("UPDATE ai_model SET is_default = 1, updater = #{updater}, update_time = NOW() WHERE id = #{id}")
    int setAsDefault(Long id, String updater);
    
    @Update("UPDATE ai_model SET deleted = 1, updater = #{updater}, update_time = NOW() WHERE id = #{id}")
    int deleteById(Long id, String updater);
    
    @Select("SELECT * FROM ai_model WHERE deleted = 0 AND (model_name LIKE CONCAT('%', #{keyword}, '%') " +
            "OR model_code LIKE CONCAT('%', #{keyword}, '%') OR provider LIKE CONCAT('%', #{keyword}, '%')) " +
            "ORDER BY create_time DESC")
    List<AIModel> searchByKeyword(String keyword);
    
    @Select("<script>" +
            "SELECT * FROM ai_model WHERE deleted = 0 " +
            "<if test='status != null'>AND status = #{status}</if> " +
            "ORDER BY create_time DESC" +
            "</script>")
    List<AIModel> findByStatus(Integer status);
}
