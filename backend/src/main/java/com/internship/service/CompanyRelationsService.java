package com.internship.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.internship.dto.TeacherCompanyDTO;
import com.internship.entity.CompanyAlumniMember;
import com.internship.entity.CompanySchoolLiaison;
import com.internship.entity.CompanyVisitSummary;
import com.internship.entity.PageResult;
import com.internship.entity.TeacherUser;
import com.internship.exception.BusinessException;
import com.internship.mapper.CompanyAlumniMemberMapper;
import com.internship.mapper.CompanySchoolLiaisonMapper;
import com.internship.mapper.CompanyUserMapper;
import com.internship.mapper.TeacherUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Year;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CompanyRelationsService {
    private final CompanyUserMapper companyUserMapper;
    private final CompanyAlumniMemberMapper alumniMemberMapper;
    private final CompanySchoolLiaisonMapper liaisonMapper;
    private final CompanyVisitService companyVisitService;
    private final TeacherUserMapper teacherUserMapper;

    public CompanyRelationsService(CompanyUserMapper companyUserMapper,
                                   CompanyAlumniMemberMapper alumniMemberMapper,
                                   CompanySchoolLiaisonMapper liaisonMapper,
                                   CompanyVisitService companyVisitService,
                                   TeacherUserMapper teacherUserMapper) {
        this.companyUserMapper = companyUserMapper;
        this.alumniMemberMapper = alumniMemberMapper;
        this.liaisonMapper = liaisonMapper;
        this.companyVisitService = companyVisitService;
        this.teacherUserMapper = teacherUserMapper;
    }

    public PageResult<TeacherCompanyDTO> findTeacherCompanies(Integer page,
                                                              Integer pageSize,
                                                              String companyName,
                                                              Integer status,
                                                              Integer auditStatus,
                                                              Integer recallStatus,
                                                              String companyTag,
                                                              String industry,
                                                              String keyword,
                                                              Integer visitedYear,
                                                              Integer visitState,
                                                              Long visitTeacherId,
                                                              Long liaisonTeacherId) {
        if (visitState != null && visitState != 0 && visitState != 1) {
            throw new BusinessException("走访状态必须为0或1");
        }
        int currentYear = Year.now().getValue();
        int selectedYear = visitedYear == null ? currentYear : visitedYear;
        List<String> companyTags = StringUtils.hasText(companyTag)
                ? Arrays.stream(companyTag.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .toList()
                : null;

        PageHelper.startPage(page, pageSize);
        List<TeacherCompanyDTO> companies = companyUserMapper.findTeacherCompanies(
                companyName, status, auditStatus, recallStatus, companyTags, industry, keyword,
                selectedYear, visitState, visitTeacherId, liaisonTeacherId);
        PageInfo<TeacherCompanyDTO> pageInfo = new PageInfo<>(companies);
        decorateCompanies(pageInfo.getList(), selectedYear, currentYear);
        return PageResult.build(pageInfo.getTotal(), pageInfo.getList(), pageInfo.getPages(),
                pageInfo.getPageNum(), pageInfo.getPageSize());
    }

    public TeacherCompanyDTO findCompanyDetail(Long companyId) {
        TeacherCompanyDTO company = companyUserMapper.findTeacherCompanyById(companyId);
        if (company == null) {
            return null;
        }
        int currentYear = Year.now().getValue();
        decorateCompanies(List.of(company), currentYear, currentYear);
        return company;
    }

    public List<TeacherUser> findTeacherOptions(String keyword) {
        return teacherUserMapper.findActiveOptions(keyword);
    }

    private void decorateCompanies(List<TeacherCompanyDTO> companies,
                                   Integer selectedYear,
                                   Integer currentYear) {
        if (companies == null || companies.isEmpty()) {
            return;
        }
        List<Long> companyIds = companies.stream().map(TeacherCompanyDTO::getId).toList();
        Map<Long, List<CompanyAlumniMember>> alumniByCompany = alumniMemberMapper
                .findByCompanyIds(companyIds).stream()
                .collect(Collectors.groupingBy(CompanyAlumniMember::getCompanyId));
        Map<Long, List<CompanySchoolLiaison>> liaisonsByCompany = liaisonMapper
                .findByCompanyIds(companyIds).stream()
                .collect(Collectors.groupingBy(CompanySchoolLiaison::getCompanyId));
        Map<Long, CompanyVisitSummary> summariesByCompany = companyVisitService
                .summarizeByCompanyIds(companyIds, selectedYear, currentYear).stream()
                .collect(Collectors.toMap(CompanyVisitSummary::getCompanyId, Function.identity()));

        for (TeacherCompanyDTO company : companies) {
            if (company.getCompanyTag() != null) {
                company.setCompanyTag(company.getCompanyTag()
                        .replace("双向选择阶段", "校企双向选择"));
            }
            company.setAlumniMembers(alumniByCompany.getOrDefault(
                    company.getId(), Collections.emptyList()));
            company.setSchoolLiaisons(liaisonsByCompany.getOrDefault(
                    company.getId(), Collections.emptyList()));
            company.setVisitSummary(summariesByCompany.getOrDefault(
                    company.getId(), emptyVisitSummary(company.getId(), selectedYear)));
        }
    }

    private CompanyVisitSummary emptyVisitSummary(Long companyId, Integer selectedYear) {
        CompanyVisitSummary summary = new CompanyVisitSummary();
        summary.setCompanyId(companyId);
        summary.setTotalVisitCount(0L);
        summary.setSelectedYear(selectedYear);
        summary.setSelectedYearVisitCount(0L);
        summary.setCurrentYearVisitCount(0L);
        summary.setVisitedInSelectedYear(false);
        summary.setVisitedThisYear(false);
        return summary;
    }
}
