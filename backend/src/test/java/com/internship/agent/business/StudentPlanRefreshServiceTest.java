package com.internship.agent.business;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StudentPlanRefreshServiceTest {
    @AfterEach void clearTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            for (var synchronization : TransactionSynchronizationManager.getSynchronizations()) synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test void waitsForCommitAndDeduplicatesStudentWithinTransaction() {
        var plans = mock(StudentPlanService.class);
        var service = service(plans);
        TransactionSynchronizationManager.initSynchronization();
        service.afterCommit(8L); service.afterCommit(8L);
        verifyNoInteractions(plans);
        assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
        for (var synchronization : TransactionSynchronizationManager.getSynchronizations()) synchronization.afterCommit();
        verify(plans, times(1)).refresh(argThat(actor -> actor.id().equals(8L) && "ROLE_STUDENT".equals(actor.role())));
    }

    @Test void rollbackDoesNotRefresh() {
        var plans = mock(StudentPlanService.class);
        var service = service(plans);
        TransactionSynchronizationManager.initSynchronization();
        service.afterCommit(8L);
        for (var synchronization : TransactionSynchronizationManager.getSynchronizations()) synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        verifyNoInteractions(plans);
    }

    @Test void refreshFailureDoesNotChangeSuccessfulBusinessOutcome() {
        var plans = mock(StudentPlanService.class);
        when(plans.refresh(any())).thenThrow(new IllegalStateException("unavailable"));
        assertDoesNotThrow(() -> service(plans).afterCommit(8L));
        verify(plans).refresh(any());
    }

    @SuppressWarnings("unchecked")
    private StudentPlanRefreshService service(StudentPlanService plans) {
        ObjectProvider<StudentPlanService> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(plans);
        return new StudentPlanRefreshService(provider);
    }
}
