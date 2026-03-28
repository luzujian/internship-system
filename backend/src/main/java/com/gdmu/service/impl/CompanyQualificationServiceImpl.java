package com.gdmu.service.impl;

import com.gdmu.entity.CompanyQualification;
import com.gdmu.entity.PageResult;
import com.gdmu.exception.BusinessException;
import com.gdmu.mapper.CompanyQualificationMapper;
import com.gdmu.service.CompanyQualificationService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 企业资质审核服务实现类
 */
@Slf4j
@Service
public class CompanyQualificationServiceImpl implements CompanyQualificationService {
    
    private final CompanyQualificationMapper companyQualificationMapper;
    
    @Autowired
    public CompanyQualificationServiceImpl(CompanyQualificationMapper companyQualificationMapper) {
        this.companyQualificationMapper = companyQualificationMapper;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insert(CompanyQualification qualification) {
        log.debug("插入企业资质审核: {}", qualification.getCompanyName());
        
        validateQualificationInfo(qualification);
        
        int result = companyQualificationMapper.insert(qualification);
        log.info("企业资质审核插入成功，企业: {}", qualification.getCompanyName());
        return result;
    }
    
    @Override
    public CompanyQualification findById(Long id) {
        log.debug("根据ID查询企业资质审核，ID: {}", id);
        
        if (id == null || id <= 0) {
            throw new BusinessException("资质ID无效");
        }
        
        return companyQualificationMapper.findById(id);
    }
    
    @Override
    public CompanyQualification findByCompanyId(Long companyId) {
        log.debug("根据企业ID查询资质审核，企业ID: {}", companyId);
        return companyQualificationMapper.findByCompanyId(companyId);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int update(CompanyQualification qualification) {
        log.debug("更新企业资质审核，ID: {}", qualification.getId());
        
        if (qualification.getId() == null || qualification.getId() <= 0) {
            throw new BusinessException("资质ID无效");
        }
        
        CompanyQualification existingQualification = companyQualificationMapper.findById(qualification.getId());
        if (existingQualification == null) {
            throw new BusinessException("资质审核不存在");
        }
        
        validateQualificationInfo(qualification);
        
        int result = companyQualificationMapper.update(qualification);
        log.info("企业资质审核更新成功，ID: {}", qualification.getId());
        return result;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        log.debug("删除企业资质审核，ID: {}", id);
        
        if (id == null || id <= 0) {
            throw new BusinessException("资质ID无效");
        }
        
        CompanyQualification qualification = companyQualificationMapper.findById(id);
        if (qualification == null) {
            throw new BusinessException("资质审核不存在");
        }
        
        int result = companyQualificationMapper.deleteById(id);
        log.info("企业资质审核删除成功，ID: {}", id);
        return result;
    }
    
    @Override
    public List<CompanyQualification> findAll() {
        log.debug("查询所有企业资质审核");
        return companyQualificationMapper.findAll();
    }
    
    @Override
    public List<CompanyQualification> list(String status, String companyName) {
        log.debug("动态条件查询企业资质审核，状态: {}, 企业名称: {}", status, companyName);
        return companyQualificationMapper.list(status, companyName);
    }
    
    @Override
    public List<CompanyQualification> findByIds(List<Long> ids) {
        log.debug("根据ID列表查询企业资质审核，ID列表: {}", ids);
        
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        
        return companyQualificationMapper.selectByIds(ids);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchDelete(List<Long> ids) {
        log.debug("批量删除企业资质审核，ID列表: {}", ids);
        
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("资质ID列表不能为空");
        }
        
        int result = companyQualificationMapper.batchDeleteByIds(ids);
        log.info("批量删除企业资质审核成功，删除数量: {}", result);
        return result;
    }
    
    @Override
    public Long count() {
        log.debug("查询企业资质审核总数");
        return companyQualificationMapper.count();
    }
    
    @Override
    public PageResult<CompanyQualification> findPage(Integer page, Integer pageSize) {
        log.debug("分页查询企业资质审核，页码: {}, 每页大小: {}", page, pageSize);
        return findPage(page, pageSize, null, null);
    }
    
    @Override
    public PageResult<CompanyQualification> findPage(Integer page, Integer pageSize, String status, String companyName) {
        log.debug("分页查询企业资质审核，页码: {}, 每页大小: {}, 状态: {}, 企业名称: {}", 
                page, pageSize, status, companyName);
        
        PageHelper.startPage(page, pageSize);
        List<CompanyQualification> qualifications = companyQualificationMapper.list(status, companyName);
        
        PageInfo<CompanyQualification> pageInfo = new PageInfo<>(qualifications);
        return PageResult.build(pageInfo.getTotal(), pageInfo.getList(),
                pageInfo.getPages(), pageInfo.getPageNum(), pageInfo.getPageSize());
    }
    
    @Override
    public int countByStatus(String status) {
        log.debug("根据状态查询资质审核数量，状态: {}", status);
        return companyQualificationMapper.countByStatus(status);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int approve(Long id, Long reviewerId) {
        log.debug("批准企业资质审核，ID: {}, 审核人ID: {}", id, reviewerId);
        
        if (id == null || id <= 0) {
            throw new BusinessException("资质ID无效");
        }
        
        CompanyQualification qualification = companyQualificationMapper.findById(id);
        if (qualification == null) {
            throw new BusinessException("资质审核不存在");
        }
        
        if (!"pending".equals(qualification.getStatus())) {
            throw new BusinessException("该资质已审核，无法重复操作");
        }
        
        qualification.setStatus("approved");
        qualification.setReviewerId(reviewerId);
        qualification.setReviewTime(new Date());
        
        int result = companyQualificationMapper.update(qualification);
        log.info("企业资质审核批准成功，ID: {}", id);
        return result;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int reject(Long id, Long reviewerId, String rejectReason) {
        log.debug("驳回企业资质审核，ID: {}, 审核人ID: {}, 驳回理由: {}", id, reviewerId, rejectReason);
        
        if (id == null || id <= 0) {
            throw new BusinessException("资质ID无效");
        }
        
        if (StringUtils.isBlank(rejectReason)) {
            throw new BusinessException("驳回理由不能为空");
        }
        
        CompanyQualification qualification = companyQualificationMapper.findById(id);
        if (qualification == null) {
            throw new BusinessException("资质审核不存在");
        }
        
        if (!"pending".equals(qualification.getStatus())) {
            throw new BusinessException("该资质已审核，无法重复操作");
        }
        
        qualification.setStatus("rejected");
        qualification.setReviewerId(reviewerId);
        qualification.setReviewTime(new Date());
        qualification.setRejectReason(rejectReason);
        
        int result = companyQualificationMapper.update(qualification);
        log.info("企业资质审核驳回成功，ID: {}", id);
        return result;
    }
    
    /**
     * 验证资质信息
     */
    private void validateQualificationInfo(CompanyQualification qualification) {
        if (qualification == null) {
            throw new BusinessException("资质信息不能为空");
        }
        
        if (qualification.getCompanyId() == null) {
            throw new BusinessException("企业ID不能为空");
        }
        
        if (StringUtils.isBlank(qualification.getCompanyName())) {
            throw new BusinessException("企业名称不能为空");
        }
        
        if (StringUtils.isBlank(qualification.getContactPerson())) {
            throw new BusinessException("联系人不能为空");
        }
        
        if (StringUtils.isBlank(qualification.getContactPhone())) {
            throw new BusinessException("联系电话不能为空");
        }
    }
}
