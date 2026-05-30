package com.internship.service;

import com.internship.entity.CompanyUser;
import com.internship.entity.PageResult;

import java.util.List;
import java.util.Map;

public interface CompanyUserService {
    
    int insert(CompanyUser companyUser);
    
    CompanyUser findById(Long id);
    
    CompanyUser findByUsername(String username);
    
    int update(CompanyUser companyUser);
    int updateProfile(CompanyUser companyUser); // 仅更新资料，不碰密码

    int delete(Long id);
    
    List<CompanyUser> findAll();
    
    List<CompanyUser> list(String companyName, Integer status);
    
    List<CompanyUser> findByIds(List<Long> ids);
    
    int batchDelete(List<Long> ids);
    
    Long count();
    
    PageResult<CompanyUser> findPage(Integer page, Integer pageSize);
    
    PageResult<CompanyUser> findPage(Integer page, Integer pageSize, String companyName, Integer status);
    
    PageResult<CompanyUser> findPage(Integer page, Integer pageSize, String companyName, Integer status, Integer recallStatus);
    
    PageResult<CompanyUser> findPage(Integer page, Integer pageSize, String companyName, Integer status, Integer recallStatus, String companyTag);
    
    PageResult<CompanyUser> findPendingAuditPage(Integer page, Integer pageSize);
    
    PageResult<CompanyUser> findPendingAuditPage(Integer page, Integer pageSize, String companyName, String contactPerson, String contactPhone, Integer status);
    
    Long countByAuditStatus(Integer auditStatus);
    
    PageResult<CompanyUser> findPendingRecallAuditPage(Integer page, Integer pageSize, String companyName, String contactPerson, String contactPhone);

    PageResult<CompanyUser> findPendingRecallAuditPageById(Integer page, Integer pageSize, Long companyId);

    Long countByRecallStatus(Integer recallStatus);
    
    int auditRecallApplication(Long id, Integer recallStatus, String recallAuditRemark, Long recallReviewerId);

    int clearRecallData();

    Map<String, Object> importFromExcel(List<Map<String, Object>> companyDataList);

    PageResult<CompanyUser> findRecallRecordsPage(Integer page, Integer pageSize, String companyName, String contactPerson);

    PageResult<CompanyUser> findRecallRecordsPage(Integer page, Integer pageSize, String companyName, String contactPerson, String startTime, String endTime);

    List<CompanyUser> findRecallRecords(String companyName, String contactPerson, String startTime, String endTime);

    int deleteRecallRecord(Long id);

    int batchDeleteRecallRecords(List<Long> ids);

    /**
     * 获取所有不重复的企业标签
     */
    List<String> getDistinctTags();

    /**
     * 批准企业注册申请（带事务，防并发重复审批）
     * @param companyId 企业ID
     * @param reviewerId 审核人ID
     * @return 更新记录数
     */
    int approveQualification(Long companyId, Long reviewerId);

    /**
     * 驳回企业注册申请（带事务，防并发重复审批）
     * @param companyId 企业ID
     * @param reviewerId 审核人ID
     * @param reason 驳回原因
     * @return 更新记录数
     */
    int rejectQualification(Long companyId, Long reviewerId, String reason);
}