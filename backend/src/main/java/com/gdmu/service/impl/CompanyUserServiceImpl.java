package com.gdmu.service.impl;

import com.gdmu.entity.CompanyUser;
import com.gdmu.entity.PageResult;
import com.gdmu.exception.BusinessException;
import com.gdmu.mapper.CompanyUserMapper;
import com.gdmu.service.CompanyUserService;
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

@Slf4j
@Service
public class CompanyUserServiceImpl implements CompanyUserService {

    private final CompanyUserMapper companyUserMapper;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public CompanyUserServiceImpl(CompanyUserMapper companyUserMapper, PasswordEncoder passwordEncoder) {
        this.companyUserMapper = companyUserMapper;
        this.passwordEncoder = passwordEncoder;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insert(CompanyUser companyUser) {
        log.debug("插入企业信息: {}", companyUser.getCompanyName());
        
        validateCompanyInfo(companyUser);

        // 使用 BCrypt 加密密码
        companyUser.setPassword(passwordEncoder.encode(companyUser.getPassword()));

        Date now = new Date();
        companyUser.setCreateTime(now);
        companyUser.setUpdateTime(now);
        
        int result = companyUserMapper.insert(companyUser);
        log.info("企业信息插入成功，企业名称: {}", companyUser.getCompanyName());
        return result;
    }
    
    @Override
    public CompanyUser findById(Long id) {
        log.debug("根据ID查询企业信息，ID: {}", id);
        
        if (id == null || id <= 0) {
            throw new BusinessException("企业ID无效");
        }
        
        return companyUserMapper.findById(id);
    }
    
    @Override
    public CompanyUser findByUsername(String username) {
        log.debug("根据用户名查询企业信息，用户名: {}", username);
        
        if (StringUtils.isBlank(username)) {
            throw new BusinessException("用户名不能为空");
        }
        
        return companyUserMapper.findByUsername(username);
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

        // 如果提供了新密码，则进行加密
        if (StringUtils.isNotBlank(companyUser.getPassword())) {
            // 加密后设置到 existingCompany
            existingCompany.setPassword(passwordEncoder.encode(companyUser.getPassword()));
        }
        // 如果没有提供密码，则保持原密码（不需要任何操作）

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

        existingCompany.setUpdateTime(new Date());

        int result = companyUserMapper.update(existingCompany);
        log.info("企业信息更新成功，企业名称: {}", companyUser.getCompanyName());
        return result;
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
        
        int result = companyUserMapper.deleteById(id);
        log.info("企业信息删除成功，企业名称: {}", company.getCompanyName());
        return result;
    }
    
    @Override
    public List<CompanyUser> findAll() {
        log.debug("查询所有企业信息");
        return companyUserMapper.findAll();
    }
    
    @Override
    public List<CompanyUser> list(String companyName, Integer status) {
        log.debug("动态条件查询企业信息，企业名称: {}, 状态: {}", companyName, status);
        return companyUserMapper.list(companyName, status);
    }
    
    @Override
    public List<CompanyUser> findByIds(List<Long> ids) {
        log.debug("根据ID列表查询企业信息，ID列表: {}", ids);
        
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        
        return companyUserMapper.selectByIds(ids);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchDelete(List<Long> ids) {
        log.debug("批量删除企业信息，ID列表: {}", ids);
        
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("企业ID列表不能为空");
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
        List<CompanyUser> companies = companyUserMapper.listWithRecallStatusAndTag(companyName, status, recallStatus, companyTags);
        
        PageInfo<CompanyUser> pageInfo = new PageInfo<>(companies);
        return PageResult.build(pageInfo.getTotal(), pageInfo.getList(),
                pageInfo.getPages(), pageInfo.getPageNum(), pageInfo.getPageSize());
    }
    
    @Override
    public PageResult<CompanyUser> findPendingAuditPage(Integer page, Integer pageSize) {
        log.debug("分页查询待审核企业信息，页码: {}, 每页大小: {}", page, pageSize);
        return findPendingAuditPage(page, pageSize, null, null, null);
    }
    
    @Override
    public PageResult<CompanyUser> findPendingAuditPage(Integer page, Integer pageSize, String companyName, String contactPerson, String contactPhone) {
        log.info("分页查询待审核企业信息，页码: {}, 每页大小: {}, 企业名称: {}, 联系人: {}, 联系电话: {}", 
                page, pageSize, companyName, contactPerson, contactPhone);
        
        PageHelper.startPage(page, pageSize);
        List<CompanyUser> companies = companyUserMapper.findPendingAudit(companyName, contactPerson, contactPhone);
        
        log.info("查询到 {} 条待审核企业记录", companies != null ? companies.size() : 0);
        
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
        
        if (StringUtils.isBlank(companyUser.getContactEmail())) {
            throw new BusinessException("联系邮箱不能为空");
        }
        
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

        for (int i = 0; i < companyDataList.size(); i++) {
            Map<String, Object> rowData = companyDataList.get(i);
            Map<String, Object> failInfo = new HashMap<>();
            failInfo.put("rowNum", i + 2);
            failInfo.putAll(rowData);

            try {
                CompanyUser companyUser = convertToCompanyUser(rowData);

                if (existingCompanyMap.containsKey(companyUser.getCompanyName())) {
                    failInfo.put("errorMsg", "企业名称已存在");
                    failList.add(failInfo);
                    failCount++;
                    continue;
                }

                validateCompanyInfo(companyUser);

                validCompanyList.add(companyUser);
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
                successCount += batchResult;

                log.info("导入批次 {} 成功，导入数量: {}", i + 1, batchResult);
            }
        }

        log.info("Excel企业数据导入完成，成功: {}, 失败: {}", successCount, failCount);

        Map<String, Object> result = new HashMap<>();
        result.put("successCount", successCount);
        result.put("failCount", failCount);
        result.put("failList", failList);

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
        companyUser.setContactEmail(contactEmail);
        companyUser.setAddress(address);

        companyUser.setUsername(companyName);
        companyUser.setPassword(passwordEncoder.encode("123456"));
        companyUser.setRole("ROLE_COMPANY");
        companyUser.setStatus(1);
        companyUser.setAuditStatus(1);

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
}