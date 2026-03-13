package com.gdmu.service;

import com.gdmu.entity.CompanyUser;
import com.gdmu.entity.PageResult;

import java.util.List;
import java.util.Map;

public interface CompanyUserService {
    
    int insert(CompanyUser companyUser);
    
    CompanyUser findById(Long id);
    
    CompanyUser findByUsername(String username);
    
    int update(CompanyUser companyUser);
    
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
    
    PageResult<CompanyUser> findPendingAuditPage(Integer page, Integer pageSize, String companyName, String contactPerson, String contactPhone);
    
    Long countByAuditStatus(Integer auditStatus);
    
    PageResult<CompanyUser> findPendingRecallAuditPage(Integer page, Integer pageSize, String companyName, String contactPerson, String contactPhone);

    PageResult<CompanyUser> findPendingRecallAuditPageById(Integer page, Integer pageSize, Long companyId);

    Long countByRecallStatus(Integer recallStatus);
    
    int auditRecallApplication(Long id, Integer recallStatus, String recallAuditRemark, Long recallReviewerId);

    int clearRecallData();

    Map<String, Object> importFromExcel(List<Map<String, Object>> companyDataList);

    PageResult<CompanyUser> findRecallRecordsPage(Integer page, Integer pageSize, String companyName, String contactPerson);

    PageResult<CompanyUser> findRecallRecordsPage(Integer page, Integer pageSize, String companyName, String contactPerson, String startTime, String endTime);

    int deleteRecallRecord(Long id);

    int batchDeleteRecallRecords(List<Long> ids);
}