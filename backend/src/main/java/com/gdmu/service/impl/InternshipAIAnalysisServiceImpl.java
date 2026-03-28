package com.gdmu.service.impl;

import com.gdmu.entity.InternshipAIAnalysis;
import com.gdmu.exception.BusinessException;
import com.gdmu.mapper.InternshipAIAnalysisMapper;
import com.gdmu.service.InternshipAIAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class InternshipAIAnalysisServiceImpl implements InternshipAIAnalysisService {
    
    private final InternshipAIAnalysisMapper internshipAIAnalysisMapper;
    
    @Autowired
    public InternshipAIAnalysisServiceImpl(InternshipAIAnalysisMapper internshipAIAnalysisMapper) {
        this.internshipAIAnalysisMapper = internshipAIAnalysisMapper;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insert(InternshipAIAnalysis analysis) {
        log.debug("插入AI分析结果，学生ID: {}", analysis.getStudentId());
        
        if (analysis.getStudentId() == null || analysis.getStudentId() <= 0) {
            throw new BusinessException("学生ID无效");
        }
        
        Date now = new Date();
        analysis.setAnalysisTime(now);
        analysis.setUpdateTime(now);
        
        int result = internshipAIAnalysisMapper.insert(analysis);
        log.info("AI分析结果插入成功，学生ID: {}", analysis.getStudentId());
        return result;
    }
    
    @Override
    public InternshipAIAnalysis findById(Long id) {
        log.debug("根据ID查询AI分析结果，ID: {}", id);
        
        if (id == null || id <= 0) {
            throw new BusinessException("ID无效");
        }
        
        return internshipAIAnalysisMapper.findById(id);
    }
    
    @Override
    public InternshipAIAnalysis findByStudentId(Long studentId) {
        log.debug("根据学生ID查询AI分析结果，学生ID: {}", studentId);
        
        if (studentId == null || studentId <= 0) {
            throw new BusinessException("学生ID无效");
        }
        
        return internshipAIAnalysisMapper.findByStudentId(studentId);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int update(InternshipAIAnalysis analysis) {
        log.debug("更新AI分析结果，ID: {}", analysis.getId());
        
        if (analysis.getId() == null || analysis.getId() <= 0) {
            throw new BusinessException("ID无效");
        }
        
        analysis.setUpdateTime(new Date());
        
        int result = internshipAIAnalysisMapper.update(analysis);
        log.info("AI分析结果更新成功，ID: {}", analysis.getId());
        return result;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteById(Long id) {
        log.debug("删除AI分析结果，ID: {}", id);
        
        if (id == null || id <= 0) {
            throw new BusinessException("ID无效");
        }
        
        int result = internshipAIAnalysisMapper.deleteById(id);
        log.info("AI分析结果删除成功，ID: {}", id);
        return result;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByStudentId(Long studentId) {
        log.debug("根据学生ID删除AI分析结果，学生ID: {}", studentId);
        
        if (studentId == null || studentId <= 0) {
            throw new BusinessException("学生ID无效");
        }
        
        int result = internshipAIAnalysisMapper.deleteByStudentId(studentId);
        log.info("AI分析结果删除成功，学生ID: {}", studentId);
        return result;
    }
    
    @Override
    public List<InternshipAIAnalysis> findAll() {
        log.debug("查询所有AI分析结果");
        return internshipAIAnalysisMapper.findAll();
    }
}
