package com.internship.controller;

import com.internship.entity.CompanyUser;
import com.internship.mapper.CompanyUserMapper;
import com.internship.service.*;
import com.internship.service.impl.CompanyUserServiceImpl;
import com.internship.utils.CurrentHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CompanyAccountContactTest {
    private CompanyController controller;
    private CompanyUserService users;
    private CompanyUser company;

    @BeforeEach
    void setup() {
        com.internship.entity.User actor = new com.internship.entity.User();
        actor.setId(71L); actor.setRole("ROLE_COMPANY"); CurrentHolder.setUser(actor);
        users = mock(CompanyUserService.class);
        controller = new CompanyController();
        ReflectionTestUtils.setField(controller, "companyUserService", users);
        company = new CompanyUser(); company.setId(71L); company.setUsername("original-login");
        company.setPhone("13800000001"); company.setEmail("owner@example.com");
        company.setContactPerson("业务联系人"); company.setContactPhone("13800000002"); company.setContactEmail("business@example.com");
        company.setPassword("original-hash"); company.setAuditStatus(1);
        when(users.findById(71L)).thenReturn(company);
    }

    @AfterEach void cleanup() { CurrentHolder.remove(); }

    @Test
    void existingAccountBindingsRemainDistinctFromLaterBusinessContacts() {
        Map<?, ?> result = (Map<?, ?>) controller.getProfile().getData();
        assertEquals("13800000001", result.get("phone"));
        assertEquals("owner@example.com", result.get("email"));
        verify(users, never()).updateProfile(any());
    }

    @Test
    void missingLegacyAccountPhoneUsesContactPhoneForDisplayOnly() {
        company.setPhone(null);
        Map<?, ?> result = (Map<?, ?>) controller.getProfile().getData();
        assertEquals("13800000002", result.get("phone"));
        assertNull(company.getPhone());
        assertEquals("owner@example.com", result.get("email"));
    }

    @Test
    void bindingsSaveDirectlyWithoutPasswordAndCannotRenameLoginAccount() {
        when(users.updateProfile(any())).thenReturn(1);
        assertEquals(200, controller.updateProfile(Map.of("phone", "020 88888888", "email", "new@example.com", "username", "changed-login")).getCode());
        var saved = ArgumentCaptor.forClass(CompanyUser.class);
        verify(users).updateProfile(saved.capture());
        assertEquals(71L, saved.getValue().getId());
        assertEquals("02088888888", saved.getValue().getPhone());
        assertEquals("new@example.com", saved.getValue().getEmail());
        assertNull(saved.getValue().getUsername());
        assertNull(saved.getValue().getPassword());
        assertNull(saved.getValue().getContactPhone());
        assertNull(saved.getValue().getContactEmail());
        assertEquals("original-login", company.getUsername());
        assertEquals("original-hash", company.getPassword());
    }

    @Test
    void invalidPhoneAndMissingRecoveryEmailDoNotWriteAnything() {
        assertNotEquals(200, controller.updateProfile(Map.of("phone", "1", "email", "owner@example.com")).getCode());
        assertNotEquals(200, controller.updateProfile(Map.of("email", "")).getCode());
        assertNotEquals(200, controller.updateProfile(Map.of("email", "not-an-email")).getCode());
        verify(users, never()).updateProfile(any());
    }

    @Test
    void profilePersistenceUsesOnlyTheBindingUpdate() {
        CompanyUserMapper mapper = mock(CompanyUserMapper.class);
        when(mapper.findById(71L)).thenReturn(company);
        when(mapper.updateAccountContact(any())).thenReturn(1);
        var service = new CompanyUserServiceImpl(mapper, mock(org.springframework.security.crypto.password.PasswordEncoder.class), mock(CompanyAlumniService.class));
        CompanyUser changes = new CompanyUser(); changes.setId(71L); changes.setEmail("new@example.com"); changes.setPhone("13800000003");
        assertEquals(1, service.updateProfile(changes));
        verify(mapper).updateAccountContact(changes);
        verify(mapper, never()).update(any());
    }
}
