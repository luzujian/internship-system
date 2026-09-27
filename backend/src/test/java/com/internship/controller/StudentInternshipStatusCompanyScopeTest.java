package com.internship.controller;

import com.internship.entity.User;
import com.internship.service.StudentInternshipStatusService;
import com.internship.utils.CurrentHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class StudentInternshipStatusCompanyScopeTest {
    @AfterEach
    void clearIdentity() {
        CurrentHolder.remove();
        SecurityContextHolder.clearContext();
    }

    @Test
    void companyCannotReadAnotherCompanyStatusesAndServiceIsNotQueried() {
        StudentInternshipStatusController controller = new StudentInternshipStatusController();
        StudentInternshipStatusService statuses = mock(StudentInternshipStatusService.class);
        ReflectionTestUtils.setField(controller, "studentInternshipStatusService", statuses);

        User actor = new User();
        actor.setId(1L);
        actor.setRole("ROLE_COMPANY");
        CurrentHolder.setUser(actor);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "preprod_company_0001", null, List.of(new SimpleGrantedAuthority("ROLE_COMPANY"))));

        assertThrows(AccessDeniedException.class, () -> controller.getInternshipStatusByCompanyId(2L));
        verifyNoInteractions(statuses);
    }
}
