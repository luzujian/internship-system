package com.internship.controller;

import com.github.pagehelper.PageHelper;
import com.internship.dto.LogExportRequest;
import com.internship.dto.LogQueryParams;
import com.internship.entity.LoginLog;
import com.internship.entity.OperateLog;
import com.internship.entity.SystemConfig;
import com.internship.exception.BusinessException;
import com.internship.mapper.LoginLogMapper;
import com.internship.mapper.OperateLogMapper;
import com.internship.mapper.SecurityEventLogMapper;
import com.internship.service.AuditLogService;
import com.internship.service.SystemConfigService;
import com.internship.task.LogRetentionTask;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 不启动 Spring、不连接数据库，覆盖日志中心查询与破坏性操作边界。 */
class LogCenterControllerTest {
    private LogController controller;
    private OperateLogMapper operations;
    private LoginLogMapper logins;
    private SecurityEventLogMapper security;
    private AuditLogService audit;
    private SystemConfigService config;
    private LogRetentionTask retention;

    @BeforeEach
    void setUp() {
        controller = new LogController();
        operations = mock(OperateLogMapper.class);
        logins = mock(LoginLogMapper.class);
        security = mock(SecurityEventLogMapper.class);
        audit = mock(AuditLogService.class);
        config = mock(SystemConfigService.class);
        when(config.getConfigValue(anyString())).thenReturn(null);
        retention = mock(LogRetentionTask.class);
        ReflectionTestUtils.setField(controller, "operateLogMapper", operations);
        ReflectionTestUtils.setField(controller, "loginLogMapper", logins);
        ReflectionTestUtils.setField(controller, "securityEventLogMapper", security);
        ReflectionTestUtils.setField(controller, "auditLogService", audit);
        ReflectionTestUtils.setField(controller, "systemConfigService", config);
        ReflectionTestUtils.setField(controller, "logRetentionTask", retention);
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        PageHelper.clearPage();
    }

    @Test
    void summaryQueriesOnlyAuthorizedCategories() {
        authorize("log:login:view");
        when(logins.countByTimeRange(any(), any(), eq("FAILURE"))).thenReturn(9L);
        Map<?, ?> summary = (Map<?, ?>) controller.getSummary().getData();
        assertEquals(9L, summary.get("todayFailedLogins"));
        assertNull(summary.get("todayOperations"));
        assertNull(summary.get("todayFailedOperations"));
        assertNull(summary.get("todayHighRiskEvents"));
        verifyNoInteractions(operations, security);
    }

    @Test
    void sharedOptionsSupportSystemAndAttemptedWithoutReadingUnauthorizedCategories() {
        authorize("log:login:view");
        Map<?, ?> options = (Map<?, ?>) controller.getOptions().getData();
        assertTrue(((List<?>) options.get("roles")).contains("SYSTEM"));
        assertTrue(((List<?>) options.get("results")).contains("ATTEMPTED"));
        verifyNoInteractions(operations, security);
    }

    @Test
    void zeroPageSizeCannotDisablePaginationAndStateIsCleared() {
        when(operations.selectByFilter(any())).thenAnswer(invocation -> {
            assertEquals(1, PageHelper.getLocalPage().getPageNum());
            assertEquals(1, PageHelper.getLocalPage().getPageSize());
            return List.of();
        });
        assertEquals(200, controller.queryOperations(0, 0, null, null, null, null, null,
                null, null, null, null, null, null, "operate_time", "desc").getCode());
        assertNull(PageHelper.getLocalPage());
    }

    @Test
    void queryCapsPageSizeAndClearsThreadLocalOnMapperFailure() {
        when(logins.selectByFilter(any())).thenAnswer(invocation -> {
            assertEquals(100, PageHelper.getLocalPage().getPageSize());
            throw new IllegalStateException("simulated database failure");
        });
        assertEquals(400, controller.queryLoginEvents(1, 100000, null, null, null, null,
                null, null, null, null, "login_time", "desc").getCode());
        assertNull(PageHelper.getLocalPage());
    }

    @Test
    void exportRejectsOverLimitBeforeReadingRowsAndAuditsFailure() throws Exception {
        authorize("log:view", "log:export");
        when(operations.countByFilter(any())).thenReturn(100001L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.exportLogs(new LogExportRequest(), response);
        assertEquals(400, response.getStatus());
        verify(operations, never()).selectByFilter(any());
        verify(audit).recordCriticalAttempt(any());
        ArgumentCaptor<OperateLog> event = ArgumentCaptor.forClass(OperateLog.class);
        verify(audit).record(event.capture());
        assertEquals("FAILURE", event.getValue().getOperationResult());
    }

    @Test
    void exportHasDatabaseLimitAndPreservesRequestIdWithMaskedIp() throws Exception {
        authorize("log:login:view", "log:export");
        LoginLog row = new LoginLog();
        row.setIpAddress("10.20.30.40");
        row.setRequestId("request-for-investigation");
        when(logins.countByFilter(any())).thenReturn(1L);
        when(logins.selectByFilter(any())).thenReturn(List.of(row));
        LogExportRequest request = new LogExportRequest();
        request.setCategory("login-events");
        request.setRiskLevel("HIGH,CRITICAL");
        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.exportLogs(request, response);
        assertEquals(200, response.getStatus());
        ArgumentCaptor<LogQueryParams> query = ArgumentCaptor.forClass(LogQueryParams.class);
        verify(logins).selectByFilter(query.capture());
        assertEquals(100001, query.getValue().getLimit());
        assertEquals("HIGH,CRITICAL", query.getValue().getRiskLevel());
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            assertEquals("10.20.*.*", workbook.getSheetAt(0).getRow(1).getCell(6).getStringCellValue());
            assertEquals("request-for-investigation", workbook.getSheetAt(0).getRow(1).getCell(9).getStringCellValue());
        }
    }

    @Test
    void exportRejectsInvalidCalendarDatesAndTrailingText() throws Exception {
        authorize("log:view", "log:export");
        for (String time : List.of("2026-02-30 00:00:00", "2026-01-01 00:00:00oops")) {
            LogExportRequest request = new LogExportRequest();
            request.setStartTime(time);
            MockHttpServletResponse response = new MockHttpServletResponse();
            controller.exportLogs(request, response);
            assertEquals(400, response.getStatus());
        }
        verifyNoInteractions(operations);
    }

    @Test
    void invalidPolicyDoesNotPartiallyPersistEarlierFields() {
        assertEquals(400, controller.updateRetentionPolicies(Map.of(
                "highRiskDays", 730, "normalDays", 1, "loginDays", 180)).getCode());
        verifyNoInteractions(config);
    }

    @Test
    void highRiskRetentionCannotBeShorterThanNormalRetention() {
        assertEquals(400, controller.updateRetentionPolicies(Map.of(
                "highRiskDays", 30, "normalDays", 180, "loginDays", 180)).getCode());
        verifyNoInteractions(config);
    }

    @Test
    void policyWriteFailureRollsBackTheWholeUpdate() {
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        SimpleTransactionStatus status = new SimpleTransactionStatus();
        when(transactionManager.getTransaction(any())).thenReturn(status);
        when(config.insert(any(SystemConfig.class))).thenReturn(1).thenThrow(new IllegalStateException("write failed"));
        ProxyFactory proxy = new ProxyFactory(controller);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        LogController transactionalController = (LogController) proxy.getProxy();
        assertThrows(BusinessException.class, () -> transactionalController.updateRetentionPolicies(Map.of(
                "highRiskDays", 365, "normalDays", 180, "loginDays", 180)));
        verify(transactionManager).rollback(status);
        verify(transactionManager, never()).commit(any());
        verify(config, times(2)).insert(any(SystemConfig.class));
    }

    @Test
    void unreadablePolicyReturnsFailureInsteadOfShowingDefaults() {
        when(config.getConfigValue(anyString())).thenThrow(new IllegalStateException("configuration unavailable"));
        assertEquals(400, controller.getRetentionPolicies().getCode());
        assertEquals(400, controller.previewRetention().getCode());
        verifyNoInteractions(operations, logins, security);
    }

    @Test
    void retentionPreviewCountsExclusiveNormalAndHighRiskGroups() {
        when(operations.countBeforeTimeExcludeHighRisk(any())).thenReturn(12L);
        when(operations.countBeforeTimeHighRisk(any())).thenReturn(3L);
        when(security.countBeforeTimeExcludeHighRisk(any())).thenReturn(5L);
        when(security.countBeforeTimeHighRisk(any())).thenReturn(2L);
        Map<?, ?> result = (Map<?, ?>) controller.previewRetention().getData();
        assertEquals(12L, result.get("operateLogNormal"));
        assertEquals(3L, result.get("operateLogHighRisk"));
        assertEquals(5L, result.get("securityEventNormal"));
        assertEquals(2L, result.get("securityEventHighRisk"));
        verify(operations, never()).countBeforeTime(any());
        verify(security, never()).countBeforeTime(any());
    }

    @Test
    void manualRetentionUsesSameArchivalServiceAsScheduledRetention() throws Exception {
        when(retention.cleanupExpiredLogs()).thenReturn(8L);
        var response = controller.runRetention(Map.of("reason", "按已批准的留存策略清理"));
        assertEquals(200, response.getCode());
        assertEquals(8L, ((Map<?, ?>) response.getData()).get("deletedCount"));
        verify(retention).cleanupExpiredLogs();
        verifyNoInteractions(operations, logins, security);
    }

    @Test
    void legacyDeletionEndpointsCannotBypassRetention() {
        assertEquals(400, controller.manualCleanOperationLogs("ADMIN", true).getCode());
        assertEquals(400, controller.autoCleanOperationLogs().getCode());
        verifyNoInteractions(operations, logins, security, retention);
    }

    private void authorize(String... permissions) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", "", Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList()));
    }
}
