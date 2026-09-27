package com.internship.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Queues student confirmation-result mail only after the workflow transaction commits. */
@Slf4j
@Service
public class InternshipConfirmationResultNotifier {

    private final InternshipConfirmationResultMailer mailer;

    public InternshipConfirmationResultNotifier(InternshipConfirmationResultMailer mailer) {
        this.mailer = mailer;
    }

    public void afterCommit(InternshipConfirmationResultNotification notification) {
        if (notification == null || !notification.supportsEmail()) {
            return;
        }

        Runnable delivery = () -> {
            try {
                mailer.send(notification);
            } catch (RuntimeException e) {
                log.warn("实习确认结果邮件任务启动失败: recordId={}, status={}, error={}",
                        notification.recordId(), notification.status(), e.getMessage());
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    delivery.run();
                }
            });
        } else {
            delivery.run();
        }
    }
}
