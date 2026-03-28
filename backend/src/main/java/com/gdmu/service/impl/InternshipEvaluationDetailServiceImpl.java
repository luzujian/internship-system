package com.gdmu.service.impl;

import com.gdmu.entity.InternshipEvaluationDetail;
import com.gdmu.mapper.InternshipEvaluationDetailMapper;
import com.gdmu.service.InternshipEvaluationDetailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
public class InternshipEvaluationDetailServiceImpl implements InternshipEvaluationDetailService {
    
    @Autowired
    private InternshipEvaluationDetailMapper evaluationDetailMapper;
    
    @Override
    public int insert(InternshipEvaluationDetail detail) {
        if (detail.getCreateTime() == null) {
            detail.setCreateTime(new Date());
        }
        if (detail.getUpdateTime() == null) {
            detail.setUpdateTime(new Date());
        }
        return evaluationDetailMapper.insert(detail);
    }
    
    @Override
    public int update(InternshipEvaluationDetail detail) {
        detail.setUpdateTime(new Date());
        return evaluationDetailMapper.update(detail);
    }
    
    @Override
    public int deleteById(Long id) {
        return evaluationDetailMapper.deleteById(id);
    }
    
    @Override
    public int deleteByEvaluationId(Long evaluationId) {
        return evaluationDetailMapper.deleteByEvaluationId(evaluationId);
    }
    
    @Override
    public int deleteByStudentId(Long studentId) {
        return evaluationDetailMapper.deleteByStudentId(studentId);
    }
    
    @Override
    public InternshipEvaluationDetail findById(Long id) {
        return evaluationDetailMapper.findById(id);
    }
    
    @Override
    public List<InternshipEvaluationDetail> findByEvaluationId(Long evaluationId) {
        return evaluationDetailMapper.findByEvaluationId(evaluationId);
    }
    
    @Override
    public List<InternshipEvaluationDetail> findByStudentId(Long studentId) {
        return evaluationDetailMapper.findByStudentId(studentId);
    }
    
    @Override
    public int batchInsert(List<InternshipEvaluationDetail> details) {
        if (details == null || details.isEmpty()) {
            return 0;
        }
        Date now = new Date();
        for (InternshipEvaluationDetail detail : details) {
            if (detail.getCreateTime() == null) {
                detail.setCreateTime(now);
            }
            if (detail.getUpdateTime() == null) {
                detail.setUpdateTime(now);
            }
        }
        return evaluationDetailMapper.batchInsert(details);
    }
    
    @Override
    @Transactional
    public int saveEvaluationDetails(Long evaluationId, Long studentId, List<InternshipEvaluationDetail> details) {
        if (details == null || details.isEmpty()) {
            return 0;
        }
        
        evaluationDetailMapper.deleteByEvaluationId(evaluationId);
        
        for (InternshipEvaluationDetail detail : details) {
            detail.setEvaluationId(evaluationId);
            detail.setStudentId(studentId);
            if (detail.getCreateTime() == null) {
                detail.setCreateTime(new Date());
            }
            if (detail.getUpdateTime() == null) {
                detail.setUpdateTime(new Date());
            }
        }
        
        return evaluationDetailMapper.batchInsert(details);
    }
}
