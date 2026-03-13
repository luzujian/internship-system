package com.gdmu.service.impl;

import com.gdmu.entity.PageResult;
import com.gdmu.entity.TemplateFile;
import com.gdmu.exception.BusinessException;
import com.gdmu.mapper.TemplateFileMapper;
import com.gdmu.service.TemplateFileService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * 模板文件服务实现类
 */
@Slf4j
@Service
public class TemplateFileServiceImpl implements TemplateFileService {
    
    @Autowired
    private TemplateFileMapper templateFileMapper;
    
    @Override
    public int insert(TemplateFile templateFile) {
        templateFile.setCreateTime(new Date());
        templateFile.setUpdateTime(new Date());
        templateFile.setDownloadCount(0);
        return templateFileMapper.insert(templateFile);
    }
    
    @Override
    public TemplateFile findById(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException("模板ID无效");
        }
        return templateFileMapper.findById(id);
    }
    
    @Override
    public int update(TemplateFile templateFile) {
        if (templateFile.getId() == null) {
            throw new BusinessException("模板ID不能为空");
        }
        return templateFileMapper.update(templateFile);
    }
    
    @Override
    public int delete(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException("模板ID无效");
        }
        return templateFileMapper.deleteById(id);
    }
    
    @Override
    public List<TemplateFile> findAll() {
        return templateFileMapper.findAll();
    }
    
    @Override
    public PageResult<TemplateFile> findPage(Integer page, Integer pageSize, String name, String category, Integer status) {
        PageHelper.startPage(page, pageSize);
        List<TemplateFile> list = templateFileMapper.list(name, category, status);
        PageInfo<TemplateFile> pageInfo = new PageInfo<>(list);
        return PageResult.build(pageInfo.getTotal(), pageInfo.getList(),
                pageInfo.getPages(), pageInfo.getPageNum(), pageInfo.getPageSize());
    }
    
    @Override
    public int incrementDownloadCount(Long id) {
        return templateFileMapper.incrementDownloadCount(id);
    }
    
    @Override
    public int batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("模板ID列表不能为空");
        }
        return templateFileMapper.batchDeleteByIds(ids);
    }
}
