package com.internship.service;

import com.internship.entity.PageResult;
import com.internship.entity.TemplateFile;

import java.util.List;

/**
 * 模板文件服务接口
 */
public interface TemplateFileService {
    
    int insert(TemplateFile templateFile);
    
    TemplateFile findById(Long id);
    
    int update(TemplateFile templateFile);
    
    int delete(Long id);
    
    List<TemplateFile> findAll();
    
    PageResult<TemplateFile> findPage(Integer page, Integer pageSize, String name, String category, Integer status);
    
    int incrementDownloadCount(Long id);
    
    int batchDelete(List<Long> ids);
}
