package com.internship.controller;

import com.internship.anno.Log;
import com.internship.entity.InternshipConfirmationRecord;
import com.internship.entity.Position;
import com.internship.entity.Result;
import com.internship.entity.User;
import com.internship.mapper.PositionMapper;
import com.internship.service.InternshipConfirmationRecordService;
import com.internship.service.InternshipConfirmationWorkflowService;
import com.internship.service.StudentJobApplicationService;
import com.internship.service.UserService;
import com.internship.websocket.AnnouncementWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/company/confirmation")
@PreAuthorize("hasRole('COMPANY')")
public class CompanyConfirmationController {
    private final UserService userService;
    private final InternshipConfirmationRecordService confirmationRecordService;
    private final InternshipConfirmationWorkflowService workflowService;
    private final StudentJobApplicationService applicationService;
    private final PositionMapper positionMapper;
    private final AnnouncementWebSocketHandler webSocketHandler;

    public CompanyConfirmationController(UserService userService,
                                         InternshipConfirmationRecordService confirmationRecordService,
                                         InternshipConfirmationWorkflowService workflowService,
                                         StudentJobApplicationService applicationService,
                                         PositionMapper positionMapper,
                                         AnnouncementWebSocketHandler webSocketHandler) {
        this.userService = userService;
        this.confirmationRecordService = confirmationRecordService;
        this.workflowService = workflowService;
        this.applicationService = applicationService;
        this.positionMapper = positionMapper;
        this.webSocketHandler = webSocketHandler;
    }

    @GetMapping("/pending")
    public Result getPendingConfirmations() {
        User user = currentUser();
        if (user == null) return Result.error("未登录");
        return Result.success(confirmationRecordService.findByCompanyIdAndStatus(user.getId(), 0));
    }

    @GetMapping("/all")
    public Result getAllConfirmations() {
        User user = currentUser();
        if (user == null) return Result.error("未登录");
        return Result.success(confirmationRecordService.findByCompanyId(user.getId()));
    }

    @PostMapping("/confirm/{id}")
    @Transactional(rollbackFor = Exception.class)
    @Log(operationType = "AUDIT", module = "INTERNSHIP_MANAGEMENT", description = "企业确认实习", riskLevel = "MEDIUM")
    public Result confirm(@PathVariable Long id) {
        try {
            User user = currentUser();
            if (user == null) return Result.error("未登录");
            InternshipConfirmationWorkflowService.ConfirmationOutcome outcome =
                    workflowService.confirm(user.getId(), id, null);
            runAfterCommit(() -> notifyResolution(user.getId(), outcome, 1));
            return Result.success("确认成功");
        } catch (Exception e) {
            rollbackCurrentTransaction();
            log.error("确认实习失败: {}", e.getMessage(), e);
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/reject/{id}")
    @Transactional(rollbackFor = Exception.class)
    @Log(operationType = "AUDIT", module = "INTERNSHIP_MANAGEMENT", description = "企业拒绝实习申请", riskLevel = "MEDIUM")
    public Result reject(@PathVariable Long id, @RequestParam String reason) {
        try {
            User user = currentUser();
            if (user == null) return Result.error("未登录");
            if (reason == null || reason.isBlank()) return Result.error("请填写拒绝原因");
            InternshipConfirmationWorkflowService.ConfirmationOutcome outcome =
                    workflowService.reject(user.getId(), id, reason.trim());
            runAfterCommit(() -> notifyResolution(user.getId(), outcome, 2));
            return Result.success("已拒绝");
        } catch (Exception e) {
            rollbackCurrentTransaction();
            log.error("拒绝实习失败: {}", e.getMessage(), e);
            return Result.error(e.getMessage());
        }
    }

    private void notifyResolution(Long companyId,
                                  InternshipConfirmationWorkflowService.ConfirmationOutcome outcome,
                                  int resultStatus) {
        InternshipConfirmationRecord record = outcome.record();
        pushTodoUpdate(companyId);
        webSocketHandler.sendConfirmationResultToStudent(
                record.getStudentId(), resultStatus, record.getCompanyName(), record.getPositionName());
        if (resultStatus == 1 && outcome.internshipStatus() != null) {
            webSocketHandler.sendInternshipStatusUpdateToStudent(
                    record.getStudentId(), outcome.internshipStatus());
            pushPositionUpdates(outcome.oldPositionId(), outcome.newPositionId());
        }
    }

    private void pushTodoUpdate(Long companyId) {
        try {
            long pendingApplications = applicationService.findByCompanyId(companyId).stream()
                    .filter(item -> "pending".equals(item.getStatus()))
                    .count();
            long pendingConfirmations = confirmationRecordService
                    .findByCompanyIdAndStatus(companyId, 0).size();
            webSocketHandler.sendCompanyTodoUpdate(
                    companyId, pendingApplications, pendingConfirmations);
        } catch (Exception e) {
            log.warn("推送企业待办更新失败: {}", e.getMessage());
        }
    }

    private void pushPositionUpdates(Long oldPositionId, Long newPositionId) {
        Set<Long> ids = new LinkedHashSet<>();
        if (oldPositionId != null) ids.add(oldPositionId);
        if (newPositionId != null) ids.add(newPositionId);
        for (Long id : ids) {
            Position position = positionMapper.findById(id);
            if (position != null) webSocketHandler.sendPositionUpdateToAll(position);
        }
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !(auth.getPrincipal() instanceof org.springframework.security.core.userdetails.User principal)) {
            return null;
        }
        return userService.findByUsername(principal.getUsername());
    }

    private void rollbackCurrentTransaction() {
        try {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        } catch (NoTransactionException ignored) {
            // Direct controller unit tests do not run through a transactional proxy.
        }
    }
}
