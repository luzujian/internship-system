package com.gdmu.service;

import com.gdmu.entity.CompanyNotification;

import java.util.List;

public interface CompanyNotificationService {
    
    int insert(CompanyNotification notification);
    
    CompanyNotification findById(Long id);
    
    List<CompanyNotification> findByCompanyId(Long companyId, Integer limit);
    
    List<CompanyNotification> findUnreadByCompanyId(Long companyId, Integer limit);
    
    int markAsRead(Long id);
    
    int deleteById(Long id);
    
    Long countByCompanyId(Long companyId);
    
    Long countUnreadByCompanyId(Long companyId);
}