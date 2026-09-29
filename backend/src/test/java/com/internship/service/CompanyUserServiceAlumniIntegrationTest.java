package com.internship.service;

import com.internship.entity.CompanyAlumniMember;
import com.internship.entity.CompanyUser;
import com.internship.mapper.CompanyUserMapper;
import com.internship.service.impl.CompanyUserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyUserServiceAlumniIntegrationTest {
    @Mock
    private CompanyUserMapper companyUserMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private CompanyAlumniService companyAlumniService;

    private CompanyUserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CompanyUserServiceImpl(
                companyUserMapper, passwordEncoder, companyAlumniService);
    }

    @Test
    void registrationPersistsCompanyAndAlumniAsOneServiceOperation() {
        CompanyUser company = validCompany();
        CompanyAlumniMember member = new CompanyAlumniMember();
        member.setName("张三");
        member.setPhone("13800000000");
        member.setPosition("经理");
        company.setIsAlumniUnit(1);
        company.setAlumniMembers(List.of(member));

        when(companyAlumniService.normalize(1, company.getAlumniMembers()))
                .thenReturn(List.of(member));
        when(passwordEncoder.encode("123456")).thenReturn("encoded");
        doAnswer(invocation -> {
            ((CompanyUser) invocation.getArgument(0)).setId(88L);
            return 1;
        }).when(companyUserMapper).insert(any(CompanyUser.class));

        int result = service.insert(company);

        assertEquals(1, result);
        assertEquals("encoded", company.getPassword());
        verify(companyAlumniService).replace(88L, List.of(member));
    }

    @Test
    void companyDetailIncludesAllAlumniMembers() {
        CompanyUser company = validCompany();
        company.setId(88L);
        CompanyAlumniMember first = new CompanyAlumniMember();
        CompanyAlumniMember second = new CompanyAlumniMember();
        when(companyUserMapper.findById(88L)).thenReturn(company);
        when(companyAlumniService.findByCompanyId(88L)).thenReturn(List.of(first, second));

        CompanyUser result = service.findById(88L);

        assertEquals(2, result.getAlumniMembers().size());
    }

    @Test
    void partialAlumniFlagUpdateReusesStoredMembersBeforeValidation() {
        CompanyUser existing = validCompany();
        existing.setId(88L);
        existing.setIsAlumniUnit(1);
        CompanyAlumniMember member = new CompanyAlumniMember();
        member.setName("张三");
        member.setPhone("13800000000");
        member.setPosition("经理");
        CompanyUser patch = new CompanyUser();
        patch.setId(88L);
        patch.setIsAlumniUnit(1);

        when(companyUserMapper.findById(88L)).thenReturn(existing);
        when(companyAlumniService.findByCompanyId(88L)).thenReturn(List.of(member));
        when(companyAlumniService.normalize(1, List.of(member))).thenReturn(List.of(member));
        when(companyUserMapper.update(existing)).thenReturn(1);

        service.update(patch);

        verify(companyAlumniService).replace(88L, List.of(member));
    }

    private CompanyUser validCompany() {
        CompanyUser company = new CompanyUser();
        company.setCompanyName("测试企业");
        company.setContactPerson("联系人");
        company.setContactPhone("13800000000");
        company.setContactEmail("contact@example.com");
        company.setAddress("测试企业地址");
        company.setPassword("123456");
        return company;
    }
}
