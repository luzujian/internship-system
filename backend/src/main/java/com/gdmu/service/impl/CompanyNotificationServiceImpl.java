package com.gdmu.service.impl;

import com.gdmu.entity.CompanyNotification;
import com.gdmu.mapper.CompanyNotificationMapper;
import com.gdmu.service.CompanyNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class CompanyNotificationServiceImpl implements CompanyNotificationService {
    
    private final CompanyNotificationMapper companyNotificationMapper;
    
    @Autowired
    public CompanyNotificationServiceImpl(CompanyNotificationMapper companyNotificationMapper) {
        this.companyNotificationMapper = companyNotificationMapper;
    }
    
    @Override
    public int insert(CompanyNotification notification) {
        if (notification.getCreateTime() == null) {
            notification.setCreateTime(new Date());
        }
        if (notification.getIsRead() == null) {
            notification.setIsRead(false);
        }
        return companyNotificationMapper.insert(notification);
    }
    
    @Override
    public CompanyNotification findById(Long id) {
        return companyNotificationMapper.findById(id);
    }
    
    @Override
    public List<CompanyNotification> findByCompanyId(Long companyId, Integer limit) {
        return companyNotificationMapper.findByCompanyId(companyId, limit);
    }
    
    @Override
    public List<CompanyNotification> findUnreadByCompanyId(Long companyId, Integer limit) {
        return companyNotificationMapper.findUnreadByCompanyId(companyId, limit);
    }
    
    @Override
    public int markAsRead(Long id) {
        return companyNotificationMapper.markAsRead(id, new Date());
    }
    
    @Override
    public int deleteById(Long id) {
        return companyNotificationMapper.deleteById(id);
    }
    
    @Override
    public Long countByCompanyId(Long companyId) {
        return companyNotificationMapper.countByCompanyId(companyId);
    }
    
    @Override
    public Long countUnreadByCompanyId(Long companyId) {
        return companyNotificationMapper.countUnreadByCompanyId(companyId);
    }
}