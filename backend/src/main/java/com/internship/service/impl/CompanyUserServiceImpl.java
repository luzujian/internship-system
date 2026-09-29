package com.internship.service.impl;

import com.internship.entity.CompanyUser;
import com.internship.entity.CompanyAlumniMember;
import com.internship.entity.PageResult;
import com.internship.exception.BusinessException;
import com.internship.mapper.CompanyUserMapper;
import com.internship.service.CompanyUserService;
import com.internship.service.CompanyAlumniService;
import com.internship.utils.UserCredentialDefaults;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CompanyUserServiceImpl implements CompanyUserService {

    private final CompanyUserMapper companyUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final CompanyAlumniService companyAlumniService;

    @Autowired
    public CompanyUserServiceImpl(CompanyUserMapper companyUserMapper,
                                  PasswordEncoder passwordEncoder,
                                  CompanyAlumniService companyAlumniService) {
        this.companyUserMapper = companyUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.companyAlumniService = companyAlumniService;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insert(CompanyUser companyUser) {
        log.debug("插入企业信息: {}", companyUser.getCompanyName());
        companyUser.setPassword(UserCredentialDefaults.PASSWORD);

        if (companyUser.getIsAlumniUnit() == null) {
            companyUser.setIsAlumniUnit(0);
        }
        List<CompanyAlumniMember> alumniMembers = companyAlumniService.normalize(
                companyUser.getIsAlumniUnit(), companyUser.getAlumniMembers());
        companyAlumniService.syncLegacyRepresentative(companyUser, alumniMembers);
        validateCompanyInfo(companyUser);

        // 使用 BCrypt 加密密码
        companyUser.setPassword(passwordEncoder.encode(companyUser.getPassword()));

        Date now = new Date();
        companyUser.setCreateTime(now);
        companyUser.setUpdateTime(now);
        
        int result = companyUserMapper.insert(companyUser);
        if (result > 0) {
            companyAlumniService.replace(companyUser.getId(), alumniMembers);
            companyUser.setAlumniMembers(alumniMembers);
        }
        log.info("企业信息插入成功，企业名称: {}", companyUser.getCompanyName());
        return result;
    }
    
    @Override
    public CompanyUser findById(Long id) {
        log.debug("根据ID查询企业信息，ID: {}", id);
        
        if (id == null || id <= 0) {
            throw new BusinessException("企业ID无效");
        }
        
        CompanyUser company = companyUserMapper.findById(id);
        if (company != null) {
            normalizeLegacyCompanyTag(company);
            company.setAlumniMembers(companyAlumniService.findByCompanyId(id));
        }
        return company;
    }
    
    @Override
    public CompanyUser findByUsername(String username) {
        log.debug("根据用户名查询企业信息，用户名: {}", username);
        
        if (StringUtils.isBlank(username)) {
            throw new BusinessException("用户名不能为空");
        }
        
        CompanyUser company = companyUserMapper.findByUsername(username);
        normalizeLegacyCompanyTag(company);
        return company;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int update(CompanyUser companyUser) {
        log.debug("更新企业信息，ID: {}", companyUser.getId());
        
        if (companyUser.getId() == null || companyUser.getId() <= 0) {
            throw new BusinessException("企业ID无效");
        }
        
        CompanyUser existingCompany = companyUserMapper.findById(companyUser.getId());
        if (existingCompany == null) {
            throw new BusinessException("企业不存在");
        }

        List<CompanyAlumniMember> normalizedAlumniMembers = null;
        boolean alumniUpdateRequested = companyUser.getIsAlumniUnit() != null
                || companyUser.getAlumniMembers() != null;
        if (alumniUpdateRequested) {
            Integer desiredAlumniFlag = companyUser.getIsAlumniUnit() != null
                    ? companyUser.getIsAlumniUnit() : existingCompany.getIsAlumniUnit();
            List<CompanyAlumniMember> desiredMembers = companyUser.getAlumniMembers();
            if (desiredMembers == null && Integer.valueOf(1).equals(desiredAlumniFlag)) {
                desiredMembers = companyAlumniService.findByCompanyId(companyUser.getId());
            }
            normalizedAlumniMembers = companyAlumniService.normalize(
                    desiredAlumniFlag, desiredMembers);
            companyUser.setIsAlumniUnit(desiredAlumniFlag);
            existingCompany.setIsAlumniUnit(desiredAlumniFlag);
            companyAlumniService.syncLegacyRepresentative(existingCompany, normalizedAlumniMembers);
        }

        // 校验用户名唯一性
        if (StringUtils.isNotBlank(companyUser.getUsername())
                && !companyUser.getUsername().equals(existingCompany.getUsername())) {
            CompanyUser byUsername = companyUserMapper.findByUsername(companyUser.getUsername());
            if (byUsername != null) {
                throw new BusinessException("登录账号已存在，请修改");
            }
        }

        // 密码处理：null/空 表示不修改密码，完全保留原值
        String newPassword = companyUser.getPassword();
        String existingPassword = existingCompany.getPassword();
        if (StringUtils.isBlank(newPassword)) {
            // 没有提供新密码，不更新 password 字段（设 null 让 Mapper 跳过）
            existingCompany.setPassword(null);
        } else if (!passwordEncoder.matches(newPassword, existingPassword)
                && !newPassword.equals(existingPassword)) {
            // 密码确实改变了，才编码
            existingCompany.setPassword(passwordEncoder.encode(newPassword));
        }
        // 如果密码未变，保留原值（existingCompany 已有原密码）

        // 如果提供了状态字段，则更新状态
        if (companyUser.getStatus() != null) {
            existingCompany.setStatus(companyUser.getStatus());
        }

        // 复制其他需要更新的字段
        if (companyUser.getCompanyName() != null) {
            existingCompany.setCompanyName(companyUser.getCompanyName());
        }
        if (companyUser.getContactPerson() != null) {
            existingCompany.setContactPerson(companyUser.getContactPerson());
        }
        if (companyUser.getContactPhone() != null) {
            existingCompany.setContactPhone(companyUser.getContactPhone());
        }
        if (companyUser.getContactEmail() != null) {
            existingCompany.setContactEmail(companyUser.getContactEmail());
        }
        if (companyUser.getPhone() != null) {
            existingCompany.setPhone(companyUser.getPhone());
        }
        if (companyUser.getEmail() != null) {
            existingCompany.setEmail(companyUser.getEmail());
        }
        if (companyUser.getAddress() != null) {
            existingCompany.setAddress(companyUser.getAddress());
        }
        if (companyUser.getIntroduction() != null) {
            existingCompany.setIntroduction(companyUser.getIntroduction());
        }
        if (companyUser.getBusinessLicense() != null) {
            existingCompany.setBusinessLicense(companyUser.getBusinessLicense());
        }
        if (companyUser.getLegalIdCard() != null) {
            existingCompany.setLegalIdCard(companyUser.getLegalIdCard());
        }
        if (companyUser.getIsInternshipBase() != null) {
            existingCompany.setIsInternshipBase(companyUser.getIsInternshipBase());
        }
        if (companyUser.getPlaquePhoto() != null) {
            existingCompany.setPlaquePhoto(companyUser.getPlaquePhoto());
        }
        if (companyUser.getAcceptBackup() != null) {
            existingCompany.setAcceptBackup(companyUser.getAcceptBackup());
        }
        if (companyUser.getHasTrainingBaseAgreement() != null) {
            existingCompany.setHasTrainingBaseAgreement(companyUser.getHasTrainingBaseAgreement());
        }
        if (companyUser.getCompanyTag() != null) {
            existingCompany.setCompanyTag(companyUser.getCompanyTag());
        }
        // 基础信息字段
        if (companyUser.getIndustry() != null) {
            existingCompany.setIndustry(companyUser.getIndustry());
        }
        if (companyUser.getScale() != null) {
            existingCompany.setScale(companyUser.getScale());
        }
        if (companyUser.getProvince() != null) {
            existingCompany.setProvince(companyUser.getProvince());
        }
        if (companyUser.getCity() != null) {
            existingCompany.setCity(companyUser.getCity());
        }
        if (companyUser.getDistrict() != null) {
            existingCompany.setDistrict(companyUser.getDistrict());
        }
        if (companyUser.getDetailAddress() != null) {
            existingCompany.setDetailAddress(companyUser.getDetailAddress());
        }
        if (companyUser.getWebsite() != null) {
            existingCompany.setWebsite(companyUser.getWebsite());
        }
        if (companyUser.getCooperationMode() != null) {
            existingCompany.setCooperationMode(companyUser.getCooperationMode());
        }
        if (companyUser.getLogo() != null) {
            existingCompany.setLogo(companyUser.getLogo());
        }
        if (companyUser.getPhotos() != null) {
            existingCompany.setPhotos(companyUser.getPhotos());
        }
        if (companyUser.getVideos() != null) {
            existingCompany.setVideos(companyUser.getVideos());
        }

        // 审核相关字段
        if (companyUser.getAuditStatus() != null) {
            if (Integer.valueOf(1).equals(companyUser.getAuditStatus())
                    && !Integer.valueOf(1).equals(existingCompany.getAuditStatus())) {
                existingCompany.setCooperationMode("mutual_choice");
            }
            existingCompany.setAuditStatus(companyUser.getAuditStatus());
        }
        if (companyUser.getAuditRemark() != null) {
            existingCompany.setAuditRemark(companyUser.getAuditRemark());
        }
        if (companyUser.getAuditTime() != null) {
            existingCompany.setAuditTime(companyUser.getAuditTime());
        }
        if (companyUser.getReviewerId() != null) {
            existingCompany.setReviewerId(companyUser.getReviewerId());
        }
        if (companyUser.getApplyTime() != null) {
            existingCompany.setApplyTime(companyUser.getApplyTime());
        }
        if (companyUser.getRecallStatus() != null) {
            // recall_status 表示当前流程状态；历史撤回字段由显式清除接口维护，普通更新不得抹掉。
            existingCompany.setRecallStatus(companyUser.getRecallStatus());
        }

        existingCompany.setUpdateTime(new Date());

        int result = companyUserMapper.update(existingCompany);
        if (result > 0 && normalizedAlumniMembers != null) {
            companyAlumniService.replace(companyUser.getId(), normalizedAlumniMembers);
            companyUser.setAlumniMembers(normalizedAlumniMembers);
        }
        log.info("企业信息更新成功，企业名称: {}", companyUser.getCompanyName());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateProfile(CompanyUser companyUser) {
        // 此方法仅更新企业资料，明确不碰密码
        CompanyUser existing = companyUserMapper.findById(companyUser.getId());
        if (existing == null) throw new BusinessException("企业不存在");
        companyUser.setPassword(null);
        companyUser.setUsername(null);
        companyUser.setRole(null);
        companyUser.setUpdateTime(new Date());
        return companyUserMapper.update(companyUser);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        log.debug("删除企业信息，ID: {}", id);

        if (id == null || id <= 0) {
            throw new BusinessException("企业ID无效");
        }

        CompanyUser company = companyUserMapper.findById(id);
        if (company == null) {
            throw new BusinessException("企业不存在");
        }

        // 级联清理所有引用 company_id 的业务表（每条独立 try-catch，表不存在时跳过，保证最后删企业成功）
        safeCleanupCompanyRelations(id);

        // 最后删除企业用户记录
        int result = companyUserMapper.deleteById(id);
        log.info("企业信息删除成功，企业名称: {}", company.getCompanyName());
        return result;
    }
    
    @Override
    public List<CompanyUser> findAll() {
        log.debug("查询所有企业信息");
        return normalizeLegacyCompanyTags(companyUserMapper.findAll());
    }
    
    @Override
    public List<CompanyUser> list(String companyName, Integer status) {
        log.debug("动态条件查询企业信息，企业名称: {}, 状态: {}", companyName, status);
        return normalizeLegacyCompanyTags(companyUserMapper.list(companyName, status));
    }
    
    @Override
    public List<CompanyUser> findByIds(List<Long> ids) {
        log.debug("根据ID列表查询企业信息，ID列表: {}", ids);
        
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        
        return normalizeLegacyCompanyTags(companyUserMapper.selectByIds(ids));
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int resetAllPasswords() {
        String passwordHash = passwordEncoder.encode(UserCredentialDefaults.PASSWORD);
        return companyUserMapper.resetAllPasswords(passwordHash);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchDelete(List<Long> ids) {
        log.debug("批量删除企业信息，ID列表: {}", ids);

        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("企业ID列表不能为空");
        }

        // 先逐个清理关联数据（每条跳过不影响整体）
        for (Long id : ids) {
            try {
                safeCleanupCompanyRelations(id);
            } catch (Exception e) {
                log.warn("批量删除预清理企业ID={}关联数据时跳过: {}", id, trimErr(e));
            }
        }

        int result = companyUserMapper.batchDeleteByIds(ids);
        log.info("批量删除企业信息成功，删除数量: {}", result);
        return result;
    }
    
    @Override
    public Long count() {
        log.debug("查询企业总数");
        return companyUserMapper.count();
    }
    
    @Override
    public PageResult<CompanyUser> findPage(Integer page, Integer pageSize) {
        log.debug("分页查询企业信息，页码: {}, 每页大小: {}", page, pageSize);
        return findPage(page, pageSize, null, null);
    }
    
    @Override
    public PageResult<CompanyUser> findPage(Integer page, Integer pageSize, String companyName, Integer status) {
        log.debug("分页查询企业信息，页码: {}, 每页大小: {}, 企业名称: {}, 状态: {}", 
                page, pageSize, companyName, status);
        
        PageHelper.startPage(page, pageSize);
        List<CompanyUser> companies = companyUserMapper.list(companyName, status);
        normalizeLegacyCompanyTags(companies);
        
        PageInfo<CompanyUser> pageInfo = new PageInfo<>(companies);
        return PageResult.build(pageInfo.getTotal(), pageInfo.getList(),
                pageInfo.getPages(), pageInfo.getPageNum(), pageInfo.getPageSize());
    }
    
    @Override
    public PageResult<CompanyUser> findPage(Integer page, Integer pageSize, String companyName, Integer status, Integer recallStatus) {
        log.debug("分页查询企业信息，页码: {}, 每页大小: {}, 企业名称: {}, 状态: {}, 撤回状态: {}", 
                page, pageSize, companyName, status, recallStatus);
        
        PageHelper.startPage(page, pageSize);
        List<CompanyUser> companies = companyUserMapper.listWithRecallStatus(companyName, status, recallStatus);
        normalizeLegacyCompanyTags(companies);
        
        PageInfo<CompanyUser> pageInfo = new PageInfo<>(companies);
        return PageResult.build(pageInfo.getTotal(), pageInfo.getList(),
                pageInfo.getPages(), pageInfo.getPageNum(), pageInfo.getPageSize());
    }
    
    @Override
    public PageResult<CompanyUser> findPage(Integer page, Integer pageSize, String companyName, Integer status, Integer recallStatus, String companyTag) {
        log.debug("分页查询企业信息，页码: {}, 每页大小: {}, 企业名称: {}, 状态: {}, 撤回状态: {}, 企业标签: {}", 
                page, pageSize, companyName, status, recallStatus, companyTag);
        
        List<String> companyTags = null;
        if (companyTag != null && !companyTag.trim().isEmpty()) {
            companyTags = Arrays.asList(companyTag.split(","));
        }
        
        PageHelper.startPage(page, pageSize);
        List<CompanyUser> companies = companyUserMapper.listWithRecallStatusAndTag(companyName, status, recallStatus, companyTags, null);
        normalizeLegacyCompanyTags(companies);
        
        PageInfo<CompanyUser> pageInfo = new PageInfo<>(companies);
        return PageResult.build(pageInfo.getTotal(), pageInfo.getList(),
                pageInfo.getPages(), pageInfo.getPageNum(), pageInfo.getPageSize());
    }
    
    @Override
    public PageResult<CompanyUser> findPage(Integer page, Integer pageSize, String companyName, Integer status, Integer recallStatus, String companyTag, String industry) {
        log.debug("分页查询企业信息，页码: {}, 每页大小: {}, 企业名称: {}, 状态: {}, 撤回状态: {}, 企业标签: {}, 行业: {}",
                page, pageSize, companyName, status, recallStatus, companyTag, industry);

        List<String> companyTags = null;
        if (companyTag != null && !companyTag.trim().isEmpty()) {
            companyTags = Arrays.asList(companyTag.split(","));
        }

        PageHelper.startPage(page, pageSize);
        List<CompanyUser> companies = companyUserMapper.listWithRecallStatusAndTag(companyName, status, recallStatus, companyTags, industry);
        normalizeLegacyCompanyTags(companies);

        PageInfo<CompanyUser> pageInfo = new PageInfo<>(companies);
        return PageResult.build(pageInfo.getTotal(), pageInfo.getList(),
                pageInfo.getPages(), pageInfo.getPageNum(), pageInfo.getPageSize());
    }

    @Override
    public PageResult<CompanyUser> findPendingAuditPage(Integer page, Integer pageSize) {
        log.debug("分页查询待审核企业信息，页码: {}, 每页大小: {}", page, pageSize);
        return findPendingAuditPage(page, pageSize, null, null, null, null);
    }

    @Override
    public PageResult<CompanyUser> findPendingAuditPage(Integer page, Integer pageSize, String companyName, String contactPerson, String contactPhone, Integer status) {
        log.info("分页查询企业注册申请，页码: {}, 每页大小: {}, 企业名称: {}, 联系人: {}, 联系电话: {}, 状态: {}",
                page, pageSize, companyName, contactPerson, contactPhone, status);

        PageHelper.startPage(page, pageSize);
        List<CompanyUser> companies = companyUserMapper.findPendingAuditWithStatus(companyName, contactPerson, contactPhone, status);
        normalizeLegacyCompanyTags(companies);

        log.info("查询到 {} 条企业记录", companies != null ? companies.size() : 0);

        PageInfo<CompanyUser> pageInfo = new PageInfo<>(companies);
        PageResult<CompanyUser> result = PageResult.build(pageInfo.getTotal(), pageInfo.getList(),
                pageInfo.getPages(), pageInfo.getPageNum(), pageInfo.getPageSize());

        log.info("分页结果 - 总数: {}, 列表大小: {}, 总页数: {}, 当前页: {}, 每页大小: {}",
                pageInfo.getTotal(), pageInfo.getList().size(), pageInfo.getPages(), pageInfo.getPageNum(), pageInfo.getPageSize());

        return result;
    }
    
    @Override
    public Long countByAuditStatus(Integer auditStatus) {
        log.debug("根据审核状态统计企业数量，审核状态: {}", auditStatus);
        return companyUserMapper.countByAuditStatus(auditStatus);
    }
    
    @Override
    public Long countByRecallStatus(Integer recallStatus) {
        log.debug("根据撤回状态统计企业数量，撤回状态: {}", recallStatus);
        return companyUserMapper.countByRecallStatus(recallStatus);
    }
    
    @Override
    public PageResult<CompanyUser> findPendingRecallAuditPage(Integer page, Integer pageSize, String companyName, String contactPerson, String contactPhone) {
        log.debug("分页查询待审核撤回申请，页码: {}, 每页大小: {}, 企业名称: {}, 联系人: {}, 联系电话: {}", 
                page, pageSize, companyName, contactPerson, contactPhone);
        
        Integer offset = (page - 1) * pageSize;
        List<CompanyUser> companies = companyUserMapper.findPendingRecallAudit(companyName, contactPerson, contactPhone, offset, pageSize);
        
        Long total = companyUserMapper.countByRecallStatus(1);
        Integer totalPages = (int) Math.ceil((double) total / pageSize);
        
        return PageResult.build(total, companies, totalPages, page, pageSize);
    }
    
    @Override
    public PageResult<CompanyUser> findPendingRecallAuditPageById(Integer page, Integer pageSize, Long companyId) {
        log.debug("根据企业ID查询撤回申请，页码: {}, 每页大小: {}, 企业ID: {}", page, pageSize, companyId);
        
        Integer offset = (page - 1) * pageSize;
        List<CompanyUser> companies = companyUserMapper.findPendingRecallAuditById(companyId, offset, pageSize);
        
        Long total = companyUserMapper.countByRecallStatus(1);
        Integer totalPages = (int) Math.ceil((double) total / pageSize);
        
        return PageResult.build(total, companies, totalPages, page, pageSize);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int auditRecallApplication(Long id, Integer recallStatus, String recallAuditRemark, Long recallReviewerId) {
        log.debug("审核撤回申请，ID: {}, 撤回状态: {}, 审核备注: {}, 审核人ID: {}", 
                id, recallStatus, recallAuditRemark, recallReviewerId);
        
        if (id == null || id <= 0) {
            throw new BusinessException("企业ID无效");
        }
        
        CompanyUser company = companyUserMapper.findById(id);
        if (company == null) {
            throw new BusinessException("企业不存在");
        }
        
        if (company.getRecallStatus() != 1) {
            throw new BusinessException("只能审核待确认的撤回申请");
        }
        
        if (recallStatus != 2 && recallStatus != 3) {
            throw new BusinessException("无效的撤回审核状态");
        }
        
        int result = companyUserMapper.updateRecallStatus(id, recallStatus, recallAuditRemark, recallReviewerId);
        
        if (result > 0) {
            log.info("撤回申请审核成功，企业名称: {}, 审核结果: {}", company.getCompanyName(), 
                    recallStatus == 2 ? "批准" : "拒绝");
        }
        
        return result;
    }
    
    private void validateCompanyInfo(CompanyUser companyUser) {
        if (companyUser == null) {
            throw new BusinessException("企业信息不能为空");
        }
        
        if (StringUtils.isBlank(companyUser.getCompanyName())) {
            throw new BusinessException("企业名称不能为空");
        }
        
        if (StringUtils.isBlank(companyUser.getContactPerson())) {
            throw new BusinessException("联系人不能为空");
        }
        
        if (StringUtils.isBlank(companyUser.getContactPhone())) {
            throw new BusinessException("联系电话不能为空");
        }

        // 联系邮箱选填，与注册逻辑保持一致
        if (StringUtils.isBlank(companyUser.getAddress())) {
            throw new BusinessException("企业地址不能为空");
        }
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importFromExcel(List<Map<String, Object>> companyDataList) {
        log.info("开始从Excel导入企业数据，总数据量: {}", companyDataList != null ? companyDataList.size() : 0);

        int successCount = 0;
        int failCount = 0;
        List<Map<String, Object>> failList = new ArrayList<>();

        if (companyDataList == null || companyDataList.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("successCount", successCount);
            result.put("failCount", failCount);
            result.put("failList", failList);
            return result;
        }

        List<String> allCompanyNames = new ArrayList<>();
        for (Map<String, Object> rowData : companyDataList) {
            String companyName = getStringValue(rowData, "企业名称");
            if (StringUtils.isNotBlank(companyName)) {
                allCompanyNames.add(companyName);
            }
        }

        Map<String, CompanyUser> existingCompanyMap = new HashMap<>();
        if (!allCompanyNames.isEmpty()) {
            for (String companyName : allCompanyNames) {
                CompanyUser existingCompany = companyUserMapper.findByUsername(companyName);
                if (existingCompany != null) {
                    existingCompanyMap.put(companyName, existingCompany);
                }
            }
        }

        List<CompanyUser> validCompanyList = new ArrayList<>();
        Map<String, String> pendingInitialPasswords = new HashMap<>();
        List<Map<String, String>> initialCredentials = new ArrayList<>();

        for (int i = 0; i < companyDataList.size(); i++) {
            Map<String, Object> rowData = companyDataList.get(i);
            Map<String, Object> failInfo = new HashMap<>();
            failInfo.put("rowNum", i + 2);
            failInfo.putAll(rowData);

            try {
                // 跳过企业名称为空的空行（如模板自带的填写说明行）
                if (StringUtils.isBlank(getStringValue(rowData, "企业名称"))) {
                    continue;
                }
                CompanyUser companyUser = convertToCompanyUser(rowData);

                if (existingCompanyMap.containsKey(companyUser.getCompanyName())) {
                    failInfo.put("errorMsg", "企业名称已存在");
                    failList.add(failInfo);
                    failCount++;
                    continue;
                }

                validateCompanyInfo(companyUser);
                String initialPassword = UserCredentialDefaults.PASSWORD;
                companyUser.setPassword(passwordEncoder.encode(initialPassword));

                validCompanyList.add(companyUser);
                pendingInitialPasswords.put(companyUser.getCompanyName(), initialPassword);
                existingCompanyMap.put(companyUser.getCompanyName(), companyUser);

            } catch (Exception e) {
                log.error("导入企业数据失败，行号: {}", i + 2, e);
                failInfo.put("errorMsg", e.getMessage());
                failList.add(failInfo);
                failCount++;
            }
        }

        if (!validCompanyList.isEmpty()) {
            final int BATCH_SIZE = 100;
            int totalSize = validCompanyList.size();
            int batchCount = (totalSize + BATCH_SIZE - 1) / BATCH_SIZE;

            for (int i = 0; i < batchCount; i++) {
                int start = i * BATCH_SIZE;
                int end = Math.min(start + BATCH_SIZE, totalSize);
                List<CompanyUser> batchList = validCompanyList.subList(start, end);

                int batchResult = companyUserMapper.batchInsert(batchList);
                if (batchResult != batchList.size()) {
                    throw new BusinessException("企业导入未全部写入，请重试");
                }
                successCount += batchResult;
                for (CompanyUser insertedCompany : batchList) {
                    initialCredentials.add(Map.of("username", insertedCompany.getUsername(),
                            "password", pendingInitialPasswords.get(insertedCompany.getCompanyName())));
                }

                log.info("导入批次 {} 成功，导入数量: {}", i + 1, batchResult);
            }
        }

        log.info("Excel企业数据导入完成，成功: {}, 失败: {}", successCount, failCount);

        Map<String, Object> result = new HashMap<>();
        result.put("successCount", successCount);
        result.put("failCount", failCount);
        result.put("failList", failList);
        result.put("initialCredentials", initialCredentials);

        return result;
    }

    private CompanyUser convertToCompanyUser(Map<String, Object> rowData) {
        log.debug("处理Excel行数据: {}", rowData);
        CompanyUser companyUser = new CompanyUser();

        String companyName = getStringValue(rowData, "企业名称");
        String contactPerson = getStringValue(rowData, "联系人");
        String contactPhone = getStringValue(rowData, "联系电话");
        String contactEmail = getStringValue(rowData, "联系邮箱");
        String address = getStringValue(rowData, "企业地址");

        log.debug("解析企业信息 - 企业名称: {}, 联系人: {}, 联系电话: {}", companyName, contactPerson, contactPhone);

        companyUser.setCompanyName(companyName);
        companyUser.setContactPerson(contactPerson);
        companyUser.setContactPhone(contactPhone);
        // 联系邮箱选填，但数据库列非空，缺失时存空字符串
        companyUser.setContactEmail(contactEmail != null ? contactEmail : "");
        companyUser.setAddress(address);

        companyUser.setUsername(companyName);
        companyUser.setRole("ROLE_COMPANY");
        companyUser.setStatus(1);
        companyUser.setAuditStatus(1);
        companyUser.setIsAlumniUnit(0);
        // 管理员导入的企业统一贴上"校企双向选择"标签
        companyUser.setCompanyTag("校企双向选择");

        Date now = new Date();
        companyUser.setCreateTime(now);
        companyUser.setUpdateTime(now);

        String phone = getStringValue(rowData, "手机号");
        if (StringUtils.isNotBlank(phone)) {
            companyUser.setPhone(phone);
        }

        String introduction = getStringValue(rowData, "企业简介");
        if (StringUtils.isNotBlank(introduction)) {
            companyUser.setIntroduction(introduction);
        }

        String businessLicense = getStringValue(rowData, "营业执照");
        if (StringUtils.isNotBlank(businessLicense)) {
            companyUser.setBusinessLicense(businessLicense);
        }

        String legalIdCard = getStringValue(rowData, "法人身份证");
        if (StringUtils.isNotBlank(legalIdCard)) {
            companyUser.setLegalIdCard(legalIdCard);
        }

        return companyUser;
    }

    private String getStringValue(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key)) {
            return null;
        }

        Object value = map.get(key);
        if (value == null) {
            return null;
        }

        return value.toString().trim();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int clearRecallData() {
        log.info("开始清除企业撤回申请数据");

        try {
            int count = companyUserMapper.clearRecallData();
            log.info("清除企业撤回申请数据成功，共清除{}条记录", count);
            return count;
        } catch (Exception e) {
            log.error("清除企业撤回申请数据失败: {}", e.getMessage(), e);
            throw new BusinessException("清除撤回申请数据失败: " + e.getMessage());
        }
    }

    @Override
    public PageResult<CompanyUser> findRecallRecordsPage(Integer page, Integer pageSize, String companyName, String contactPerson) {
        return findRecallRecordsPage(page, pageSize, companyName, contactPerson, null, null);
    }

    @Override
    public PageResult<CompanyUser> findRecallRecordsPage(Integer page, Integer pageSize, String companyName, String contactPerson, String startTime, String endTime) {
        log.debug("分页查询企业撤回记录，页码: {}, 每页大小: {}, 企业名称: {}, 联系人: {}, 开始时间: {}, 结束时间: {}",
                page, pageSize, companyName, contactPerson, startTime, endTime);
        
        PageHelper.startPage(page, pageSize);
        List<CompanyUser> records = companyUserMapper.findRecallRecords(companyName, contactPerson, startTime, endTime);
        PageInfo<CompanyUser> pageInfo = new PageInfo<>(records);
        
        return PageResult.build(pageInfo.getTotal(), pageInfo.getList(), pageInfo.getPages(), page, pageSize);
    }

    @Override
    public List<CompanyUser> findRecallRecords(String companyName, String contactPerson, String startTime, String endTime) {
        log.debug("查询企业撤回记录列表（不分页），企业名称: {}, 联系人: {}, 开始时间: {}, 结束时间: {}",
                companyName, contactPerson, startTime, endTime);
        return companyUserMapper.findRecallRecords(companyName, contactPerson, startTime, endTime);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteRecallRecord(Long id) {
        log.debug("删除企业撤回记录，ID: {}", id);
        
        if (id == null || id <= 0) {
            throw new BusinessException("企业ID无效");
        }
        
        CompanyUser company = companyUserMapper.findById(id);
        if (company == null) {
            throw new BusinessException("企业不存在");
        }
        
        return companyUserMapper.clearRecallDataById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchDeleteRecallRecords(List<Long> ids) {
        log.debug("批量删除企业撤回记录，IDs: {}", ids);

        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("ID列表不能为空");
        }

        int count = 0;
        for (Long id : ids) {
            count += companyUserMapper.clearRecallDataById(id);
        }

        return count;
    }

    @Override
    public List<String> getDistinctTags() {
        List<String> rawTags = companyUserMapper.getDistinctTags();
        // company_tag 字段存的是逗号分隔的多标签，需要拆分后去重排序
        // 再合并合作模式映射标签与状态类标签（实习基地/已签协议/校友单位），保证筛选下拉完整
        java.util.Set<String> result = new java.util.TreeSet<>();
        if (rawTags != null) {
            rawTags.stream()
                    .flatMap(tag -> Arrays.stream(tag.split(",")))
                    .map(String::trim)
                    .map(tag -> "双向选择阶段".equals(tag) ? "校企双向选择" : tag)
                    .filter(StringUtils::isNotBlank)
                    .forEach(result::add);
        }
        List<String> modeTags = companyUserMapper.getDistinctCooperationModeTags();
        if (modeTags != null) result.addAll(modeTags);
        List<String> flagTags = companyUserMapper.getDistinctFlagTags();
        if (flagTags != null) result.addAll(flagTags);
        return new ArrayList<>(result);
    }

    private List<CompanyUser> normalizeLegacyCompanyTags(List<CompanyUser> companies) {
        if (companies != null) companies.forEach(this::normalizeLegacyCompanyTag);
        return companies;
    }

    private void normalizeLegacyCompanyTag(CompanyUser company) {
        if (company != null && company.getCompanyTag() != null) {
            company.setCompanyTag(company.getCompanyTag()
                    .replace("双向选择阶段", "校企双向选择"));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int approveQualification(Long companyId, Long reviewerId) {
        CompanyUser company = companyUserMapper.findById(companyId);
        if (company == null) {
            throw new BusinessException("企业不存在");
        }
        if (company.getAuditStatus() != null && company.getAuditStatus() != 0) {
            throw new BusinessException("该企业已审核，无法重复操作");
        }
        company.setCooperationMode("mutual_choice");
        company.setAuditStatus(1);
        company.setStatus(1);
        company.setAuditTime(new Date());
        company.setReviewerId(reviewerId);
        return companyUserMapper.update(company);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int rejectQualification(Long companyId, Long reviewerId, String reason) {
        CompanyUser company = companyUserMapper.findById(companyId);
        if (company == null) {
            throw new BusinessException("企业不存在");
        }
        if (company.getAuditStatus() != null && company.getAuditStatus() != 0) {
            throw new BusinessException("该企业已审核，无法重复操作");
        }
        company.setAuditStatus(2);
        company.setAuditTime(new Date());
        company.setReviewerId(reviewerId);
        company.setAuditRemark(reason);
        return companyUserMapper.update(company);
    }

    /**
     * 安全级联清理企业关联的所有业务表。
     * 每条清理都独立 try-catch，表不存在/外键异常只打 WARN，保证最后能删企业本身。
     */
    private void safeCleanupCompanyRelations(Long companyId) {
        // ===== Step 1. 先删依赖子记录 =====
        // 实习鉴定文件（外键到 internship_appraisal_submission，必须先删文件再删提交）
        try {
            companyUserMapper.deleteAppraisalFileByCompanyId(companyId);
        } catch (Exception e) { log.warn("跳过清理 company→internship_appraisal_file: {}", trimErr(e)); }

        // ===== Step 2. 删岗位（position.company_id → 删企业前必须先删全部岗位） =====
        try {
            companyUserMapper.deletePositionsByCompanyId(companyId);
        } catch (Exception e) { log.warn("跳过清理 company→position: {}", trimErr(e)); }

        // ===== Step 3. 实习申请 / 岗位申请 =====
        try {
            companyUserMapper.deleteInternshipApplicationByCompanyId(companyId);
        } catch (Exception e) { log.warn("跳过清理 company→internship_application: {}", trimErr(e)); }
        try {
            companyUserMapper.deleteJobApplicationByCompanyId(companyId);
        } catch (Exception e) { log.warn("跳过清理 company→student_job_application: {}", trimErr(e)); }

        // ===== Step 4. 实习确认 / 阶段分配 / 实习状态 =====
        try {
            companyUserMapper.deleteConfirmationRecordByCompanyId(companyId);
        } catch (Exception e) { log.warn("跳过清理 company→internship_confirmation_record: {}", trimErr(e)); }
        try {
            companyUserMapper.deleteStageAssignmentByCompanyId(companyId);
        } catch (Exception e) { log.warn("跳过清理 company→internship_stage_assignment: {}", trimErr(e)); }
        try {
            companyUserMapper.deleteInternshipStatusByCompanyId(companyId);
        } catch (Exception e) { log.warn("跳过清理 company→student_internship_status: {}", trimErr(e)); }

        // ===== Step 5. 面试邀请 / 通知 =====
        try {
            companyUserMapper.deleteInterviewInvitationByCompanyId(companyId);
        } catch (Exception e) { log.warn("跳过清理 company→interview_invitation: {}", trimErr(e)); }
        try {
            companyUserMapper.deleteCompanyNotificationByCompanyId(companyId);
        } catch (Exception e) { log.warn("跳过清理 company→company_notification: {}", trimErr(e)); }

        // ===== Step 6. 企业扩展信息：资质 / 校企联络员 / 校友成员 / 访问记录 / 推荐评级 =====
        try {
            companyUserMapper.deleteCompanyQualificationByCompanyId(companyId);
        } catch (Exception e) { log.warn("跳过清理 company→company_qualification: {}", trimErr(e)); }
        try {
            companyUserMapper.deleteCompanySchoolLiaisonByCompanyId(companyId);
        } catch (Exception e) { log.warn("跳过清理 company→company_school_liaison: {}", trimErr(e)); }
        try {
            companyUserMapper.deleteCompanyAlumniMemberByCompanyId(companyId);
        } catch (Exception e) { log.warn("跳过清理 company→company_alumni_member: {}", trimErr(e)); }
        try {
            companyUserMapper.deleteCompanyVisitRecordByCompanyId(companyId);
        } catch (Exception e) { log.warn("跳过清理 company→company_visit_record: {}", trimErr(e)); }
        try {
            companyUserMapper.deleteCompanyRecommendationRatingByCompanyId(companyId);
        } catch (Exception e) { log.warn("跳过清理 company→company_recommendation_rating: {}", trimErr(e)); }

        // ===== Step 7. 企业实习鉴定提交（在子记录和岗位之后） =====
        try {
            companyUserMapper.deleteAppraisalSubmissionByCompanyId(companyId);
        } catch (Exception e) { log.warn("跳过清理 company→internship_appraisal_submission: {}", trimErr(e)); }

        log.debug("企业关联业务数据级联清理完成（已跳过不存在的表），企业ID: {}", companyId);
    }

    /** 提取精简错误信息，避免超长 SQL 堆栈刷屏 */
    private String trimErr(Throwable e) {
        Throwable c = e;
        String lastMsg = (e.getMessage() == null ? "" : e.getMessage().split(";")[0].trim());
        int depth = 0;
        while (c.getCause() != null && depth < 5) {
            c = c.getCause();
            if (c.getMessage() != null) {
                String m = c.getMessage().split(";")[0].trim();
                if (m.length() < 160) lastMsg = m;
            }
            depth++;
        }
        return lastMsg.length() > 160 ? lastMsg.substring(0, 160) : lastMsg;
    }
}
