package com.gdmu.mapper;

import com.gdmu.entity.KeywordLibrary;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface KeywordLibraryMapper {
    
    @Insert("INSERT INTO keyword_library (keyword, category, description, weight, status, usage_type, related_tags, creator) " +
            "VALUES (#{keyword}, #{category}, #{description}, #{weight}, #{status}, #{usageType}, #{relatedTags}, #{creator})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(KeywordLibrary keywordLibrary);
    
    @Select("SELECT * FROM keyword_library WHERE deleted = 0 ORDER BY create_time DESC")
    List<KeywordLibrary> findAll();
    
    @Select("SELECT * FROM keyword_library WHERE id = #{id} AND deleted = 0")
    KeywordLibrary findById(Long id);
    
    @Update("UPDATE keyword_library SET keyword = #{keyword}, category = #{category}, description = #{description}, " +
            "weight = #{weight}, status = #{status}, usage_type = #{usageType}, related_tags = #{relatedTags}, updater = #{updater}, update_time = NOW() " +
            "WHERE id = #{id}")
    int update(KeywordLibrary keywordLibrary);
    
    @Update("UPDATE keyword_library SET deleted = 1, updater = #{updater}, update_time = NOW() WHERE id = #{id}")
    int deleteById(Long id);
    
    @Select("SELECT * FROM keyword_library WHERE deleted = 0 AND category = #{category} ORDER BY create_time DESC")
    List<KeywordLibrary> findByCategory(String category);
    
    @Select("SELECT * FROM keyword_library WHERE deleted = 0 AND usage_type = #{usageType} ORDER BY create_time DESC")
    List<KeywordLibrary> findByUsageType(String usageType);
    
    @Select("SELECT * FROM keyword_library WHERE deleted = 0 AND status = #{status} ORDER BY create_time DESC")
    List<KeywordLibrary> findByStatus(Integer status);
    
    @Select("SELECT * FROM keyword_library WHERE deleted = 0 AND keyword LIKE CONCAT('%', #{keyword}, '%') ORDER BY create_time DESC")
    List<KeywordLibrary> searchByKeyword(String keyword);
}
