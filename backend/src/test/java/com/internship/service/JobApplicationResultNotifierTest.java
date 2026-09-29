package com.internship.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JobApplicationResultNotifierTest {

    @Mock
    private JobApplicationResultMailer mailer;

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void sendsImmediatelyWithoutTransaction() {
        JobApplicationResultNotification notification = notification("approved", false);

        new JobApplicationResultNotifier(mailer).afterCommit(notification);

        verify(mailer).send(notification);
    }

    @Test
    void waitsForCommit() {
        JobApplicationResultNotification notification = notification("rejected", false);
        TransactionSynchronizationManager.initSynchronization();

        new JobApplicationResultNotifier(mailer).afterCommit(notification);

        verify(mailer, never()).send(notification);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        verify(mailer).send(notification);
    }

    @Test
    void rollbackDoesNotSend() {
        JobApplicationResultNotification notification = notification("interview_passed", true);
        TransactionSynchronizationManager.initSynchronization();

        new JobApplicationResultNotifier(mailer).afterCommit(notification);

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(mailer, never()).send(notification);
    }

    @Test
    void unsupportedStatusIsIgnored() {
        new JobApplicationResultNotifier(mailer).afterCommit(notification("withdrawn", false));

        verify(mailer, never()).send(org.mockito.ArgumentMatchers.any());
    }

    private JobApplicationResultNotification notification(String status, boolean directHire) {
        return new JobApplicationResultNotification(
                100L, 400L, 200L, "测试企业", 300L, "Java实习生", status, directHire);
    }
}
