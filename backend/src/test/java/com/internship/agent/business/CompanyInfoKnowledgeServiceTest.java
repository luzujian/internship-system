package com.internship.agent.business;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.entity.CompanyUser;
import com.internship.service.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class CompanyInfoKnowledgeServiceTest {
    @Test void approvalAndStoredModeDetermineCooperationLabel() {
        var companies=mock(CompanyUserService.class);var liaisons=mock(CompanySchoolLiaisonService.class);
        var json=new ObjectMapper();var service=new CompanyInfoKnowledgeService(json,companies,liaisons);
        var company=new CompanyUser();company.setId(3L);company.setCooperationMode("mutual_choice");
        when(companies.findById(3L)).thenReturn(company);
        assertEquals(0L,service.read(3L).get("activeTagCount"));
        company.setAuditStatus(1);
        assertEquals(1L,service.read(3L).get("activeTagCount"));
        company.setCooperationMode(null);
        assertEquals(0L,service.read(3L).get("activeTagCount"));
    }
    @Test void readsCurrentValuesWithSharedDefinitionsAndExcludesAccountSecrets() throws Exception {
        var companies=mock(CompanyUserService.class);var liaisons=mock(CompanySchoolLiaisonService.class);
        var json=new ObjectMapper();var service=new CompanyInfoKnowledgeService(json,companies,liaisons);
        var company=new CompanyUser();company.setId(3L);company.setCompanyName("更新后的企业");company.setPassword("secret");
        company.setContactEmail("contact@example.com");company.setAcceptBackup(1);company.setCooperationMode("student_contact");
        when(companies.findById(3L)).thenReturn(company);when(liaisons.findByCompanyId(3L)).thenReturn(List.of());
        var result=json.valueToTree(service.read(3L));
        assertTrue(result.toString().contains("contact@example.com"));assertFalse(result.toString().contains("secret"));
        assertTrue(result.path("tags").findValues("active").stream().anyMatch(node->node.asBoolean()));
        assertEquals(2,result.path("activeTagCount").asInt());
        assertEquals(result.path("tags").size()-2,result.path("inactiveTagCount").asInt());
        company.setContactEmail("new@example.com");assertTrue(json.writeValueAsString(service.read(3L)).contains("new@example.com"));
        verify(companies,times(2)).findById(3L);
    }
}
