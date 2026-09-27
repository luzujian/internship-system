package com.internship.service.impl;

import com.internship.entity.CompanyUser;
import com.internship.mapper.CompanyUserMapper;
import com.internship.service.CompanyAlumniService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompanyUserServiceImplTagNormalizationTest {
    @Test
    void approvalAppliesCurrentAdmissionPolicyToOldPendingApplications() {
        CompanyUserMapper mapper = mock(CompanyUserMapper.class);
        CompanyUser company = new CompanyUser();
        company.setId(1L); company.setCooperationMode("student_contact"); company.setAuditStatus(0);
        when(mapper.findById(1L)).thenReturn(company);
        when(mapper.update(company)).thenReturn(1);
        CompanyUserServiceImpl service = new CompanyUserServiceImpl(
                mapper, mock(PasswordEncoder.class), mock(CompanyAlumniService.class));
        assertEquals(1,service.approveQualification(1L,2L));
        assertEquals("mutual_choice",company.getCooperationMode());
        assertEquals(1,company.getAuditStatus());
    }
    @Test
    void legacyStoredTagIsNormalizedInCompanyAndOptionOutputs() {
        CompanyUserMapper mapper = mock(CompanyUserMapper.class);
        CompanyUser company = new CompanyUser();
        company.setCompanyTag("双向选择阶段,接受兜底");
        when(mapper.findByUsername("company")).thenReturn(company);
        when(mapper.getDistinctTags()).thenReturn(List.of(
                "双向选择阶段,接受兜底", "校企双向选择"));
        CompanyUserServiceImpl service = new CompanyUserServiceImpl(
                mapper, mock(PasswordEncoder.class), mock(CompanyAlumniService.class));

        assertEquals("校企双向选择,接受兜底",
                service.findByUsername("company").getCompanyTag());
        List<String> tags = service.getDistinctTags();
        assertEquals(1, tags.stream().filter("校企双向选择"::equals).count());
        assertFalse(tags.contains("双向选择阶段"));
    }
}
