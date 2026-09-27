package com.internship.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Queues student result mail only after the application state change commits. */
@Slf4j
@Service
public class JobApplicationResultNotifier {

    private final JobApplicationResultMailer mailer;

    public JobApplicationResultNotifier(JobApplicationResultMailer mailer) {
        this.mailer = mailer;
    }

    public void afterCommit(JobApplicationResultNotification notification) {
        if (notification == null || !notification.supportsEmail()) {
            return;
        }

        Runnable delivery = () -> {
            try {
                mailer.send(notification);
            } catch (RuntimeException e) {
                log.warn("申请结果邮件任务启动失败: applicationId={}, status={}, error={}",
                        notification.applicationId(), notification.status(), e.getMessage());
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
