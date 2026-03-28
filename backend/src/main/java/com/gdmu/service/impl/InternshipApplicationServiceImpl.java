package com.gdmu.service.impl;

import com.gdmu.entity.InternshipApplicationEntity;
import com.gdmu.mapper.InternshipApplicationMapper;
import com.gdmu.service.InternshipApplicationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class InternshipApplicationServiceImpl implements InternshipApplicationService {
    
    @Autowired
    private InternshipApplicationMapper internshipApplicationMapper;
    
    @Override
    public int insert(InternshipApplicationEntity application) {
        if (application.getCreateTime() == null) {
            application.setCreateTime(new Date());
        }
        if (application.getUpdateTime() == null) {
            application.setUpdateTime(new Date());
        }
        return internshipApplicationMapper.insert(application);
    }
    
    @Override
    public InternshipApplicationEntity findById(Long id) {
        return internshipApplicationMapper.findById(id);
    }
    
    @Override
    public List<InternshipApplicationEntity> findByCompanyId(Long companyId) {
        return internshipApplicationMapper.findByCompanyId(companyId);
    }
    
    @Override
    public List<InternshipApplicationEntity> findByPositionId(Long positionId) {
        return internshipApplicationMapper.findByPositionId(positionId);
    }
    
    @Override
    public List<InternshipApplicationEntity> findByStudentId(Long studentId) {
        return internshipApplicationMapper.findByStudentId(studentId);
    }
    
    @Override
    public List<InternshipApplicationEntity> findByStatus(String status) {
        return internshipApplicationMapper.findByStatus(status);
    }
    
    @Override
    public List<InternshipApplicationEntity> findByCompanyIdAndStatus(Long companyId, String status) {
        return internshipApplicationMapper.findByCompanyIdAndStatus(companyId, status);
    }
    
    @Override
    public boolean updateStatus(Long id, String status) {
        int result = internshipApplicationMapper.updateStatus(id, status);
        return result > 0;
    }
    
    @Override
    public boolean markAsViewed(Long id) {
        int result = internshipApplicationMapper.updateViewed(id, true);
        return result > 0;
    }
    
    @Override
    public boolean deleteById(Long id) {
        int result = internshipApplicationMapper.deleteById(id);
        return result > 0;
    }
    
    @Override
    public List<InternshipApplicationEntity> findAll() {
        return internshipApplicationMapper.findAll();
    }
    
    @Override
    public Long countByCompanyId(Long companyId) {
        return internshipApplicationMapper.countByCompanyId(companyId);
    }
    
    @Override
    public Long countByCompanyIdAndStatus(Long companyId, String status) {
        return internshipApplicationMapper.countByCompanyIdAndStatus(companyId, status);
    }
}