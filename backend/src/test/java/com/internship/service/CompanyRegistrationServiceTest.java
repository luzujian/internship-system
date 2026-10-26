package com.internship.service;

import com.internship.dto.CompanyRegisterDTO;
import com.internship.entity.CompanyContact;
import com.internship.entity.CompanyUser;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CompanyRegistrationServiceTest {
    @Test
    void oneContactPairBindsAccountAndCreatesInitialRecruitmentContact() {
        CompanyUserService users = mock(CompanyUserService.class);
        CompanyContactService contacts = mock(CompanyContactService.class);
        CompanyUser company = new CompanyUser();
        company.setContactPerson(" 负责人 ");
        company.setContactPhone("020 88888888");
        company.setContactEmail("owner@example.com ");
        company.setPhone("13800000000");
        company.setEmail("unused@example.com");
        when(users.insert(company)).thenAnswer(invocation -> { company.setId(71L); return 1; });

        assertEquals(1, new CompanyRegistrationService(users, contacts).register(company));
        assertEquals("02088888888", company.getPhone());
        assertEquals("owner@example.com", company.getEmail());
        var contact = ArgumentCaptor.forClass(CompanyContact.class);
        var order = inOrder(users, contacts);
        order.verify(users).insert(company);
        order.verify(contacts).save(eq(71L), isNull(), contact.capture());
        assertEquals("负责人", contact.getValue().getName());
        assertEquals(company.getPhone(), contact.getValue().getPhone());
        assertEquals(company.getEmail(), contact.getValue().getEmail());
    }

    @Test
    void failedCompanyInsertDoesNotCreateOrphanContact() {
        CompanyUserService users = mock(CompanyUserService.class);
        CompanyContactService contacts = mock(CompanyContactService.class);
        CompanyUser company = new CompanyUser();
        company.setContactPerson("负责人"); company.setContactPhone("13800000000"); company.setContactEmail("owner@example.com");
        assertEquals(0, new CompanyRegistrationService(users, contacts).register(company));
        verifyNoInteractions(contacts);
    }

    @Test
    void registrationAcceptsOneEmailAndFixedPhoneAndRejectsMissingEmail() {
        CompanyRegisterDTO dto = validRegistration();
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(dto).isEmpty());
            dto.setContactEmail("");
            assertTrue(validator.validate(dto).stream().anyMatch(v -> v.getMessage().equals("联系邮箱不能为空")));
            dto.setEmail("legacy@example.com");
            assertTrue(validator.validate(dto).isEmpty(), "旧客户端仅提供原邮箱仍可注册");
            dto.setContactPhone("1");
            assertFalse(validator.validate(dto).isEmpty());
        }
    }

    @Test
    void contactEmailIsTheCanonicalRegistrationEmail() {
        CompanyRegisterDTO dto = validRegistration();
        dto.setEmail("legacy@example.com");
        assertEquals("owner@example.com", dto.registrationEmail());
    }

    private CompanyRegisterDTO validRegistration() {
        CompanyRegisterDTO dto = new CompanyRegisterDTO();
        dto.setCompanyName("验收企业有限公司"); dto.setCreditCode("914400000000000000");
        dto.setIndustry("it"); dto.setScale("1-50"); dto.setProvince("广东省");
        dto.setDetailAddress("广州市测试路一号"); dto.setContactPerson("负责人");
        dto.setContactPhone("02088888888"); dto.setContactEmail("owner@example.com");
        return dto;
    }
}
