package com.internship.service;

import com.internship.entity.CompanyUser;
import com.internship.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PasswordResetCompanyAuditStatusTest {
    @Test
    void pendingAndRejectedCompanyCannotResetApplicationCredentialByEmail() {
        var service = new PasswordResetService();
        var companies = mock(CompanyUserService.class);
        var mail = mock(MailService.class);
        ReflectionTestUtils.setField(service, "companyUserService", companies);
        ReflectionTestUtils.setField(service, "adminUserService", mock(AdminUserService.class));
        ReflectionTestUtils.setField(service, "teacherUserService", mock(TeacherUserService.class));
        ReflectionTestUtils.setField(service, "studentUserService", mock(StudentUserService.class));
        ReflectionTestUtils.setField(service, "mailService", mail);

        CompanyUser company = new CompanyUser();
        company.setUsername("测试企业");
        company.setEmail("registered@example.com");
        company.setStatus(1);
        when(companies.findByUsername("测试企业")).thenReturn(company);

        company.setAuditStatus(0);
        assertNull(service.resolveAccountRole("测试企业", "registered@example.com"));
        assertEquals(PasswordResetService.UNIFIED_AUTH_FAIL_MSG,
                assertThrows(BusinessException.class,
                        () -> service.sendResetCode("测试企业", "registered@example.com", "127.0.0.1")).getMessage());
        company.setAuditStatus(2);
        assertNull(service.resolveAccountRole("测试企业", "registered@example.com"));
        company.setAuditStatus(1);
        assertEquals("COMPANY", service.resolveAccountRole("测试企业", "registered@example.com"));
        verifyNoInteractions(mail);
    }
}
