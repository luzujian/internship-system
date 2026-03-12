package com.gdmu.service;

import com.gdmu.entity.PageResult;
import com.gdmu.entity.ResourceDocument;

import java.util.List;

public interface ResourceDocumentService {
    
    int insert(ResourceDocument resourceDocument);
    
    ResourceDocument findById(Long id);
    
    int update(ResourceDocument resourceDocument);
    
    int delete(Long id);
    
    List<ResourceDocument> findAll();
    
    List<ResourceDocument> list(String title, String status, String publisherRole);
    
    List<ResourceDocument> findByIds(List<Long> ids);
    
    int batchDelete(List<Long> ids);
    
    Long count();
    
    PageResult<ResourceDocument> findPage(Integer page, Integer pageSize);
    
    PageResult<ResourceDocument> findPage(Integer page, Integer pageSize, String title, String status, String publisherRole);
    
    int incrementDownloadCount(Long id);
    
    int incrementViewCount(Long id);
}
