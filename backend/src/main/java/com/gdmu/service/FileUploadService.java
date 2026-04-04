package com.gdmu.service;

import com.gdmu.entity.FileUpload;
import com.gdmu.entity.PageResult;

import java.util.List;

/**
 * 文件上传记录服务接口
 */
public interface FileUploadService {
    /**
     * 插入文件上传记录
     * @param fileUpload 文件上传记录
     * @return 插入的记录数
     */
    int insert(FileUpload fileUpload);
    
    /**
     * 根据ID查询文件上传记录
     * @param id 记录ID
     * @return 文件上传记录
     */
    FileUpload findById(Long id);
    
    /**
     * 更新文件上传记录
     * @param fileUpload 文件上传记录
     * @return 更新的记录数
     */
    int update(FileUpload fileUpload);
    
    /**
     * 删除文件上传记录
     * @param id 记录ID
     * @return 删除的记录数
     */
    int delete(Long id);
    
    /**
     * 查询所有文件上传记录
     * @return 文件上传记录列表
     */
    List<FileUpload> findAll();
    
    /**
     * 根据上传人ID和类型查询文件上传记录
     * @param uploaderId 上传人ID
     * @param uploaderType 上传人类型
     * @return 文件上传记录列表
     */
    List<FileUpload> findByUploader(Long uploaderId, String uploaderType);
    
    /**
     * 动态条件查询文件上传记录
     * @param uploaderId 上传人ID
     * @param uploaderType 上传人类型
     * @param businessType 业务类型
     * @return 文件上传记录列表
     */
    List<FileUpload> list(Long uploaderId, String uploaderType, String businessType);
    
    /**
     * 根据ID列表查询文件上传记录
     * @param ids 记录ID列表
     * @return 文件上传记录列表
     */
    List<FileUpload> findByIds(List<Long> ids);
    
    /**
     * 批量删除文件上传记录
     * @param ids 记录ID列表
     * @return 删除的记录数
     */
    int batchDelete(List<Long> ids);
    
    /**
     * 查询文件上传记录总数
     * @return 文件上传记录总数
     */
    Long count();
    
    /**
     * 分页查询文件上传记录
     * @param page 页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    PageResult<FileUpload> findPage(Integer page, Integer pageSize);
    
    /**
     * 分页查询文件上传记录
     * @param page 页码
     * @param pageSize 每页大小
     * @param uploaderId 上传人ID
     * @param uploaderType 上传人类型
     * @param businessType 业务类型
     * @return 分页结果
     */
    PageResult<FileUpload> findPage(Integer page, Integer pageSize, Long uploaderId, String uploaderType, String businessType);
}