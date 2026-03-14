package com.gdmu.mapper;

import com.gdmu.entity.CompanyNotification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CompanyNotificationMapper {
    
    int insert(CompanyNotification notification);
    
    CompanyNotification findById(Long id);
    
    List<CompanyNotification> findByCompanyId(@Param("companyId") Long companyId, 
                                           @Param("limit") Integer limit);
    
    List<CompanyNotification> findUnreadByCompanyId(@Param("companyId") Long companyId, 
                                                  @Param("limit") Integer limit);
    
    int markAsRead(@Param("id") Long id, 
                   @Param("readTime") java.util.Date readTime);
    
    int deleteById(Long id);
    
    Long countByCompanyId(@Param("companyId") Long companyId);
    
    Long countUnreadByCompanyId(@Param("companyId") Long companyId);
}