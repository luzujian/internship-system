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
        if (acceptBackup != null && acceptBackup == 1) {
            return "接受兜底";
        }
        
        if (registerTime == null) {
            return null;
        }
        
        try {
            LocalDate registerDate = registerTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            
            LocalDate startDate = getDualSelectionStartDate();
            LocalDate endDate = getDualSelectionEndDate();
            
            if (startDate != null && endDate != null) {
                if (!registerDate.isBefore(startDate) && !registerDate.isAfter(endDate)) {
                    return "双向选择阶段";
                } else if (registerDate.isAfter(endDate)) {
                    return "学生自主联系";
                }
            }
        } catch (Exception e) {
            log.error("判断企业标签失败: {}", e.getMessage(), e);
        }
        
        return null;
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
                String tag = determineCompanyTag(company.getRegisterTime(), company.getAcceptBackup());
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
                String tag = determineCompanyTag(company.getRegisterTime(), company.getAcceptBackup());
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
