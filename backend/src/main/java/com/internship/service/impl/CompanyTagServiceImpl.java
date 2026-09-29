package com.internship.service.impl;

import com.internship.entity.CompanyUser;
import com.internship.mapper.CompanyUserMapper;
import com.internship.service.CompanyTagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class CompanyTagServiceImpl implements CompanyTagService {

    /** 自动标签集合：重建时按字段重新生成；集合之外的标签视为手动标签，重建时保留 */
    private static final java.util.Set<String> AUTO_TAGS =
            java.util.Set.of("接受兜底", "校企双向选择", "学生自主联系", "实习基地");

    @Autowired
    private CompanyUserMapper companyUserMapper;

    @Override
    public String determineCompanyTag(Date registerTime, Integer acceptBackup) {
        return determineCompanyTags(registerTime, acceptBackup, null, null);
    }

    @Override
    public String determineCompanyTags(Date registerTime, Integer acceptBackup, Integer isInternshipBase, String cooperationMode) {
        return rebuildTags(null, acceptBackup, isInternshipBase, cooperationMode);
    }

    /** 按字段重建自动标签，并保留现有标签中的手动标签（不属于 AUTO_TAGS 的） */
    private String rebuildTags(String currentTag, Integer acceptBackup, Integer isInternshipBase, String cooperationMode) {
        java.util.List<String> tags = new java.util.ArrayList<>();

        if (acceptBackup != null && acceptBackup == 1) {
            tags.add("接受兜底");
        }

        if (isInternshipBase != null && isInternshipBase == 1) {
            tags.add("实习基地");
        }

        if (cooperationMode != null && !cooperationMode.trim().isEmpty()) {
            if ("mutual_choice".equals(cooperationMode)) {
                tags.add("校企双向选择");
            } else if ("student_contact".equals(cooperationMode)) {
                tags.add("学生自主联系");
            }
        }

        if (currentTag != null && !currentTag.isBlank()) {
            for (String t : currentTag.split(",")) {
                String trim = t.trim();
                // 保留手动标签；"校企双向选择"同时作为固定标签保留（管理员导入/贴上的不会因重建被冲掉）
                if (!trim.isEmpty() && (!AUTO_TAGS.contains(trim) || "校企双向选择".equals(trim))) {
                    if (!tags.contains(trim)) {
                        tags.add(trim);
                    }
                }
            }
        }

        return tags.isEmpty() ? null : String.join(",", tags);
    }
    
    @Override
    public void updateCompanyTags() {
        try {
            List<CompanyUser> companies = companyUserMapper.findAll();
            for (CompanyUser company : companies) {
                String tag = rebuildTags(company);
                String currentTag = company.getCompanyTag() == null || company.getCompanyTag().isBlank()
                        ? null : company.getCompanyTag();
                if (!Objects.equals(tag, currentTag)) {
                    company.setCompanyTag(tag == null ? "" : tag);
                    companyUserMapper.update(company);
                    log.info("更新企业标签: {} -> {}", company.getCompanyName(), tag);
                }
            }
        } catch (Exception e) {
            log.error("批量更新企业标签失败: {}", e.getMessage(), e);
        }
    }

    private String rebuildTags(CompanyUser company) {
        String tags = rebuildTags(company.getCompanyTag(), company.getAcceptBackup(),
                company.getIsInternshipBase(), company.getCooperationMode());
        if (tags == null || Integer.valueOf(1).equals(company.getAuditStatus())) return tags;
        String filtered = java.util.Arrays.stream(tags.split(","))
                .filter(tag -> !"校企双向选择".equals(tag))
                .collect(java.util.stream.Collectors.joining(","));
        return filtered.isEmpty() ? null : filtered;
    }

    @Override
    public void updateCompanyTag(Long companyId) {
        try {
            CompanyUser company = companyUserMapper.findById(companyId);
            if (company != null) {
                String tag = rebuildTags(company);
                String currentTag = company.getCompanyTag() == null || company.getCompanyTag().isBlank()
                        ? null : company.getCompanyTag();
                if (!Objects.equals(tag, currentTag)) {
                    company.setCompanyTag(tag == null ? "" : tag);
                    companyUserMapper.update(company);
                    log.info("更新企业标签: {} -> {}", company.getCompanyName(), tag);
                }
            }
        } catch (Exception e) {
            log.error("更新企业标签失败: {}", e.getMessage(), e);
        }
    }
}
