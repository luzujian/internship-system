package com.internship.task;

import com.internship.entity.AuditSignatureRoot;
import com.internship.entity.OperateLog;
import com.internship.mapper.AuditSignatureRootMapper;
import com.internship.mapper.LoginLogMapper;
import com.internship.mapper.OperateLogMapper;
import com.internship.mapper.SecurityEventLogMapper;
import com.internship.service.AuditAlertService;
import com.internship.service.AuditHashService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 验证正常留存导致的证据缺口与内容/签名异常分别报告，不连接数据库。 */
class AuditIntegrityTaskTest {
    private AuditIntegrityTask task;
    private OperateLogMapper operations;
    private AuditSignatureRootMapper roots;
    private AuditHashService hashes;
    private AuditAlertService alerts;

    @BeforeEach
    void setUp() {
        task = new AuditIntegrityTask();
        operations = mock(OperateLogMapper.class);
        roots = mock(AuditSignatureRootMapper.class);
        hashes = mock(AuditHashService.class);
        alerts = mock(AuditAlertService.class);
        ReflectionTestUtils.setField(task, "operateLogMapper", operations);
        ReflectionTestUtils.setField(task, "loginLogMapper", mock(LoginLogMapper.class));
        ReflectionTestUtils.setField(task, "securityEventLogMapper", mock(SecurityEventLogMapper.class));
        ReflectionTestUtils.setField(task, "signatureRootMapper", roots);
        ReflectionTestUtils.setField(task, "auditHashService", hashes);
        ReflectionTestUtils.setField(task, "auditAlertService", alerts);
        when(hashes.canonicalPayload(any(OperateLog.class))).thenAnswer(invocation -> "p" + ((OperateLog) invocation.getArgument(0)).getId());
        when(hashes.computeEntryHash(anyString(), anyString(), anyInt()))
                .thenAnswer(invocation -> "h" + ((String) invocation.getArgument(1)).substring(1));
        when(hashes.verifyRootSignature(anyString(), any(LocalDate.class), anyLong(), anyString(), anyLong(), anyInt(), anyString()))
                .thenReturn(true);
    }

    @Test
    void retainedAwayMiddleRecordIsIncompleteWithoutFalseTamperingAlarm() {
        OperateLog first = row(1, AuditHashService.GENESIS_PREV_HASH);
        OperateLog third = row(3, "h2");
        chain(first, third);
        root(third);
        Map<String, Object> report = task.verifyAll(true);
        assertEquals("INCOMPLETE", report.get("status"));
        assertEquals(false, report.get("passed"));
        assertEquals(0, report.get("totalIssues"));
        assertEquals(1, report.get("totalWarnings"));
        assertEquals(2L, table(report).get("verifiedEntries"));
        verify(hashes, times(2)).computeEntryHash(anyString(), anyString(), anyInt());
        verifyNoInteractions(alerts);
    }

    @Test
    void modifiedPayloadStillFailsAndAlertsEvenWhenContinuityIsIncomplete() {
        OperateLog third = row(3, "missing-predecessor");
        third.setEntryHash("modified-hash");
        chain(third);
        root(third);
        Map<String, Object> report = task.verifyAll(true);
        assertEquals("FAILED", report.get("status"));
        assertEquals(1, report.get("totalIssues"));
        assertEquals(1, report.get("totalWarnings"));
        verify(alerts).alert(eq(AuditAlertService.TYPE_INTEGRITY_FAILURE), contains("1 个问题"));
    }

    @Test
    void invalidRootSignatureRemainsAFailure() {
        OperateLog first = row(1, AuditHashService.GENESIS_PREV_HASH);
        chain(first);
        root(first);
        when(hashes.verifyRootSignature(anyString(), any(LocalDate.class), anyLong(), anyString(), anyLong(), anyInt(), anyString()))
                .thenReturn(false);
        Map<String, Object> report = task.verifyAll(true);
        assertEquals("FAILED", report.get("status"));
        assertEquals(1, report.get("totalIssues"));
        verify(alerts).alert(eq(AuditAlertService.TYPE_INTEGRITY_FAILURE), contains("签名根被篡改"));
    }

    @Test
    void mismatchingExistingAnchorRemainsAFailure() {
        OperateLog first = row(1, AuditHashService.GENESIS_PREV_HASH);
        chain(first);
        AuditSignatureRoot root = root(first);
        root.setLastEntryHash("different-signed-hash");
        Map<String, Object> report = task.verifyAll(true);
        assertEquals("FAILED", report.get("status"));
        assertEquals(1, report.get("totalIssues"));
        verify(alerts).alert(eq(AuditAlertService.TYPE_INTEGRITY_FAILURE), contains("根锚点不一致"));
    }

    @Test
    void missingOnlineRootAnchorIsExplicitlyIncomplete() {
        OperateLog first = row(1, AuditHashService.GENESIS_PREV_HASH);
        root(first);
        when(operations.selectById(1L)).thenReturn(null);
        Map<String, Object> report = task.verifyAll(true);
        assertEquals("INCOMPLETE", report.get("status"));
        assertEquals(0, report.get("totalIssues"));
        assertEquals(1, report.get("totalWarnings"));
        verifyNoInteractions(alerts);
    }

    @Test
    void unhashedHistoricalRecordsCannotProduceAPassingReport() {
        when(operations.countUnchained()).thenReturn(12L);
        Map<String, Object> report = task.verifyAll(true);
        assertEquals("INCOMPLETE", report.get("status"));
        assertEquals(false, report.get("passed"));
        assertEquals(12L, table(report).get("unsignedEntries"));
        assertEquals(0L, table(report).get("verifiedEntries"));
    }

    @Test
    void emptyDatabaseIsNoDataInsteadOfPassing() {
        Map<String, Object> report = task.verifyAll(true);
        assertEquals("NO_DATA", report.get("status"));
        assertEquals(false, report.get("passed"));
        assertEquals(0, report.get("totalIssues"));
    }

    @Test
    void intactSignedChainCanPassWithOtherTablesEmpty() {
        OperateLog first = row(1, AuditHashService.GENESIS_PREV_HASH);
        OperateLog second = row(2, "h1");
        chain(first, second);
        root(second);
        Map<String, Object> report = task.verifyAll(true);
        assertEquals("PASSED", report.get("status"));
        assertEquals(true, report.get("passed"));
        assertEquals(0, report.get("totalWarnings"));
        verifyNoInteractions(alerts);
    }

    @Test
    void validHashesWithoutASignatureRootRemainIncomplete() {
        chain(row(1, AuditHashService.GENESIS_PREV_HASH));
        Map<String, Object> report = task.verifyAll(true);
        assertEquals("INCOMPLETE", report.get("status"));
        assertEquals(false, report.get("passed"));
        assertEquals(0, report.get("totalIssues"));
        assertEquals(1, report.get("totalWarnings"));
    }

    @Test
    void incrementalVerificationChecksFirstRowAgainstSignedBoundary() {
        OperateLog second = row(2, "h1");
        AuditSignatureRoot root = root(second);
        when(roots.selectLatest("operate_log")).thenReturn(root);
        OperateLog third = row(3, "different-predecessor");
        when(operations.selectChainedBatch(eq(2L), anyInt())).thenReturn(List.of(third));
        Map<String, Object> report = task.verifyAll(false);
        assertEquals("INCOMPLETE", report.get("status"));
        assertEquals(0, report.get("totalIssues"));
        assertEquals(1, report.get("totalWarnings"));
    }

    @Test
    void missingHashKeyRemainsAFailure() {
        OperateLog first = row(1, AuditHashService.GENESIS_PREV_HASH);
        chain(first);
        root(first);
        doThrow(new IllegalStateException("key unavailable")).when(hashes).computeEntryHash(anyString(), anyString(), anyInt());
        Map<String, Object> report = task.verifyAll(true);
        assertEquals("FAILED", report.get("status"));
        assertEquals(1, report.get("totalIssues"));
        verify(alerts).alert(eq(AuditAlertService.TYPE_INTEGRITY_FAILURE), contains("密钥缺失"));
    }

    private OperateLog row(long id, String previousHash) {
        OperateLog row = new OperateLog();
        row.setId(id);
        row.setPrevHash(previousHash);
        row.setEntryHash("h" + id);
        row.setKeyVersion(1);
        return row;
    }

    private void chain(OperateLog... rows) {
        when(operations.selectChainedBatch(eq(0L), anyInt())).thenReturn(List.of(rows));
    }

    private AuditSignatureRoot root(OperateLog anchor) {
        AuditSignatureRoot root = new AuditSignatureRoot();
        root.setRootDate(java.sql.Date.valueOf("2026-10-01"));
        root.setLastEntryId(anchor.getId());
        root.setLastEntryHash(anchor.getEntryHash());
        root.setEntryCount(anchor.getId());
        root.setKeyVersion(1);
        root.setSignature("signed-root");
        when(roots.selectByTable("operate_log")).thenReturn(List.of(root));
        when(operations.selectById(anchor.getId())).thenReturn(anchor);
        return root;
    }

    private Map<?, ?> table(Map<String, Object> report) {
        return (Map<?, ?>) report.get("operate_log");
    }
}
