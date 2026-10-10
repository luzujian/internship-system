package com.internship.service.impl;

import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CompanyTagServiceImplFreePlacementTest {
    private final CompanyTagServiceImpl service = new CompanyTagServiceImpl();

    @Test
    void approvalControlsLabelWithoutChangingHistoricalMode() {
        var mapper = org.mockito.Mockito.mock(com.internship.mapper.CompanyUserMapper.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "companyUserMapper", mapper);
        var company = new com.internship.entity.CompanyUser();
        company.setId(1L); company.setCooperationMode("mutual_choice");
        company.setCompanyTag("校企双向选择,其他标签"); company.setAuditStatus(0);
        org.mockito.Mockito.when(mapper.findById(1L)).thenReturn(company);
        service.updateCompanyTag(1L);
        assertEquals("其他标签", company.getCompanyTag());
        company.setAuditStatus(1);
        service.updateCompanyTag(1L);
        assertEquals("校企双向选择,其他标签", company.getCompanyTag());
        company.setAuditStatus(2);
        service.updateCompanyTag(1L);
        assertEquals("其他标签", company.getCompanyTag());
        company.setCooperationMode("student_contact");
        service.updateCompanyTag(1L);
        assertEquals("student_contact", company.getCooperationMode());
        assertEquals("其他标签", company.getCompanyTag());
        company.setCompanyTag("学生自主联系,其他标签");
        company.setAuditStatus(1);
        service.updateCompanyTag(1L);
        assertEquals("校企双向选择,其他标签", company.getCompanyTag());
        company.setCooperationMode(null);
        service.updateCompanyTag(1L);
        assertEquals("校企双向选择,其他标签", company.getCompanyTag());
    }

    @Test
    void companyTagsNoLongerDependOnRegistrationWindow() {
        assertNull(service.determineCompanyTags(new Date(), 0, null, null));
        assertEquals("校企双向选择",
                service.determineCompanyTags(new Date(), 0, null, "mutual_choice"));
        assertEquals("接受兜底,校企双向选择",
                service.determineCompanyTags(new Date(), 1, null, "mutual_choice"));
    }
}
