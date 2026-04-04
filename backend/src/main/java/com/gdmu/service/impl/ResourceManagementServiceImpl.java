package com.gdmu.service.impl;

import com.gdmu.entity.Resource;
import com.gdmu.mapper.ResourceMapper;
import com.gdmu.service.ResourceManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ResourceManagementServiceImpl implements ResourceManagementService {
    
    private static final Logger logger = LoggerFactory.getLogger(ResourceManagementServiceImpl.class);
    
    @Autowired
    private ResourceMapper resourceMapper;
    
    @Value("${file.upload.path:/uploads}")
    private String uploadPath;
    
    @Value("${file.upload.url-prefix:/uploads}")
    private String urlPrefix;
    
    @Override
    @Transactional
    public Long uploadResource(MultipartFile file, Resource resource, Long uploaderId, String uploaderRole, String uploaderName) {
        try {
            if (file != null && !file.isEmpty()) {
                String datePath = new SimpleDateFormat("yyyy/MM/dd").format(new Date());
                String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
                String relativePath = datePath + "/" + fileName;
                String fullPath = uploadPath + "/" + relativePath;
                
                File destFile = new File(fullPath);
                destFile.getParentFile().mkdirs();
                file.transferTo(destFile);
                
                resource.setFileUrl(urlPrefix + "/" + relativePath);
                resource.setFileSize(file.getSize());
            }
            
            resource.setUploaderId(uploaderId);
            resource.setUploaderRole(uploaderRole);
            resource.setUploaderName(uploaderName);
            resource.setDownloadCount(0);
            resource.setStatus("PUBLISHED");
            resource.setCreateTime(LocalDateTime.now());
            resource.setUpdateTime(LocalDateTime.now());
            
            resourceMapper.insert(resource);
            
            logger.info("资源上传成功: id={}, name={}", resource.getId(), resource.getName());
            return resource.getId();
        } catch (IOException e) {
            logger.error("资源上传失败: {}", e.getMessage(), e);
            throw new RuntimeException("资源上传失败", e);
        }
    }
    
    @Override
    public Resource getResourceById(Long id) {
        return resourceMapper.selectById(id);
    }
    
    @Override
    public List<Resource> getResourceList(String type, String keyword, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        
        if (type != null && !type.isEmpty() && keyword != null && !keyword.isEmpty()) {
            return resourceMapper.selectByTypeAndKeyword(type, keyword);
        } else if (type != null && !type.isEmpty()) {
            return resourceMapper.selectPageByType(type, offset, pageSize);
        } else if (keyword != null && !keyword.isEmpty()) {
            return resourceMapper.searchByKeyword(keyword);
        } else {
            return resourceMapper.selectPage(offset, pageSize);
        }
    }
    
    @Override
    public int getResourceCount(String type, String keyword) {
        if (type != null && !type.isEmpty() && keyword != null && !keyword.isEmpty()) {
            return resourceMapper.countByTypeAndKeyword(type, keyword);
        } else if (type != null && !type.isEmpty()) {
            return resourceMapper.countByTypeAndStatus(type, "PUBLISHED");
        } else {
            return resourceMapper.countByStatus("PUBLISHED");
        }
    }
    
    @Override
    @Transactional
    public void updateResource(Resource resource) {
        resource.setUpdateTime(LocalDateTime.now());
        resourceMapper.updateById(resource);
        logger.info("资源更新成功: id={}", resource.getId());
    }
    
    @Override
    @Transactional
    public void deleteResource(Long id) {
        Resource resource = resourceMapper.selectById(id);
        if (resource != null && resource.getFileUrl() != null) {
            try {
                String relativePath = resource.getFileUrl().replace(urlPrefix + "/", "");
                String fullPath = uploadPath + "/" + relativePath;
                File file = new File(fullPath);
                if (file.exists()) {
                    file.delete();
                }
            } catch (Exception e) {
                logger.warn("删除文件失败: {}", e.getMessage());
            }
        }
        resourceMapper.deleteById(id);
        logger.info("资源删除成功: id={}", id);
    }
    
    @Override
    @Transactional
    public void incrementDownloadCount(Long id) {
        resourceMapper.incrementDownloadCount(id);
    }
    
    @Override
    public Map<String, Object> getResourcePage(String type, String keyword, int page, int pageSize) {
        Map<String, Object> result = new HashMap<>();
        
        int total = getResourceCount(type, keyword);
        List<Resource> list = getResourceList(type, keyword, page, pageSize);
        
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("totalPages", (int) Math.ceil((double) total / pageSize));
        
        return result;
    }
}
