package com.internship.service.impl;

import com.internship.entity.OperateLog;
import com.internship.entity.SecurityEventLog;
import com.internship.exception.BusinessException;
import com.internship.mapper.LoginLogMapper;
import com.internship.mapper.OperateLogMapper;
import com.internship.mapper.SecurityEventLogMapper;
import com.internship.service.AuditAlertService;
import com.internship.service.AuditHashService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceImplTest {

    @Mock
    private OperateLogMapper operateLogMapper;

    @Mock
    private LoginLogMapper loginLogMapper;

    @Mock
    private SecurityEventLogMapper securityEventLogMapper;

    @Mock
    private AuditHashService auditHashService;

    @Mock
    private AuditAlertService auditAlertService;

    @InjectMocks
    private AuditLogServiceImpl auditLogService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        // 哈希链 Mock 默认行为（lenient：部分用例不触发）
        lenient().when(auditHashService.currentKeyVersion()).thenReturn(1);
        lenient().when(auditHashService.computeEntryHash(anyString(), anyString())).thenReturn("a".repeat(64));
        lenient().when(auditHashService.computeEntryHash(anyString(), anyString(), anyInt())).thenReturn("a".repeat(64));
        lenient().when(auditHashService.canonicalPayload(any(com.internship.entity.OperateLog.class))).thenReturn("op-payload");
        lenient().when(auditHashService.canonicalPayload(any(com.internship.entity.LoginLog.class))).thenReturn("lg-payload");
        lenient().when(auditHashService.canonicalPayload(any(com.internship.entity.SecurityEventLog.class))).thenReturn("se-payload");
    }

    @Test
    void hashChainFieldsAreSetOnRecord() {
        com.internship.entity.OperateLog logEntry = new com.internship.entity.OperateLog();
        logEntry.setModule("LOG_MANAGEMENT");
        logEntry.setOperationType("CREATE");

        auditLogService.record(logEntry);

        org.mockito.ArgumentCaptor<com.internship.entity.OperateLog> captor =
                org.mockito.ArgumentCaptor.forClass(com.internship.entity.OperateLog.class);
        verify(operateLogMapper).insert(captor.capture());
        assertEquals("a".repeat(64), captor.getValue().getEntryHash(), "必须写入链节点哈希");
        assertEquals(com.internship.service.AuditHashService.GENESIS_PREV_HASH, captor.getValue().getPrevHash(),
                "首条记录的前驱为创世哈希");
        assertEquals(1, captor.getValue().getKeyVersion(), "必须记录密钥版本");
    }

    @Test
    void hashChainFailureDegradesToUnhashedWriteWithAlert() {
        // 哈希计算抛异常：降级为无哈希写入，且不阻断
        lenient().when(auditHashService.currentKeyVersion()).thenThrow(new RuntimeException("key missing"));
        com.internship.entity.OperateLog logEntry = new com.internship.entity.OperateLog();
        logEntry.setModule("LOG_MANAGEMENT");
        logEntry.setOperationType("UPDATE");

        auditLogService.record(logEntry);

        org.mockito.ArgumentCaptor<com.internship.entity.OperateLog> captor =
                org.mockito.ArgumentCaptor.forClass(com.internship.entity.OperateLog.class);
        verify(operateLogMapper).insert(captor.capture());
        org.junit.jupiter.api.Assertions.assertNull(captor.getValue().getEntryHash(), "哈希失败时降级为空哈希");
        verify(auditAlertService).alert(org.mockito.ArgumentMatchers.eq(com.internship.service.AuditAlertService.TYPE_WRITE_FAILURE),
                org.mockito.ArgumentMatchers.contains("哈希链计算降级"));
    }

    @Test
    void legacyActionsAreNormalizedToCanonicalOnes() {
        assertEquals("CREATE", auditLogService.normalizeAction("ADD"));
        assertEquals("CREATE", auditLogService.normalizeAction("INSERT"));
        assertEquals("UPDATE", auditLogService.normalizeAction("MODIFY"));
        assertEquals("DELETE", auditLogService.normalizeAction("REMOVE"));
        assertEquals("READ", auditLogService.normalizeAction("SELECT"));
        assertEquals("READ", auditLogService.normalizeAction("QUERY"));
        // 非历史别名保持原样
        assertEquals("EXPORT", auditLogService.normalizeAction("EXPORT"));
    }

    @Test
    void actionAliasesExpandForBackwardCompatibleFiltering() {
        assertTrue(auditLogService.expandActionAliases("CREATE").containsAll(
                java.util.List.of("CREATE", "ADD", "INSERT")));
        assertTrue(auditLogService.expandActionAliases("READ").contains("SELECT"));
        // 未映射动作原样返回
        assertEquals(java.util.List.of("LOGIN"), auditLogService.expandActionAliases("LOGIN"));
    }

    @Test
    void serverGeneratesOperateTimeAndRiskLevelOnRecord() {
        OperateLog logEntry = new OperateLog();
        logEntry.setModule("LOG_MANAGEMENT");
        logEntry.setOperationType("ADD");
        logEntry.setOperationResult("SUCCESS");

        auditLogService.record(logEntry);

        ArgumentCaptor<OperateLog> captor = ArgumentCaptor.forClass(OperateLog.class);
        verify(operateLogMapper).insert(captor.capture());
        assertNotNull(captor.getValue().getOperateTime(), "操作时间必须由服务端生成");
        assertEquals("CREATE", captor.getValue().getOperationType(), "动作必须规范化");
        assertEquals("LOW", captor.getValue().getRiskLevel(), "风险等级默认 LOW");
        assertTrue(captor.getValue().getOperateTime().before(new Date(System.currentTimeMillis() + 1000)));
    }

    @Test
    void criticalOperationIsBlockedWhenAuditWriteFails() {
        doThrow(new RuntimeException("db down")).when(operateLogMapper).insert(any(OperateLog.class));

        OperateLog logEntry = new OperateLog();
        logEntry.setModule("LOG_MANAGEMENT");
        logEntry.setOperationType("CLEAN");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> auditLogService.recordCriticalAttempt(logEntry));
        assertTrue(exception.getMessage().contains("审计服务暂不可用"));
    }

    @Test
    void normalOperationIsNotBlockedWhenAuditWriteFails() {
        doThrow(new RuntimeException("db down")).when(operateLogMapper).insert(any(OperateLog.class));

        OperateLog logEntry = new OperateLog();
        logEntry.setModule("LOG_MANAGEMENT");
        logEntry.setOperationType("UPDATE");

        // 普通操作审计失败不覆盖业务结果：不抛异常
        auditLogService.record(logEntry);
    }

    @Test
    void securityEventTimeIsServerGeneratedWithDefaultRisk() {
        SecurityEventLog event = new SecurityEventLog();
        event.setEventType("LOGIN_FAILURE");
        event.setResult("FAILURE");

        auditLogService.recordSecurityEvent(event);

        ArgumentCaptor<SecurityEventLog> captor = ArgumentCaptor.forClass(SecurityEventLog.class);
        verify(securityEventLogMapper).insert(captor.capture());
        assertNotNull(captor.getValue().getEventTime(), "事件时间必须由服务端生成");
        assertEquals("LOW", captor.getValue().getRiskLevel(), "风险等级默认 LOW");
    }

    @Test
    void securityEventWriteFailureDoesNotPropagate() {
        doThrow(new RuntimeException("db down")).when(securityEventLogMapper).insert(any(SecurityEventLog.class));

        SecurityEventLog event = new SecurityEventLog();
        event.setEventType("LOGIN_FAILURE");

        // 安全事件写入失败仅告警，不影响业务
        auditLogService.recordSecurityEvent(event);
    }
}
