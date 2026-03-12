package com.gdmu.mapper;

import com.gdmu.entity.SystemConfig;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SystemConfigMapper {
    
    @Insert("INSERT INTO system_config (config_key, config_value, config_type, config_name, description, status) " +
            "VALUES (#{configKey}, #{configValue}, #{configType}, #{configName}, #{description}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SystemConfig systemConfig);
    
    @Select("SELECT * FROM system_config WHERE deleted = 0 ORDER BY create_time DESC")
    List<SystemConfig> findAll();
    
    @Select("SELECT * FROM system_config WHERE id = #{id} AND deleted = 0")
    SystemConfig findById(Long id);
    
    @Select("SELECT * FROM system_config WHERE config_key = #{configKey} AND deleted = 0")
    SystemConfig findByConfigKey(String configKey);
    
    @Update("UPDATE system_config SET config_key = #{configKey}, config_value = #{configValue}, config_type = #{configType}, " +
            "config_name = #{configName}, description = #{description}, status = #{status}, update_time = NOW() " +
            "WHERE id = #{id}")
    int update(SystemConfig systemConfig);
    
    @Update("UPDATE system_config SET deleted = 1, update_time = NOW() WHERE id = #{id}")
    int deleteById(Long id);
    
    @Select("SELECT * FROM system_config WHERE deleted = 0 AND status = #{status} ORDER BY create_time DESC")
    List<SystemConfig> findByStatus(Integer status);
    
    @Select("SELECT * FROM system_config WHERE deleted = 0 AND config_name LIKE CONCAT('%', #{configName}, '%') ORDER BY create_time DESC")
    List<SystemConfig> searchByConfigName(String configName);
}
