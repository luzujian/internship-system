package com.gdmu.service.impl;

import com.gdmu.entity.CompanyUser;
import com.gdmu.mapper.CompanyUserMapper;
import com.gdmu.mapper.SystemSettingsMapper;
import com.gdmu.service.CompanyTagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class CompanyTagServiceImpl implements CompanyTagService {
    
    @Autowired
    private CompanyUserMapper companyUserMapper;
    
    @Lazy
    @Autowired
    private SystemSettingsMapper systemSettingsMapper;
    
    @Override
    public String determineCompanyTag(Date registerTime, Integer acceptBackup) {
        return null;
    }
    
    @Override
    public String determineCompanyTags(Date registerTime, Integer acceptBackup, Integer isInternshipBase, String cooperationMode) {
        java.util.List<String> tags = new java.util.ArrayList<>();
        
        if (acceptBackup != null && acceptBackup == 1) {
            tags.add("接受兜底");
        }
        
        if (isInternshipBase != null) {
            if (isInternshipBase == 1) {
                tags.add("国家级");
            } else if (isInternshipBase == 2) {
                tags.add("省级");
            }
        }
        
        if (cooperationMode != null && !cooperationMode.trim().isEmpty()) {
            if ("mutual_choice".equals(cooperationMode)) {
                tags.add("双向选择阶段");
            } else if ("student_contact".equals(cooperationMode)) {
                tags.add("学生自主联系");
            }
        } else if (registerTime != null) {
            try {
                LocalDate registerDate = registerTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                
                LocalDate startDate = getDualSelectionStartDate();
                LocalDate endDate = getDualSelectionEndDate();
                
                if (startDate != null && endDate != null) {
                    if (!registerDate.isBefore(startDate) && !registerDate.isAfter(endDate)) {
                        tags.add("双向选择阶段");
                    } else if (registerDate.isAfter(endDate)) {
                        tags.add("学生自主联系");
                    }
                }
            } catch (Exception e) {
                log.error("判断企业标签失败: {}", e.getMessage(), e);
            }
        }
        
        return tags.isEmpty() ? null : String.join(",", tags);
    }
    
    private LocalDate getDualSelectionStartDate() {
        try {
            com.gdmu.entity.SystemSettings settings = systemSettingsMapper.getLatestSettings();
            if (settings != null && settings.getDualSelectionStartDate() != null) {
                return settings.getDualSelectionStartDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            }
        } catch (Exception e) {
            log.error("获取双向选择开始日期失败: {}", e.getMessage(), e);
        }
        return null;
    }
    
    private LocalDate getDualSelectionEndDate() {
        try {
            com.gdmu.entity.SystemSettings settings = systemSettingsMapper.getLatestSettings();
            if (settings != null && settings.getDualSelectionEndDate() != null) {
                return settings.getDualSelectionEndDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            }
        } catch (Exception e) {
            log.error("获取双向选择结束日期失败: {}", e.getMessage(), e);
        }
        return null;
    }
    
    @Override
    public void updateCompanyTags() {
        try {
            List<CompanyUser> companies = companyUserMapper.findAll();
            for (CompanyUser company : companies) {
                String tag = determineCompanyTags(company.getRegisterTime(), company.getAcceptBackup(), company.getIsInternshipBase(), company.getCooperationMode());
                if (tag != null && !tag.equals(company.getCompanyTag())) {
                    company.setCompanyTag(tag);
                    companyUserMapper.update(company);
                    log.info("更新企业标签: {} -> {}", company.getCompanyName(), tag);
                }
            }
        } catch (Exception e) {
            log.error("批量更新企业标签失败: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public void updateCompanyTag(Long companyId) {
        try {
            CompanyUser company = companyUserMapper.findById(companyId);
            if (company != null) {
                String tag = determineCompanyTags(company.getRegisterTime(), company.getAcceptBackup(), company.getIsInternshipBase(), company.getCooperationMode());
                if (tag != null && !tag.equals(company.getCompanyTag())) {
                    company.setCompanyTag(tag);
                    companyUserMapper.update(company);
                    log.info("更新企业标签: {} -> {}", company.getCompanyName(), tag);
                }
            }
        } catch (Exception e) {
            log.error("更新企业标签失败: {}", e.getMessage(), e);
        }
    }
}
