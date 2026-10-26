package com.internship.controller;

import com.internship.entity.CompanyUser;
import com.internship.entity.Result;
import com.internship.mapper.CompanyUserMapper;
import com.internship.service.CompanyTagService;
import com.internship.service.CompanyUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Records the expressly retained, high-risk company-name-only application flow. */
class CompanyApplicationAccessControllerTest {
    private CompanyRegisterController controller;
    private CompanyUserMapper mapper;
    private CompanyUserService companyService;
    private CompanyUser company;

    @BeforeEach
    void setUp() {
        controller = new CompanyRegisterController();
        mapper = mock(CompanyUserMapper.class);
        companyService = mock(CompanyUserService.class);
        ReflectionTestUtils.setField(controller, "companyUserMapper", mapper);
        ReflectionTestUtils.setField(controller, "companyUserService", companyService);
        ReflectionTestUtils.setField(controller, "companyTagService", mock(CompanyTagService.class));
        company = new CompanyUser();
        company.setId(7L);
        company.setUsername("测试企业");
        company.setCompanyName("测试企业");
        company.setEmail("original@example.com");
        company.setContactPhone("13800000000");
        company.setBusinessLicense("secret-file-url");
        company.setAuditStatus(0);
        company.setRecallStatus(0);
        when(mapper.findByUsername("测试企业")).thenReturn(company);
        when(mapper.findById(7L)).thenReturn(company);
        when(companyService.findById(7L)).thenReturn(company);
        when(companyService.update(any())).thenReturn(1);
    }

    @Test
    void companyNameOnlyStatusIncludesApplicationDetails() {
        Result result = controller.checkCompanyStatus("测试企业");
        assertEquals(200, result.getCode());
        Map<?, ?> data = (Map<?, ?>) result.getData();
        assertEquals(7L, data.get("id"));
        assertEquals("original@example.com", data.get("email"));
        assertEquals("secret-file-url", data.get("businessLicense"));
    }

    @Test
    void rejectedStatusReturnsTeacherRemarkForStatusDialog() {
        company.setAuditStatus(2);
        company.setAuditRemark("请补交完整营业执照");

        Result result = controller.checkCompanyStatus("测试企业");

        assertEquals(200, result.getCode());
        Map<?, ?> data = (Map<?, ?>) result.getData();
        assertEquals(2, data.get("status"));
        assertEquals("请补交完整营业执照", data.get("auditRemark"));
    }

    @Test
    void nameOnlyRecallIsImmediatelyAllowedThenAllowsEmailEditWithoutCode() {
        when(mapper.update(any())).thenReturn(1);

        Result recall = controller.recallApplication(Map.of(
                "companyName", "测试企业",
                "recallReason", "联系人姓名填写错误")).getBody();
        assertNotNull(recall);
        assertEquals(200, recall.getCode());
        assertEquals(2, company.getRecallStatus());
        assertEquals(3, company.getAuditStatus());
        assertEquals("自动允许撤回修改", company.getRecallAuditRemark());
        assertNotNull(company.getRecallAuditTime());
        assertNull(company.getRecallReviewerId());

        Result edit = controller.updateCompany(7L, Map.of(
                "email", "replacement@example.com",
                "contactPerson", "Replacement Contact")).getBody();
        assertNotNull(edit);
        assertEquals(200, edit.getCode());
        var changed = org.mockito.ArgumentCaptor.forClass(CompanyUser.class);
        verify(companyService).update(changed.capture());
        assertEquals("replacement@example.com", changed.getValue().getEmail());
        assertEquals("Replacement Contact", changed.getValue().getContactPerson());
        assertEquals(0, changed.getValue().getAuditStatus());
        assertEquals(0, changed.getValue().getRecallStatus());
        assertNull(changed.getValue().getPassword());
    }

    @Test
    void rejectedApplicationCanBeUpdatedWithoutManagementToken() {
        company.setAuditStatus(2);
        Result result = controller.updateCompany(7L, Map.of("email", "new@example.com")).getBody();
        assertNotNull(result);
        assertEquals(200, result.getCode());
        verify(companyService).update(any());
    }

    @Test
    void malformedPersonalEmailIsStillRejected() {
        company.setRecallStatus(2);
        assertEquals(HttpStatus.BAD_REQUEST, controller.updateCompany(7L,
                Map.of("email", "not-an-email")).getStatusCode());
        verify(companyService, never()).update(any());
    }

    @Test
    void recalledApplicationBindsTheSingleContactEmailAndPhone() {
        company.setRecallStatus(2);
        Result result = controller.updateCompany(7L, Map.of("contactPerson", "新联系人",
                "contactPhone", "02088888888", "contactEmail", "new@example.com")).getBody();
        assertNotNull(result);
        assertEquals(200, result.getCode());
        var changed = org.mockito.ArgumentCaptor.forClass(CompanyUser.class);
        verify(companyService).update(changed.capture());
        assertEquals("new@example.com", changed.getValue().getEmail());
        assertEquals("new@example.com", changed.getValue().getContactEmail());
        assertEquals("02088888888", changed.getValue().getPhone());
        assertEquals("02088888888", changed.getValue().getContactPhone());
        assertNull(changed.getValue().getPassword());
    }

    @Test
    void unusedCompanyEmailCodeHandlerIsNotExposed() {
        boolean mapped = Arrays.stream(CompanyRegisterController.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(PostMapping.class))
                .filter(mapping -> mapping != null)
                .flatMap(mapping -> Arrays.stream(mapping.value()))
                .anyMatch("/send-verify-code"::equals);
        assertFalse(mapped);
    }
}
