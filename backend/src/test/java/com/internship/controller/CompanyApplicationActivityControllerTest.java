package com.internship.controller;

import com.internship.entity.User;
import com.internship.service.CompanyApplicationActivityService;
import com.internship.utils.CurrentHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.internship.service.StudentJobApplicationService;
import com.internship.service.StudentInternshipStatusService;
import com.internship.websocket.AnnouncementWebSocketHandler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompanyApplicationActivityControllerTest {
    private final CompanyApplicationActivityService service = mock(CompanyApplicationActivityService.class);
    private final CompanyApplicationActivityController controller = new CompanyApplicationActivityController(service);
    @AfterEach void clearIdentity() {
        CurrentHolder.remove();
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test void companyRefreshIsSentOnlyAfterTheBusinessTransactionCommits() {
        var company = new CompanyController();
        var jobs = mock(StudentJobApplicationService.class);
        var statuses = mock(StudentInternshipStatusService.class);
        var socket = mock(AnnouncementWebSocketHandler.class);
        ReflectionTestUtils.setField(company, "studentJobApplicationService", jobs);
        ReflectionTestUtils.setField(company, "studentInternshipStatusService", statuses);
        ReflectionTestUtils.setField(company, "webSocketHandler", socket);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        ReflectionTestUtils.invokeMethod(company, "pushTodoUpdate", 7L);
        verifyNoInteractions(jobs, statuses, socket);
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
        verify(socket).sendCompanyTodoUpdate(7L, 0L, 0L);
    }

    @Test void rollbackDoesNotPublishAnActivityRefresh() {
        var company = new CompanyController();
        var socket = mock(AnnouncementWebSocketHandler.class);
        ReflectionTestUtils.setField(company, "webSocketHandler", socket);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        ReflectionTestUtils.invokeMethod(company, "pushTodoUpdate", 7L);
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(1));
        verifyNoInteractions(socket);
    }

    @Test void rejectsNonCompanyEvenWhenUserIdMatches() {
        User user = new User(); user.setId(7L); user.setRole("ROLE_STUDENT"); CurrentHolder.setUser(user);
        assertThrows(AccessDeniedException.class, () -> controller.list(0, 10, false));
        assertThrows(AccessDeniedException.class, () -> controller.detail(31L));
        assertThrows(AccessDeniedException.class, () -> controller.forApplication(91L));
        assertThrows(AccessDeniedException.class, () -> controller.read(31L,
                new CompanyApplicationActivityController.ReadVersion("pending", 1000L)));
        verifyNoInteractions(service);
    }
    @Test void usesAuthenticatedCompanyForEveryEndpoint() {
        User user = new User(); user.setId(7L); user.setRole("ROLE_COMPANY"); CurrentHolder.setUser(user);
        controller.list(10, 10, true); controller.detail(31L); controller.forApplication(91L);
        controller.read(31L, new CompanyApplicationActivityController.ReadVersion("pending", 1000L));
        verify(service).list(7L, 10, 10, true); verify(service).detail(7L, 31L);
        verify(service).forApplication(7L, 91L); verify(service).read(7L, 31L, "pending", 1000L);
    }
}
