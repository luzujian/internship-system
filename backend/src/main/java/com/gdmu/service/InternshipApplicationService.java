package com.gdmu.service;

import com.gdmu.entity.InternshipApplicationEntity;

import java.util.List;

public interface InternshipApplicationService {
    
    int insert(InternshipApplicationEntity application);
    
    InternshipApplicationEntity findById(Long id);
    
    List<InternshipApplicationEntity> findByCompanyId(Long companyId);
    
    List<InternshipApplicationEntity> findByPositionId(Long positionId);
    
    List<InternshipApplicationEntity> findByStudentId(Long studentId);
    
    List<InternshipApplicationEntity> findByStatus(String status);
    
    List<InternshipApplicationEntity> findByCompanyIdAndStatus(Long companyId, String status);
    
    boolean updateStatus(Long id, String status);
    
    boolean markAsViewed(Long id);
    
    boolean deleteById(Long id);
    
    List<InternshipApplicationEntity> findAll();
    
    Long countByCompanyId(Long companyId);
    
    Long countByCompanyIdAndStatus(Long companyId, String status);
}