package com.internship.agent.business;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashSet;
import java.util.Set;

/** 实际业务提交后的确定性计划刷新；不调用模型、不发送消息，失败不能影响已完成业务。 */
@Service
@Slf4j
public class StudentPlanRefreshService {
    private final ObjectProvider<StudentPlanService> plans;
    private final Object transactionKey = new Object();

    public StudentPlanRefreshService(ObjectProvider<StudentPlanService> plans) { this.plans = plans; }

    public void afterCommit(Long studentId) {
        if (studentId == null || studentId <= 0) return;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            refresh(studentId);
            return;
        }
        @SuppressWarnings("unchecked")
        Set<Long> queued = (Set<Long>) TransactionSynchronizationManager.getResource(transactionKey);
        if (queued == null) {
            queued = new HashSet<>();
            Set<Long> pending = queued;
            TransactionSynchronizationManager.bindResource(transactionKey, pending);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { pending.forEach(StudentPlanRefreshService.this::refresh); }
                @Override public void afterCompletion(int status) { TransactionSynchronizationManager.unbindResourceIfPossible(transactionKey); }
            });
        }
        queued.add(studentId);
    }

    private void refresh(Long studentId) {
        try {
            plans.getObject().refresh(new AgentTypes.Actor("ROLE_STUDENT:" + studentId + ":false:", studentId,
                    "ROLE_STUDENT", "", null, null, null, false, Set.of()));
        } catch (Exception e) {
            log.warn("业务已提交，计划快照暂未刷新 student={}，下次读取会重新计算", studentId);
        }
    }
}
