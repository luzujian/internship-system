package com.internship.controller;

import com.internship.dto.TeacherCompanyDTO;
import com.internship.entity.PageResult;
import com.internship.service.CompanyRelationsService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeacherCompanyStatusControllerTest {
    @Test
    void pendingAuditAndNormalAccountAreReturnedAndFilteredSeparately() throws Exception {
        CompanyRelationsService service = mock(CompanyRelationsService.class);
        TeacherController controller = new TeacherController();
        ReflectionTestUtils.setField(controller, "companyRelationsService", service);
        TeacherCompanyDTO company = pendingCompany();
        when(service.findTeacherCompanies(1, 10, null, 1, 0, null, null, null,
                "普门", 2026, null, null, null)).thenReturn(PageResult.build(1L, List.of(company)));

        MockMvcBuilders.standaloneSetup(controller).build().perform(get("/api/teacher/companies")
                .param("page", "1").param("pageSize", "10").param("keyword", "普门")
                .param("auditStatus", "0").param("status", "1").param("visitedYear", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows[0].auditStatus").value(0))
                .andExpect(jsonPath("$.data.rows[0].status").value(1))
                .andExpect(jsonPath("$.data.rows[0].password").doesNotExist())
                .andExpect(jsonPath("$.data.rows[0].auditRemark").doesNotExist());

        verify(service).findTeacherCompanies(1, 10, null, 1, 0, null, null, null,
                "普门", 2026, null, null, null);
    }

    @Test
    void detailPreservesPendingAuditWhenAccountIsNormal() throws Exception {
        CompanyRelationsService service = mock(CompanyRelationsService.class);
        TeacherController controller = new TeacherController();
        ReflectionTestUtils.setField(controller, "companyRelationsService", service);
        when(service.findCompanyDetail(58L)).thenReturn(pendingCompany());

        MockMvcBuilders.standaloneSetup(controller).build().perform(get("/api/teacher/companies/58"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value(0))
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.businessLicense").doesNotExist())
                .andExpect(jsonPath("$.data.legalIdCard").doesNotExist());
    }

    private TeacherCompanyDTO pendingCompany() {
        TeacherCompanyDTO company = new TeacherCompanyDTO();
        company.setId(58L);
        company.setCompanyName("深圳普门科技股份有限公司");
        company.setAuditStatus(0);
        company.setStatus(1);
        return company;
    }
}
