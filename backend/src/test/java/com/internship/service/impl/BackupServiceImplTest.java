package com.internship.service.impl;

import com.internship.entity.BackupAuditLog;
import com.internship.entity.BackupRecord;
import com.internship.entity.BackupSchedule;
import com.internship.mapper.BackupRecordMapper;
import com.internship.mapper.BackupScheduleMapper;
import com.internship.service.BackupAuditService;
import com.internship.utils.AliyunOSSOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BackupServiceImplTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path temp;

    @Test
    void invalidCaDoesNotLeaveCredentialFile() throws Exception {
        ReflectionTestUtils.setField(service, "datasourceUrl", "jdbc:mysql://127.0.0.1:3306/fixture");
        ReflectionTestUtils.setField(service, "datasourceUsername", "synthetic-user");
        ReflectionTestUtils.setField(service, "datasourcePassword", "synthetic-password");
        ReflectionTestUtils.setField(service, "backupTlsCa", "invalid\"ca.pem");
        java.nio.file.Path root = java.nio.file.Path.of(System.getProperty("java.io.tmpdir"));
        java.util.Set<java.nio.file.Path> before;
        try (var files = java.nio.file.Files.list(root)) {
            before = files.filter(p -> p.getFileName().toString().startsWith("mysql-config-")).collect(java.util.stream.Collectors.toSet());
        }
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "createTempConfigFile"));
        try (var files = java.nio.file.Files.list(root)) {
            assertFalse(files.anyMatch(p -> p.getFileName().toString().startsWith("mysql-config-") && !before.contains(p)));
        }
    }

    @Test
    void localStorageRoundTripDoesNotContactOssAndRejectsOutsideRoot() throws Exception {
        ReflectionTestUtils.setField(service, "storageType", "LOCAL");
        ReflectionTestUtils.setField(service, "backupPath", temp.resolve("backups").toString());
        byte[] bytes = "SQL test fixture".getBytes();
        String path = ReflectionTestUtils.invokeMethod(service, "storeBackup", bytes, "backups/test.sql");
        org.junit.jupiter.api.Assertions.assertArrayEquals(bytes, service.readBackup(path));
        java.nio.file.Path outside = java.nio.file.Files.write(temp.resolve("outside.sql"), bytes);
        org.junit.jupiter.api.Assertions.assertThrows(SecurityException.class, () -> service.readBackup(outside.toUri().toString()));
        ReflectionTestUtils.invokeMethod(service, "deleteStoredBackup", path);
        assertFalse(java.nio.file.Files.exists(java.nio.file.Path.of(java.net.URI.create(path))));
        verifyNoInteractions(oss);
    }

    @Test
    void restoreRejectsTamperingBeforeAccessingDatabase() throws Exception {
        byte[] original = "SELECT 1;".getBytes();
        BackupRecord record = record(10L, "https://bucket.example/backups/test.sql", original);
        when(records.findById(10L)).thenReturn(record);
        when(oss.downloadFile(record.getBackupPath())).thenReturn("SELECT 2;".getBytes());
        var failure = org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> service.restoreBackup(10L));
        assertTrue(failure.getMessage().contains("完整性校验失败"));
        verify(oss).downloadFile(record.getBackupPath());
        verifyNoInteractions(schedules, audits);
    }
    private final BackupRecordMapper records = mock(BackupRecordMapper.class);
    private final BackupScheduleMapper schedules = mock(BackupScheduleMapper.class);
    private final BackupAuditService audits = mock(BackupAuditService.class);
    private final AliyunOSSOperator oss = mock(AliyunOSSOperator.class);
    private final BackupServiceImpl service = new BackupServiceImpl();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "backupRecordMapper", records);
        ReflectionTestUtils.setField(service, "backupScheduleMapper", schedules);
        ReflectionTestUtils.setField(service, "backupAuditService", audits);
        ReflectionTestUtils.setField(service, "aliyunOSSOperator", oss);
    }

    @Test
    void verifiesDownloadedOssBackupAndRejectsChangedContent() throws Exception {
        byte[] original = "CREATE TABLE example (id INT);".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        BackupRecord record = record(1L, "https://bucket.example/backups/backup.sql", original);
        when(records.findById(1L)).thenReturn(record);
        when(oss.downloadFile(record.getBackupPath())).thenReturn(original);
        assertTrue(service.verifyBackup(1L));

        byte[] changed = original.clone();
        changed[0]++;
        when(oss.downloadFile(record.getBackupPath())).thenReturn(changed);
        assertFalse(service.verifyBackup(1L));
    }

    @Test
    void verifiesCompressedBackupUsingChecksumOfOriginalSql() throws Exception {
        byte[] original = "INSERT INTO example VALUES (1);".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(original);
        }
        byte[] compressed = output.toByteArray();
        BackupRecord record = record(2L, "https://bucket.example/backups/backup.sql", original);
        record.setIsCompressed(true);
        record.setBackupSize((long) compressed.length);
        when(records.findById(2L)).thenReturn(record);
        when(oss.downloadFile(record.getBackupPath())).thenReturn(compressed);

        assertTrue(service.verifyBackup(2L));
    }

    @Test
    void rejectsBackupWithoutChecksumBeforeDownloading() {
        BackupRecord record = new BackupRecord();
        record.setId(3L);
        when(records.findById(3L)).thenReturn(record);

        assertFalse(service.verifyBackup(3L));
        verifyNoInteractions(oss);
    }

    @Test
    void expiredCleanupDeletesOssObjectsAndRetainsFailedRecords() throws Exception {
        BackupSchedule schedule = new BackupSchedule();
        schedule.setRetentionDays(30);
        BackupRecord failed = record(1L, "https://bucket.example/backups/failed.sql", new byte[]{1});
        BackupRecord removed = record(2L, "https://bucket.example/backups/removed.sql", new byte[]{2});
        BackupAuditLog audit = new BackupAuditLog();
        audit.setId(9L);
        when(schedules.getSchedule()).thenReturn(schedule);
        when(records.findExpiredRecords(any())).thenReturn(List.of(failed, removed));
        when(audits.startAuditLog(eq("CLEAN_EXPIRED"), anyString(), eq("system"), eq("127.0.0.1")))
                .thenReturn(audit);
        org.mockito.Mockito.doThrow(new IllegalStateException("OSS unavailable"))
                .when(oss).deleteFile(failed.getBackupPath());

        service.cleanExpiredBackups();

        verify(oss).deleteFile(failed.getBackupPath());
        verify(oss).deleteFile(removed.getBackupPath());
        verify(records).deleteByIds("2");
        verify(audits).completeAuditLog(eq(9L), eq(2), contains("1"), anyLong());
    }

    private BackupRecord record(Long id, String path, byte[] original) throws Exception {
        BackupRecord record = new BackupRecord();
        record.setId(id);
        record.setBackupName("backup.sql");
        record.setBackupPath(path);
        record.setBackupSize((long) original.length);
        record.setChecksum(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original)));
        return record;
    }
}
