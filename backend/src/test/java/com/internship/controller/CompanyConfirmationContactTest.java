package com.internship.controller;

import com.internship.entity.*;
import com.internship.entity.dto.InternshipContact;
import com.internship.mapper.CompanyUserMapper;
import com.internship.mapper.PositionMapper;
import com.internship.service.*;
import com.internship.websocket.AnnouncementWebSocketHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompanyConfirmationContactTest {
    private CompanyConfirmationController controller;
    private CompanyConfirmationMaterialsService materials;
    private CompanyUserMapper companies;
    private PositionMapper positions;

    @BeforeEach void setup() {
        UserService users = mock(UserService.class);
        materials = mock(CompanyConfirmationMaterialsService.class);
        companies = mock(CompanyUserMapper.class); positions = mock(PositionMapper.class);
        controller = new CompanyConfirmationController(users, mock(InternshipConfirmationRecordService.class),
                mock(InternshipConfirmationWorkflowService.class), mock(StudentJobApplicationService.class),
                positions, mock(AnnouncementWebSocketHandler.class));
        ReflectionTestUtils.setField(controller, "materials", materials);
        ReflectionTestUtils.setField(controller, "companyUserMapper", companies);
        User user = new User(); user.setId(20L);
        when(users.findByUsername("company")).thenReturn(user);
        var principal = new org.springframework.security.core.userdetails.User("company", "", List.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    private void record(Long positionId) {
        InternshipConfirmationRecord record = new InternshipConfirmationRecord();
        record.setId(10L); record.setCompanyId(20L); record.setPositionId(positionId);
        when(materials.owned(20L, 10L)).thenReturn(record);
        CompanyUser company = new CompanyUser(); company.setId(20L);
        company.setContactPerson("企业联系人"); company.setContactPhone("02088888888");
        when(companies.findById(20L)).thenReturn(company);
    }

    @Test void detailIncludesLinkedPositionContact() throws Exception {
        record(30L);
        Position position = new Position(); position.setCompanyId(20L);
        position.setContactPerson("岗位负责人"); position.setContactPhone("13800000000");
        when(positions.findById(30L)).thenReturn(position);

        Map<?, ?> data = (Map<?, ?>) controller.details(10L).getData();

        assertEquals(new InternshipContact("岗位负责人", "13800000000"), data.get("internshipContact"));
    }

    @Test void textPositionDetailIncludesCompanyBusinessContact() throws Exception {
        record(null);

        Map<?, ?> data = (Map<?, ?>) controller.details(10L).getData();

        assertEquals(new InternshipContact("企业联系人", "02088888888"), data.get("internshipContact"));
        verifyNoInteractions(positions);
    }

    @Test void ownershipIsCheckedBeforeReadingAnyContact() {
        when(materials.owned(20L, 10L)).thenThrow(new AccessDeniedException("无权读取"));

        assertThrows(AccessDeniedException.class, () -> controller.details(10L));

        verifyNoInteractions(positions, companies);
    }
}
