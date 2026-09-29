package com.internship.service.impl;

import com.internship.auth.config.CustomPasswordEncoder;
import com.internship.entity.CompanyUser;
import com.internship.entity.User;
import com.internship.mapper.CompanyUserMapper;
import com.internship.service.AdminUserService;
import com.internship.service.StudentUserService;
import com.internship.service.TeacherUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CompanyPasswordEncodingTest {
    private UserServiceImpl service;
    private CompanyUserMapper mapper;
    private CustomPasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl();
        mapper = mock(CompanyUserMapper.class);
        encoder = new CustomPasswordEncoder();
        ReflectionTestUtils.setField(service, "passwordEncoder", encoder);
        ReflectionTestUtils.setField(service, "adminUserService", mock(AdminUserService.class));
        ReflectionTestUtils.setField(service, "teacherUserService", mock(TeacherUserService.class));
        ReflectionTestUtils.setField(service, "studentUserService", mock(StudentUserService.class));
        ReflectionTestUtils.setField(service, "companyUserMapper", mapper);
        when(mapper.update(any())).thenReturn(1);
    }

    @Test
    void commonPasswordResetNeverWritesPlaintextCompanyPassword() {
        CompanyUser company = company("company", encoder.encode("old-password"));
        when(mapper.findByUsername("company")).thenReturn(company);

        assertEquals(1, service.updatePassword("company", "New-Password123!"));

        assertNotEquals("New-Password123!", company.getPassword());
        assertTrue(encoder.matches("New-Password123!", company.getPassword()));
    }

    @Test
    void genericUpdateEncodesNewCompanyPassword() {
        CompanyUser company = company("company", encoder.encode("old-password"));
        when(mapper.findById(7L)).thenReturn(company);
        User input = new User();
        input.setId(7L);
        input.setUsername("company");
        input.setRole("ROLE_COMPANY");
        input.setPassword("New-Password123!");

        assertEquals(1, service.update(input));

        assertNotEquals("New-Password123!", company.getPassword());
        assertTrue(encoder.matches("New-Password123!", company.getPassword()));
    }

    @Test
    void genericUpdateDoesNotRehashUnchangedCompanyHash() {
        String storedHash = encoder.encode("old-password");
        CompanyUser company = company("company", storedHash);
        when(mapper.findById(7L)).thenReturn(company);
        User input = new User();
        input.setId(7L);
        input.setUsername("company");
        input.setRole("ROLE_COMPANY");
        input.setPassword(storedHash);

        assertEquals(1, service.update(input));

        assertNull(company.getPassword(), "mapper should skip the password column");
    }

    private CompanyUser company(String username, String password) {
        CompanyUser company = new CompanyUser();
        company.setId(7L);
        company.setUsername(username);
        company.setPassword(password);
        return company;
    }
}
