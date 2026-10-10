package com.internship.service.impl;

import com.internship.entity.CompanyContact;
import com.internship.entity.CompanyUser;
import com.internship.mapper.CompanyContactMapper;
import com.internship.mapper.CompanyUserMapper;
import com.internship.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompanyImportMergeTest {
    @Test void mergesTwoContactsAndKeepsAccountPasswordAndCompanyIdentity() {
        CompanyUserMapper companies = mock(CompanyUserMapper.class);
        CompanyContactMapper contacts = mock(CompanyContactMapper.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        CompanyUserServiceImpl service = new CompanyUserServiceImpl(companies, encoder, mock(CompanyAlumniService.class));
        ReflectionTestUtils.setField(service, "companyContactService", new CompanyContactService(contacts, mock(PositionCacheService.class)));
        CompanyUser existing = new CompanyUser(); existing.setId(77L); existing.setCompanyName("测试企业");
        existing.setUsername("original-account"); existing.setPassword("existing-password-hash"); existing.setContactPerson("旧联系人");
        existing.setContactPhone("13800000000"); existing.setContactEmail("old@example.test"); existing.setAddress("原详细地址");
        existing.setAuditStatus(1); existing.setIsAlumniUnit(1); existing.setRecallStatus(0);
        when(companies.findImportMatches("测试企业")).thenReturn(List.of(existing)); when(companies.updateImportedProfile(any())).thenReturn(1);
        when(contacts.lockCompany(77L)).thenReturn(77L);
        List<CompanyContact> saved = new ArrayList<>();
        when(contacts.findByCompanyId(77L)).thenAnswer(i -> new ArrayList<>(saved));
        when(contacts.insert(any())).thenAnswer(i -> { CompanyContact c = i.getArgument(0); c.setId(100L + saved.size()); saved.add(c); return 1; });
        Map<String, Object> one = Map.of("企业名称", "测试企业", "联系人", "张老师", "联系电话", "13800000001", "联系邮箱", "one@example.test", "企业简介", "新简介");
        Map<String, Object> two = Map.of("企业名称", "测试企业", "联系人", "王老师", "联系电话", "13800000002", "联系邮箱", "two@example.test");
        Map<String, Object> result = service.importFromExcel(List.of(one, one, two));
        assertEquals(1, result.get("successCount")); assertEquals(0, result.get("createdCount")); assertEquals(1, result.get("updatedCount"));
        assertEquals(2, saved.size()); assertEquals(77L, existing.getId()); assertEquals("original-account", existing.getUsername());
        assertEquals("existing-password-hash", existing.getPassword()); assertEquals("原详细地址", existing.getAddress());
        assertEquals("新简介", existing.getIntroduction()); assertEquals(1, existing.getIsAlumniUnit()); assertEquals(1, existing.getAuditStatus());
        assertEquals("mutual_choice", existing.getCooperationMode());
        assertEquals("张老师", existing.getContactPerson()); assertEquals(List.of(), result.get("initialCredentials"));
        verify(companies, never()).insert(any()); verifyNoInteractions(encoder);
    }
    @Test void newlyImportedCompanyReceivesTheCooperationModeBeforeInsert() {
        CompanyUserMapper companies = mock(CompanyUserMapper.class);
        CompanyContactMapper contacts = mock(CompanyContactMapper.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        CompanyUserServiceImpl service = new CompanyUserServiceImpl(companies, encoder, mock(CompanyAlumniService.class));
        ReflectionTestUtils.setField(service, "companyContactService", new CompanyContactService(contacts, mock(PositionCacheService.class)));
        when(companies.findImportMatches("新企业")).thenReturn(List.of());
        when(encoder.encode(anyString())).thenReturn("password-hash");
        when(companies.insert(any())).thenAnswer(invocation -> {
            CompanyUser company = invocation.getArgument(0);
            assertEquals("mutual_choice", company.getCooperationMode());
            company.setId(78L);
            return 1;
        });
        when(companies.updateImportedProfile(any())).thenReturn(1);
        when(contacts.insert(any())).thenReturn(1);
        var result = service.importFromExcel(List.of(Map.of("企业名称", "新企业", "联系人", "张老师", "联系电话", "13800000001", "企业地址", "测试地址")));
        assertEquals(1, result.get("createdCount"), result.toString());
        assertEquals(0, result.get("failCount"));
        verify(companies).insert(any());
    }
    @Test void ambiguousOrInvalidGroupDoesNotWriteAnyPartOfTheCompany() {
        CompanyUserMapper companies = mock(CompanyUserMapper.class);
        CompanyContactMapper contacts = mock(CompanyContactMapper.class);
        CompanyUserServiceImpl service = new CompanyUserServiceImpl(companies, mock(PasswordEncoder.class), mock(CompanyAlumniService.class));
        ReflectionTestUtils.setField(service, "companyContactService", new CompanyContactService(contacts, mock(PositionCacheService.class)));
        when(companies.findImportMatches("重复企业")).thenReturn(List.of(new CompanyUser(), new CompanyUser()));
        var result = service.importFromExcel(List.of(Map.of("企业名称", "重复企业", "联系人", "张老师", "联系电话", "13800000001")));
        assertEquals(1, result.get("failCount")); verify(companies, never()).update(any()); verify(companies, never()).insert(any());
        verifyNoInteractions(contacts);
    }
}
