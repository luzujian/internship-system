package com.internship.service;

import com.internship.entity.CompanyAlumniMember;
import com.internship.entity.CompanyUser;
import com.internship.exception.BusinessException;
import com.internship.mapper.CompanyAlumniMemberMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class CompanyAlumniService {
    private final CompanyAlumniMemberMapper alumniMemberMapper;

    public CompanyAlumniService(CompanyAlumniMemberMapper alumniMemberMapper) {
        this.alumniMemberMapper = alumniMemberMapper;
    }

    public List<CompanyAlumniMember> findByCompanyId(Long companyId) {
        if (companyId == null) {
            return Collections.emptyList();
        }
        return alumniMemberMapper.findByCompanyId(companyId);
    }

    public List<CompanyAlumniMember> normalize(Integer isAlumniUnit,
                                                List<CompanyAlumniMember> members) {
        if (!Integer.valueOf(1).equals(isAlumniUnit)) {
            return new ArrayList<>();
        }
        if (members == null || members.isEmpty()) {
            throw new BusinessException("校友单位至少需要填写一名校友");
        }

        List<CompanyAlumniMember> normalized = new ArrayList<>(members.size());
        for (CompanyAlumniMember source : members) {
            if (source == null || !StringUtils.hasText(source.getName())) {
                throw new BusinessException("校友姓名不能为空");
            }
            if (!StringUtils.hasText(source.getPhone())) {
                throw new BusinessException("校友联系电话不能为空");
            }
            if (!StringUtils.hasText(source.getPosition())) {
                throw new BusinessException("校友任职不能为空");
            }
            String name = source.getName().trim();
            String phone = source.getPhone().trim();
            String position = source.getPosition().trim();
            if (name.length() > 50 || phone.length() > 20 || position.length() > 100) {
                throw new BusinessException("校友信息长度超出限制");
            }

            CompanyAlumniMember member = new CompanyAlumniMember();
            member.setName(name);
            member.setPhone(phone);
            member.setPosition(position);
            normalized.add(member);
        }
        return normalized;
    }

    public void syncLegacyRepresentative(CompanyUser company,
                                         List<CompanyAlumniMember> normalizedMembers) {
        if (!Integer.valueOf(1).equals(company.getIsAlumniUnit()) || normalizedMembers.isEmpty()) {
            company.setIsAlumniUnit(0);
            company.setAlumniRepresentativeName(null);
            company.setAlumniRepresentativePhone(null);
            company.setAlumniRepresentativePosition(null);
            return;
        }
        CompanyAlumniMember first = normalizedMembers.get(0);
        company.setAlumniRepresentativeName(first.getName());
        company.setAlumniRepresentativePhone(first.getPhone());
        company.setAlumniRepresentativePosition(first.getPosition());
    }

    @Transactional(rollbackFor = Exception.class)
    public void replace(Long companyId, List<CompanyAlumniMember> normalizedMembers) {
        alumniMemberMapper.deleteByCompanyId(companyId);
        if (normalizedMembers == null || normalizedMembers.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (CompanyAlumniMember member : normalizedMembers) {
            member.setId(null);
            member.setCompanyId(companyId);
            member.setCreateTime(now);
            member.setUpdateTime(now);
        }
        alumniMemberMapper.batchInsert(normalizedMembers);
    }
}
