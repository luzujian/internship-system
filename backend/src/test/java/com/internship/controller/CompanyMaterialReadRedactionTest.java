package com.internship.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.entity.CompanyUser;
import com.internship.entity.PageResult;
import com.internship.service.CompanyRelationsService;
import com.internship.service.CompanyUserService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompanyMaterialReadRedactionTest {
    @Test
    void adminListAndDetailKeepAttachmentPresenceWithoutSendingPublicOssUrl() {
        CompanyUserController controller = new CompanyUserController();
        CompanyUserService service = mock(CompanyUserService.class);
        ReflectionTestUtils.setField(controller, "companyUserService", service);
        ReflectionTestUtils.setField(controller, "companyRelationsService", mock(CompanyRelationsService.class));
        ReflectionTestUtils.setField(controller, "objectMapper", new ObjectMapper());
        CompanyUser company = new CompanyUser();
        company.setId(15L);
        company.setBusinessLicense("https://public.example/images/2026/09/file.png");
        when(service.findById(15L)).thenReturn(company);
        when(service.findPage(1, 10, null, null, null, null))
                .thenReturn(PageResult.build(1L, List.of(company)));

        @SuppressWarnings("unchecked")
        Map<String, Object> detail = (Map<String, Object>) controller.getCompanyById(15L).getData();
        assertEquals("uploaded.png", detail.get("businessLicense"));
        assertEquals("https://public.example/images/2026/09/file.png", company.getBusinessLicense());

        @SuppressWarnings("unchecked")
        PageResult<CompanyUser> page = (PageResult<CompanyUser>) controller
                .getAllCompanies(1, 10, null, null, null, null).getData();
        assertEquals("uploaded.png", page.getRows().get(0).getBusinessLicense());
    }

    @Test
    void recallPagesRedactQualificationUrlsUnderTheirExistingPermissions() throws Exception {
        CompanyUserController controller = new CompanyUserController();
        CompanyUserService service = mock(CompanyUserService.class);
        ReflectionTestUtils.setField(controller, "companyUserService", service);
        CompanyUser company = new CompanyUser();
        company.setId(15L);
        company.setBusinessLicense("https://private.example/images/2026/09/license.pdf");
        company.setLegalIdCard("https://private.example/images/2026/09/front.jpg,https://private.example/images/2026/09/back.jpg");
        company.setPlaquePhoto("https://private.example/images/2026/09/plaque.png");
        PageResult<CompanyUser> storedPage = PageResult.build(1L, List.of(company), 1, 1, 10);
        when(service.findPendingRecallAuditPage(1, 10, null, null, null)).thenReturn(storedPage);
        when(service.findRecallRecordsPage(1, 10, null, null, null, null)).thenReturn(storedPage);

        assertEquals("hasRole('ADMIN')", CompanyUserController.class
                .getMethod("getPendingRecallAuditCompanies", Integer.class, Integer.class,
                        String.class, String.class, String.class, Long.class)
                .getAnnotation(PreAuthorize.class).value());
        assertEquals("hasAuthority('application:withdrawal:view')", CompanyUserController.class
                .getMethod("getRecallRecords", Integer.class, Integer.class,
                        String.class, String.class, String.class, String.class)
                .getAnnotation(PreAuthorize.class).value());

        @SuppressWarnings("unchecked")
        PageResult<CompanyUser> pending = (PageResult<CompanyUser>) controller
                .getPendingRecallAuditCompanies(1, 10, null, null, null, null).getData();
        @SuppressWarnings("unchecked")
        PageResult<CompanyUser> history = (PageResult<CompanyUser>) controller
                .getRecallRecords(1, 10, null, null, null, null).getData();
        for (PageResult<CompanyUser> page : List.of(pending, history)) {
            assertEquals(1L, page.getTotal());
            assertEquals(1, page.getCurrent());
            CompanyUser visible = page.getRows().get(0);
            assertEquals("uploaded.pdf", visible.getBusinessLicense());
            assertEquals("uploaded.jpg,uploaded.jpg", visible.getLegalIdCard());
            assertEquals("uploaded.png", visible.getPlaquePhoto());
            assertFalse(new ObjectMapper().writeValueAsString(page).contains("private.example"));
        }
        assertEquals("https://private.example/images/2026/09/license.pdf", company.getBusinessLicense());
    }
}
