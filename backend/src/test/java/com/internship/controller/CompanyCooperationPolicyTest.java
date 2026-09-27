package com.internship.controller;

import com.internship.dto.CompanyRegisterDTO;
import com.internship.entity.CompanyUser;
import com.internship.entity.User;
import com.internship.mapper.CompanyUserMapper;
import com.internship.service.*;
import com.internship.utils.CurrentHolder;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class CompanyCooperationPolicyTest {
    @Test void registrationRequiresRecoveryEmailButNoVerificationCode() {
        var dto = new CompanyRegisterDTO();
        dto.setCompanyName("测试企业");
        dto.setCreditCode("91440000TEST000001");
        dto.setIndustry("it");
        dto.setScale("1-50");
        dto.setProvince("广东省");
        dto.setDetailAddress("测试街道一号");
        dto.setContactPerson("联系人");
        dto.setContactPhone("13800000000");
        dto.setEmail("recovery@example.com");
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(dto).isEmpty());
            dto.setEmail("invalid-email");
            assertFalse(validator.validate(dto).isEmpty());
        }
    }

    @Test void registrationAndResubmissionIgnoreClientCooperationMode() {
        var controller = new CompanyRegisterController();
        var mapper = mock(CompanyUserMapper.class);
        var service = mock(CompanyUserService.class);
        ReflectionTestUtils.setField(controller,"companyUserMapper",mapper);
        ReflectionTestUtils.setField(controller,"companyUserService",service);
        var registrationLimit = mock(CompanyRegistrationRateLimitService.class);
        when(registrationLimit.allow("测试企业", "test@example.com")).thenReturn(true);
        ReflectionTestUtils.setField(controller,"registrationRateLimitService",registrationLimit);
        ReflectionTestUtils.setField(controller,"companyTagService",mock(CompanyTagService.class));
        when(service.insert(any())).thenReturn(1);
        when(service.update(any())).thenReturn(1);
        var dto = new CompanyRegisterDTO();
        dto.setCompanyName("测试企业");dto.setEmail("test@example.com");
        dto.setCooperationMode("student_contact");
        var httpResponse = new MockHttpServletResponse();
        var registration = controller.register(dto, httpResponse);
        assertEquals(200, registration.getCode());
        assertEquals("no-store", httpResponse.getHeader("Cache-Control"));
        var inserted=org.mockito.ArgumentCaptor.forClass(CompanyUser.class);
        verify(service).insert(inserted.capture());
        assertEquals("mutual_choice",inserted.getValue().getCooperationMode());
        assertEquals(0,inserted.getValue().getAuditStatus());
        assertEquals("123456", inserted.getValue().getPassword());
        assertEquals("注册申请提交成功，请等待管理员审核", registration.getData());
        var existing = new CompanyUser();existing.setId(1L);existing.setEmail("old@example.com");existing.setRecallStatus(2);
        when(mapper.findById(1L)).thenReturn(existing);
        assertEquals(200,controller.updateCompany(1L,Map.of("email","test@example.com","cooperationMode","student_contact")).getBody().getCode());
        var updated=org.mockito.ArgumentCaptor.forClass(CompanyUser.class);
        verify(service).update(updated.capture());
        assertEquals("mutual_choice",updated.getValue().getCooperationMode());
        assertEquals(0,updated.getValue().getAuditStatus());
    }

    @Test void companyCannotChangeItsCooperationModeThroughProfile() {
        var controller=new CompanyController();var service=mock(CompanyUserService.class);
        ReflectionTestUtils.setField(controller,"companyUserService",service);
        ReflectionTestUtils.setField(controller,"companyTagService",mock(CompanyTagService.class));
        ReflectionTestUtils.setField(controller,"positionCacheService",mock(PositionCacheService.class));
        var company=new CompanyUser();company.setId(1L);company.setCooperationMode("mutual_choice");
        when(service.findById(1L)).thenReturn(company);when(service.update(any())).thenReturn(1);
        var user=new User();user.setId(1L);user.setRole("ROLE_COMPANY");CurrentHolder.setUser(user);
        try {
            assertEquals(200,controller.updateCompanyInfo(Map.of("cooperationMode","student_contact")).getCode());
            assertEquals("mutual_choice",company.getCooperationMode());
        } finally {CurrentHolder.remove();}
    }
}
