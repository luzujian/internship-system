package com.gdmu.mapper;

import com.gdmu.entity.TemplateFile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 模板文件Mapper接口
 */
@Mapper
public interface TemplateFileMapper {
    
    int insert(TemplateFile templateFile);
    
    TemplateFile findById(Long id);
    
    int update(TemplateFile templateFile);
    
    int deleteById(Long id);
    
    List<TemplateFile> findAll();
    
    List<TemplateFile> list(@Param("name") String name, @Param("category") String category, @Param("status") Integer status);
    
    int incrementDownloadCount(Long id);
    
    Long count();
    
    int batchDeleteByIds(@Param("ids") List<Long> ids);
}
