package com.gdmu.service.impl;

import com.gdmu.entity.PageResult;
import com.gdmu.entity.ResourceDocument;
import com.gdmu.exception.BusinessException;
import com.gdmu.mapper.ResourceDocumentMapper;
import com.gdmu.service.ResourceDocumentService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class ResourceDocumentServiceImpl implements ResourceDocumentService {
    
    private final ResourceDocumentMapper resourceDocumentMapper;
    
    @Autowired
    public ResourceDocumentServiceImpl(ResourceDocumentMapper resourceDocumentMapper) {
        this.resourceDocumentMapper = resourceDocumentMapper;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insert(ResourceDocument resourceDocument) {
        log.debug("插入资源文档: {}", resourceDocument.getTitle());
        
        validateResourceDocumentInfo(resourceDocument);
        
        Date now = new Date();
        resourceDocument.setPublishTime(now);
        
        int result = resourceDocumentMapper.insert(resourceDocument);
        log.info("资源文档插入成功，标题: {}", resourceDocument.getTitle());
        return result;
    }
    
    @Override
    public ResourceDocument findById(Long id) {
        log.debug("根据ID查询资源文档，ID: {}", id);
        
        if (id == null || id <= 0) {
            throw new BusinessException("资源文档ID无效");
        }
        
        return resourceDocumentMapper.findById(id);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int update(ResourceDocument resourceDocument) {
        log.debug("更新资源文档，ID: {}", resourceDocument.getId());
        
        if (resourceDocument.getId() == null || resourceDocument.getId() <= 0) {
            throw new BusinessException("资源文档ID无效");
        }
        
        ResourceDocument existingDocument = resourceDocumentMapper.findById(resourceDocument.getId());
        if (existingDocument == null) {
            throw new BusinessException("资源文档不存在");
        }
        
        validateResourceDocumentInfo(resourceDocument);
        
        int result = resourceDocumentMapper.update(resourceDocument);
        log.info("资源文档更新成功，标题: {}", resourceDocument.getTitle());
        return result;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        log.debug("删除资源文档，ID: {}", id);
        
        if (id == null || id <= 0) {
            throw new BusinessException("资源文档ID无效");
        }
        
        ResourceDocument resourceDocument = resourceDocumentMapper.findById(id);
        if (resourceDocument == null) {
            throw new BusinessException("资源文档不存在");
        }
        
        int result = resourceDocumentMapper.deleteById(id);
        log.info("资源文档删除成功，标题: {}", resourceDocument.getTitle());
        return result;
    }
    
    @Override
    public List<ResourceDocument> findAll() {
        log.debug("查询所有资源文档");
        return resourceDocumentMapper.findAll();
    }
    
    @Override
    public List<ResourceDocument> list(String title, String status, String publisherRole) {
        log.debug("动态条件查询资源文档，标题: {}, 状态: {}, 发布人身份: {}", title, status, publisherRole);
        return resourceDocumentMapper.list(title, status, publisherRole);
    }
    
    @Override
    public List<ResourceDocument> findByIds(List<Long> ids) {
        log.debug("根据ID列表查询资源文档，ID列表: {}", ids);
        
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        
        return resourceDocumentMapper.selectByIds(ids);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchDelete(List<Long> ids) {
        log.debug("批量删除资源文档，ID列表: {}", ids);
        
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("资源文档ID列表不能为空");
        }
        
        int result = resourceDocumentMapper.batchDeleteByIds(ids);
        log.info("批量删除资源文档成功，删除数量: {}", result);
        return result;
    }
    
    @Override
    public Long count() {
        log.debug("查询资源文档总数");
        return resourceDocumentMapper.count();
    }
    
    @Override
    public PageResult<ResourceDocument> findPage(Integer page, Integer pageSize) {
        log.debug("分页查询资源文档，页码: {}, 每页大小: {}", page, pageSize);
        return findPage(page, pageSize, null, null, null);
    }
    
    @Override
    public PageResult<ResourceDocument> findPage(Integer page, Integer pageSize, String title, String status, String publisherRole) {
        log.debug("分页查询资源文档，页码: {}, 每页大小: {}, 标题: {}, 状态: {}, 发布人身份: {}", 
                page, pageSize, title, status, publisherRole);
        
        List<ResourceDocument> resourceDocuments = resourceDocumentMapper.findPage(
                (page - 1) * pageSize, pageSize, title, status, publisherRole);
        
        Long total = resourceDocumentMapper.count();
        int totalPages = (int) Math.ceil((double) total / pageSize);
        
        return PageResult.build(total, resourceDocuments, totalPages, page, pageSize);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int incrementDownloadCount(Long id) {
        log.debug("增加资源文档下载次数，ID: {}", id);
        return resourceDocumentMapper.incrementDownloadCount(id);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int incrementViewCount(Long id) {
        log.debug("增加资源文档浏览次数，ID: {}", id);
        return resourceDocumentMapper.incrementViewCount(id);
    }
    
    private void validateResourceDocumentInfo(ResourceDocument resourceDocument) {
        if (resourceDocument == null) {
            throw new BusinessException("资源文档信息不能为空");
        }
        
        if (StringUtils.isBlank(resourceDocument.getTitle())) {
            throw new BusinessException("资源文档标题不能为空");
        }
        
        if (StringUtils.isBlank(resourceDocument.getPublisher())) {
            throw new BusinessException("发布人不能为空");
        }
        
        if (StringUtils.isBlank(resourceDocument.getPublisherRole())) {
            throw new BusinessException("发布人身份不能为空");
        }
        
        if (StringUtils.isBlank(resourceDocument.getStatus())) {
            throw new BusinessException("资源文档状态不能为空");
        }
    }
}
