package com.gdmu.service;

import com.gdmu.entity.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface ResourceManagementService {
    
    Long uploadResource(MultipartFile file, Resource resource, Long uploaderId, String uploaderRole, String uploaderName);
    
    Resource getResourceById(Long id);
    
    List<Resource> getResourceList(String type, String keyword, int page, int pageSize);
    
    int getResourceCount(String type, String keyword);
    
    void updateResource(Resource resource);
    
    void deleteResource(Long id);
    
    void incrementDownloadCount(Long id);
    
    Map<String, Object> getResourcePage(String type, String keyword, int page, int pageSize);
}
