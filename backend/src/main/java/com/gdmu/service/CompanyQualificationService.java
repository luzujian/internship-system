package com.gdmu.service;

import com.gdmu.entity.CompanyQualification;
import com.gdmu.entity.PageResult;

import java.util.List;

/**
 * 企业资质审核服务接口
 */
public interface CompanyQualificationService {
    /**
     * 插入企业资质审核
     * @param qualification 企业资质审核
     * @return 插入的记录数
     */
    int insert(CompanyQualification qualification);
    
    /**
     * 根据ID查询企业资质审核
     * @param id 资质ID
     * @return 企业资质审核
     */
    CompanyQualification findById(Long id);
    
    /**
     * 根据企业ID查询资质审核
     * @param companyId 企业ID
     * @return 企业资质审核
     */
    CompanyQualification findByCompanyId(Long companyId);
    
    /**
     * 更新企业资质审核
     * @param qualification 企业资质审核
     * @return 更新的记录数
     */
    int update(CompanyQualification qualification);
    
    /**
     * 删除企业资质审核
     * @param id 资质ID
     * @return 删除的记录数
     */
    int delete(Long id);
    
    /**
     * 查询所有企业资质审核
     * @return 企业资质审核列表
     */
    List<CompanyQualification> findAll();
    
    /**
     * 动态条件查询企业资质审核
     * @param status 审核状态
     * @param companyName 企业名称
     * @return 企业资质审核列表
     */
    List<CompanyQualification> list(String status, String companyName);
    
    /**
     * 根据ID列表查询企业资质审核
     * @param ids 资质ID列表
     * @return 企业资质审核列表
     */
    List<CompanyQualification> findByIds(List<Long> ids);
    
    /**
     * 批量删除企业资质审核
     * @param ids 资质ID列表
     * @return 删除的记录数
     */
    int batchDelete(List<Long> ids);
    
    /**
     * 查询企业资质审核总数
     * @return 企业资质审核总数
     */
    Long count();
    
    /**
     * 分页查询企业资质审核
     * @param page 页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    PageResult<CompanyQualification> findPage(Integer page, Integer pageSize);
    
    /**
     * 分页查询企业资质审核
     * @param page 页码
     * @param pageSize 每页大小
     * @param status 审核状态
     * @param companyName 企业名称
     * @return 分页结果
     */
    PageResult<CompanyQualification> findPage(Integer page, Integer pageSize, String status, String companyName);
    
    /**
     * 根据状态查询资质审核数量
     * @param status 审核状态
     * @return 资质审核数量
     */
    int countByStatus(String status);
    
    /**
     * 批准资质审核
     * @param id 资质ID
     * @param reviewerId 审核人ID
     * @return 更新的记录数
     */
    int approve(Long id, Long reviewerId);
    
    /**
     * 驳回资质审核
     * @param id 资质ID
     * @param reviewerId 审核人ID
     * @param rejectReason 驳回理由
     * @return 更新的记录数
     */
    int reject(Long id, Long reviewerId, String rejectReason);
}
