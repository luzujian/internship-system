package com.gdmu.service.impl;

import com.gdmu.entity.FileUpload;
import com.gdmu.entity.PageResult;
import com.gdmu.exception.BusinessException;
import com.gdmu.mapper.FileUploadMapper;
import com.gdmu.service.FileUploadService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 文件上传记录服务实现类
 */
@Slf4j
@Service
public class FileUploadServiceImpl implements FileUploadService {
    
    private final FileUploadMapper fileUploadMapper;
    
    @Autowired
    public FileUploadServiceImpl(FileUploadMapper fileUploadMapper) {
        this.fileUploadMapper = fileUploadMapper;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insert(FileUpload fileUpload) {
        log.debug("插入文件上传记录: {}", fileUpload.getFileName());
        
        // 参数校验
        validateFileUploadInfo(fileUpload);
        
        // 设置上传时间
        Date now = new Date();
        fileUpload.setUploadTime(now);
        
        int result = fileUploadMapper.insert(fileUpload);
        log.info("文件上传记录插入成功，文件名: {}", fileUpload.getFileName());
        return result;
    }
    
    @Override
    public FileUpload findById(Long id) {
        log.debug("根据ID查询文件上传记录，ID: {}", id);
        
        // 参数校验
        if (id == null || id <= 0) {
            throw new BusinessException("记录ID无效");
        }
        
        return fileUploadMapper.findById(id);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int update(FileUpload fileUpload) {
        log.debug("更新文件上传记录，ID: {}", fileUpload.getId());
        
        // 参数校验
        if (fileUpload.getId() == null || fileUpload.getId() <= 0) {
            throw new BusinessException("记录ID无效");
        }
        
        // 检查记录是否存在
        FileUpload existingFileUpload = fileUploadMapper.findById(fileUpload.getId());
        if (existingFileUpload == null) {
            throw new BusinessException("记录不存在");
        }
        
        // 参数校验
        validateFileUploadInfo(fileUpload);
        
        int result = fileUploadMapper.update(fileUpload);
        log.info("文件上传记录更新成功，文件名: {}", fileUpload.getFileName());
        return result;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        log.debug("删除文件上传记录，ID: {}", id);
        
        // 参数校验
        if (id == null || id <= 0) {
            throw new BusinessException("记录ID无效");
        }
        
        // 检查记录是否存在
        FileUpload fileUpload = fileUploadMapper.findById(id);
        if (fileUpload == null) {
            throw new BusinessException("记录不存在");
        }
        
        int result = fileUploadMapper.deleteById(id);
        log.info("文件上传记录删除成功，文件名: {}", fileUpload.getFileName());
        return result;
    }
    
    @Override
    public List<FileUpload> findAll() {
        log.debug("查询所有文件上传记录");
        return fileUploadMapper.findAll();
    }
    
    @Override
    public List<FileUpload> findByUploader(Long uploaderId, String uploaderType) {
        log.debug("根据上传人查询文件上传记录，上传人ID: {}, 上传人类型: {}", uploaderId, uploaderType);
        
        // 参数校验
        if (uploaderId == null || uploaderId <= 0) {
            throw new BusinessException("上传人ID无效");
        }
        
        if (StringUtils.isBlank(uploaderType)) {
            throw new BusinessException("上传人类型不能为空");
        }
        
        return fileUploadMapper.findByUploader(uploaderId, uploaderType);
    }
    
    @Override
    public List<FileUpload> list(Long uploaderId, String uploaderType, String businessType) {
        log.debug("动态条件查询文件上传记录，上传人ID: {}, 上传人类型: {}, 业务类型: {}", 
                uploaderId, uploaderType, businessType);
        
        return fileUploadMapper.list(uploaderId, uploaderType, businessType);
    }
    
    @Override
    public List<FileUpload> findByIds(List<Long> ids) {
        log.debug("根据ID列表查询文件上传记录，ID列表: {}", ids);
        
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        
        return fileUploadMapper.selectByIds(ids);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchDelete(List<Long> ids) {
        log.debug("批量删除文件上传记录，ID列表: {}", ids);
        
        // 参数校验
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("记录ID列表不能为空");
        }
        
        int result = fileUploadMapper.batchDeleteByIds(ids);
        log.info("批量删除文件上传记录成功，删除数量: {}", result);
        return result;
    }
    
    @Override
    public Long count() {
        log.debug("查询文件上传记录总数");
        return fileUploadMapper.count();
    }
    
    @Override
    public PageResult<FileUpload> findPage(Integer page, Integer pageSize) {
        log.debug("分页查询文件上传记录，页码: {}, 每页大小: {}", page, pageSize);
        return findPage(page, pageSize, null, null, null);
    }
    
    @Override
    public PageResult<FileUpload> findPage(Integer page, Integer pageSize, Long uploaderId, String uploaderType, String businessType) {
        log.debug("分页查询文件上传记录，页码: {}, 每页大小: {}, 上传人ID: {}, 上传人类型: {}, 业务类型: {}", 
                page, pageSize, uploaderId, uploaderType, businessType);
        
        // 使用PageHelper进行分页查询
        PageHelper.startPage(page, pageSize);
        List<FileUpload> fileUploads = fileUploadMapper.list(uploaderId, uploaderType, businessType);
        
        // 构建分页结果
        PageInfo<FileUpload> pageInfo = new PageInfo<>(fileUploads);
        return PageResult.build(pageInfo.getTotal(), pageInfo.getList(),
                pageInfo.getPages(), pageInfo.getPageNum(), pageInfo.getPageSize());
    }
    
    /**
     * 验证文件上传记录信息
     */
    private void validateFileUploadInfo(FileUpload fileUpload) {
        if (fileUpload == null) {
            throw new BusinessException("文件上传记录信息不能为空");
        }
        
        if (StringUtils.isBlank(fileUpload.getFileName())) {
            throw new BusinessException("文件名不能为空");
        }
        
        if (StringUtils.isBlank(fileUpload.getFilePath())) {
            throw new BusinessException("文件路径不能为空");
        }
        
        if (fileUpload.getFileSize() == null || fileUpload.getFileSize() < 0) {
            throw new BusinessException("文件大小无效");
        }
        
        if (StringUtils.isBlank(fileUpload.getFileType())) {
            throw new BusinessException("文件类型不能为空");
        }
        
        if (fileUpload.getUploaderId() == null || fileUpload.getUploaderId() <= 0) {
            throw new BusinessException("上传人ID无效");
        }
        
        if (StringUtils.isBlank(fileUpload.getUploaderType())) {
            throw new BusinessException("上传人类型不能为空");
        }
        
        if (StringUtils.isBlank(fileUpload.getBusinessType())) {
            throw new BusinessException("业务类型不能为空");
        }
    }
}