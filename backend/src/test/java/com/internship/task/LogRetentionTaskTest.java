package com.internship.task;

import com.internship.entity.OperateLog;
import com.internship.mapper.LoginLogMapper;
import com.internship.mapper.OperateLogMapper;
import com.internship.mapper.SecurityEventLogMapper;
import com.internship.service.AuditAlertService;
import com.internship.service.AuditLogService;
import com.internship.service.SystemConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 只使用模拟 Mapper 和临时目录，不访问业务数据库或备份目录。 */
class LogRetentionTaskTest {
    @TempDir
    Path temporaryDirectory;

    private LogRetentionTask task;
    private OperateLogMapper operations;
    private LoginLogMapper logins;
    private SecurityEventLogMapper security;
    private SystemConfigService config;
    private AuditLogService audit;
    private AuditAlertService alerts;

    @BeforeEach
    void setUp() {
        task = new LogRetentionTask();
        operations = mock(OperateLogMapper.class);
        logins = mock(LoginLogMapper.class);
        security = mock(SecurityEventLogMapper.class);
        config = mock(SystemConfigService.class);
        when(config.getConfigValue(anyString())).thenReturn(null);
        audit = mock(AuditLogService.class);
        alerts = mock(AuditAlertService.class);
        ReflectionTestUtils.setField(task, "operateLogMapper", operations);
        ReflectionTestUtils.setField(task, "loginLogMapper", logins);
        ReflectionTestUtils.setField(task, "securityEventLogMapper", security);
        ReflectionTestUtils.setField(task, "systemConfigService", config);
        ReflectionTestUtils.setField(task, "auditLogService", audit);
        ReflectionTestUtils.setField(task, "auditAlertService", alerts);
        ReflectionTestUtils.setField(task, "backupPath", temporaryDirectory.toString());
    }

    @Test
    void highRiskRowsAreDeletedOnlyAfterTheirArchiveIsReadable() throws Exception {
        OperateLog row = new OperateLog();
        row.setId(42L);
        row.setRiskLevel("HIGH");
        row.setDescription("必须保留的审计记录");
        when(operations.selectExpiredHighRiskBatch(any(), eq(0L), anyInt())).thenReturn(List.of(row));
        when(operations.deleteArchivedHighRisk(eq(List.of(42L)))).thenAnswer(invocation -> {
            try (var files = Files.list(temporaryDirectory.resolve("audit-archive/operate_log"))) {
                Path archive = files.findFirst().orElseThrow();
                try (GZIPInputStream input = new GZIPInputStream(Files.newInputStream(archive))) {
                    String json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                    assertTrue(json.contains("\"id\":42"));
                    assertTrue(json.contains("必须保留的审计记录"));
                }
            }
            return 1;
        });
        assertEquals(1L, task.cleanupExpiredLogs());
        verify(operations).deleteArchivedHighRisk(List.of(42L));
        verify(operations, never()).deleteBeforeTimeBatch(any(), anyInt());
        verify(security, never()).deleteBeforeTimeBatch(any(), anyInt());
    }

    @Test
    void archiveWriteFailureKeepsHighRiskRowsInDatabase() throws Exception {
        Path regularFile = temporaryDirectory.resolve("not-a-directory");
        Files.writeString(regularFile, "test");
        ReflectionTestUtils.setField(task, "backupPath", regularFile.toString());
        OperateLog row = new OperateLog();
        row.setId(42L);
        when(operations.selectExpiredHighRiskBatch(any(), eq(0L), anyInt())).thenReturn(List.of(row));
        assertThrows(Exception.class, () -> task.cleanupExpiredLogs());
        verify(operations).selectExpiredHighRiskBatch(any(), eq(0L), anyInt());
        verify(operations, never()).deleteArchivedHighRisk(anyList());
        verify(operations, never()).deleteBeforeTimeBatch(any(), anyInt());
    }

    @Test
    void unavailableAuditStopsScheduledCleanupBeforeAnyDeletion() {
        doThrow(new IllegalStateException("audit unavailable")).when(audit).recordCriticalAttempt(any());
        task.runRetention();
        verifyNoInteractions(operations, logins, security, config);
        verify(alerts).alert(eq(AuditAlertService.TYPE_RETENTION_FAILURE), anyString());
    }

    @Test
    void invalidStoredPolicyStopsCleanup() {
        when(config.getConfigValue("audit.retention.highRiskDays")).thenReturn("0");
        assertThrows(IllegalArgumentException.class, () -> task.cleanupExpiredLogs());
        verifyNoInteractions(operations, logins, security);
    }

    @Test
    void unreadablePolicyDoesNotSilentlySwitchToDefaultRetention() {
        when(config.getConfigValue(anyString())).thenThrow(new IllegalStateException("configuration unavailable"));
        assertThrows(IllegalStateException.class, () -> task.cleanupExpiredLogs());
        verifyNoInteractions(operations, logins, security);
    }

    @Test
    void nonnumericStoredPolicyCannotFallBackToShorterDefaults() {
        when(config.getConfigValue("audit.retention.highRiskDays")).thenReturn("730x");
        assertThrows(IllegalArgumentException.class, () -> task.cleanupExpiredLogs());
        verifyNoInteractions(operations, logins, security);
    }
}
