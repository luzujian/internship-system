package com.gdmu.mapper;

import com.gdmu.entity.CompanyUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CompanyUserMapper {
    
    int insert(CompanyUser companyUser);
    
    int batchInsert(@Param("companyUsers") List<CompanyUser> companyUsers);
    
    CompanyUser findById(Long id);
    
    CompanyUser findByUsername(@Param("username") String username);
    
    int update(CompanyUser companyUser);
    
    int deleteById(Long id);
    
    List<CompanyUser> findAll();
    
    List<CompanyUser> list(@Param("companyName") String companyName, 
                           @Param("status") Integer status);
    
    List<CompanyUser> listWithRecallStatus(@Param("companyName") String companyName, 
                                             @Param("status") Integer status,
                                             @Param("recallStatus") Integer recallStatus);
    
    List<CompanyUser> listWithRecallStatusAndTag(@Param("companyName") String companyName, 
                                                     @Param("status") Integer status,
                                                     @Param("recallStatus") Integer recallStatus,
                                                     @Param("companyTag") String companyTag);
    
    List<CompanyUser> selectByIds(List<Long> ids);
    
    int batchDeleteByIds(@Param("ids") List<Long> ids);
    
    Long count();
    
    List<CompanyUser> findPage(@Param("offset") Integer offset, @Param("pageSize") Integer pageSize);
    
    int countByCreateTimeAfter(@Param("createTime") java.util.Date createTime);
    
    int countByStatus(@Param("status") Integer status);
    
    List<CompanyUser> selectAll();
    
    List<CompanyUser> findByAuditStatus(@Param("auditStatus") Integer auditStatus);
    
    List<CompanyUser> findPendingAudit(@Param("companyName") String companyName,
                                        @Param("contactPerson") String contactPerson,
                                        @Param("contactPhone") String contactPhone);
    
    Long countByAuditStatus(@Param("auditStatus") Integer auditStatus);
    
    int updateAuditStatus(@Param("id") Long id, 
                          @Param("auditStatus") Integer auditStatus, 
                          @Param("auditRemark") String auditRemark, 
                          @Param("reviewerId") Long reviewerId);
    
    List<CompanyUser> findByRecallStatus(@Param("recallStatus") Integer recallStatus);
    
    List<CompanyUser> findPendingRecallAudit(@Param("companyName") String companyName,
                                             @Param("contactPerson") String contactPerson,
                                             @Param("contactPhone") String contactPhone,
                                             @Param("offset") Integer offset,
                                             @Param("pageSize") Integer pageSize);
    
    List<CompanyUser> findPendingRecallAuditById(@Param("companyId") Long companyId,
                                                @Param("offset") Integer offset,
                                                @Param("pageSize") Integer pageSize);
    
    Long countByRecallStatus(@Param("recallStatus") Integer recallStatus);
    
    int updateRecallStatus(@Param("id") Long id,
                           @Param("recallStatus") Integer recallStatus,
                           @Param("recallAuditRemark") String recallAuditRemark,
                           @Param("recallReviewerId") Long recallReviewerId);
}