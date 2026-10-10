package com.internship.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.entity.OperateLog;
import com.internship.entity.SecurityEventLog;
import com.internship.mapper.LoginLogMapper;
import com.internship.mapper.OperateLogMapper;
import com.internship.mapper.SecurityEventLogMapper;
import com.internship.service.AuditAlertService;
import com.internship.service.AuditLogService;
import com.internship.service.SystemConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.ToIntFunction;
import java.util.zip.GZIPOutputStream;

/**
 * 日志时间留存任务（P1 + P2 归档增强）
 * 每天 02:30 分批删除到期数据；清理前后写入安全事件；失败告警。
 * 高风险记录到期后先归档为独立 JSONL.gz 文件（默认再保留2年）再删除，
 * 实现在线1年 + 归档2年的完整留存链。
 */
@Slf4j
@Component
public class LogRetentionTask {

    private static final int RETENTION_BATCH_SIZE = 5000;
    private static final String RETENTION_HIGH_RISK_DAYS_KEY = "audit.retention.highRiskDays";
    private static final String RETENTION_NORMAL_DAYS_KEY = "audit.retention.normalDays";
    private static final String RETENTION_LOGIN_DAYS_KEY = "audit.retention.loginDays";
    private static final String RETENTION_ARCHIVE_DAYS_KEY = "audit.retention.archiveDays";
    private static final int DEFAULT_ARCHIVE_DAYS = 730;

    @Autowired
    private OperateLogMapper operateLogMapper;

    @Autowired
    private LoginLogMapper loginLogMapper;

    @Autowired
    private SecurityEventLogMapper securityEventLogMapper;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private AuditAlertService auditAlertService;

    @Autowired
    private SystemConfigService systemConfigService;

    @Value("${backup.path:./backups}")
    private String backupPath;

    /** 归档 JSON 序列化器（注册时间模块，避免 Date 序列化失败） */
    private final ObjectMapper archiveMapper = new ObjectMapper().findAndRegisterModules();

    @Scheduled(cron = "0 30 2 * * ?")
    public void runRetention() {
        try {
            OperateLog attempt = new OperateLog();
            attempt.setOperatorName("SYSTEM");
            attempt.setOperatorUsername("SYSTEM");
            attempt.setOperatorRole("SYSTEM");
            attempt.setOperationType("CLEAN");
            attempt.setModule("LOG_MANAGEMENT");
            attempt.setRiskLevel("HIGH");
            attempt.setDescription("定时日志留存清理（02:30）");
            auditLogService.recordCriticalAttempt(attempt);
            recordRetentionEvent("ATTEMPTED", 0);
            long totalDeleted = cleanupExpiredLogs();
            recordRetentionEvent("SUCCESS", totalDeleted);
            log.info("日志留存清理完成：共删除 {} 条到期数据", totalDeleted);
        } catch (Exception e) {
            log.error("日志留存清理失败：{}", e.getMessage(), e);
            recordRetentionEvent("FAILURE", 0);
            auditAlertService.alert(AuditAlertService.TYPE_RETENTION_FAILURE,
                    "日志留存清理失败: " + e.getMessage());
        }
    }

    /** 手动和定时清理共用同一路径；同一应用实例内禁止并发清理。 */
    public synchronized long cleanupExpiredLogs() throws Exception {
        int highRiskDays = getRetentionConfig(RETENTION_HIGH_RISK_DAYS_KEY, 365);
        int normalDays = getRetentionConfig(RETENTION_NORMAL_DAYS_KEY, 180);
        int loginDays = getRetentionConfig(RETENTION_LOGIN_DAYS_KEY, 180);
        if (highRiskDays < normalDays) {
            throw new IllegalArgumentException("高风险日志留存时间不能短于普通业务日志");
        }
        long totalDeleted = deleteInBatches(
                before -> operateLogMapper.deleteBeforeTimeBatchExcludeHighRisk(before, RETENTION_BATCH_SIZE), daysBefore(normalDays));
        totalDeleted += deleteInBatches(
                before -> loginLogMapper.deleteBeforeTimeBatch(before, RETENTION_BATCH_SIZE), daysBefore(loginDays));
        totalDeleted += deleteInBatches(
                before -> securityEventLogMapper.deleteBeforeTimeBatchExcludeHighRisk(before, RETENTION_BATCH_SIZE), daysBefore(normalDays));
        Date highRiskBefore = daysBefore(highRiskDays);
        totalDeleted += archiveAndDeleteHighRiskOperateLogs(highRiskBefore);
        totalDeleted += archiveAndDeleteHighRiskSecurityEvents(highRiskBefore);
        cleanupExpiredArchives();
        return totalDeleted;
    }

    // ================================================================
    // P2 高风险归档 + 删除
    // ================================================================

    /** operate_log 高风险到期数据：归档 → 删除 */
    private long archiveAndDeleteHighRiskOperateLogs(Date before) throws Exception {
        long deleted = 0;
        long afterId = 0;
        while (true) {
            var batch = operateLogMapper.selectExpiredHighRiskBatch(before, afterId, RETENTION_BATCH_SIZE);
            if (batch.isEmpty()) {
                break;
            }
            String fileName = writeArchive("operate_log", batch);
            deleted += operateLogMapper.deleteArchivedHighRisk(batch.stream().map(OperateLog::getId).toList());
            afterId = batch.getLast().getId();
            recordArchiveEvent("operate_log", fileName, batch.size());
        }
        return deleted;
    }

    /** security_event_log 高风险到期数据：归档 → 删除 */
    private long archiveAndDeleteHighRiskSecurityEvents(Date before) throws Exception {
        long deleted = 0;
        long afterId = 0;
        while (true) {
            var batch = securityEventLogMapper.selectExpiredHighRiskBatch(before, afterId, RETENTION_BATCH_SIZE);
            if (batch.isEmpty()) {
                break;
            }
            String fileName = writeArchive("security_event_log", batch);
            deleted += securityEventLogMapper.deleteArchivedHighRisk(batch.stream().map(SecurityEventLog::getId).toList());
            afterId = batch.getLast().getId();
            recordArchiveEvent("security_event_log", fileName, batch.size());
        }
        return deleted;
    }

    private String writeArchive(String table, List<?> rows) throws Exception {
        String fileName = table + "-" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date())
                + "-" + UUID.randomUUID() + ".jsonl.gz";
        try (ArchiveWriter writer = new ArchiveWriter(archiveFile(table, fileName))) {
            for (Object row : rows) {
                writer.writeLine(archiveMapper.writeValueAsString(row));
            }
        }
        return fileName;
    }

    private File archiveFile(String table, String fileName) {
        File dir = new File(backupPath, "audit-archive" + File.separator + table);
        if (!dir.exists() && !dir.mkdirs()) {
            log.warn("归档目录创建失败: {}", dir.getAbsolutePath());
        }
        return new File(dir, fileName);
    }

    /** 清理超过归档保留期（默认730天）的归档文件 */
    private void cleanupExpiredArchives() {
        try {
            int archiveDays = getRetentionConfig(RETENTION_ARCHIVE_DAYS_KEY, DEFAULT_ARCHIVE_DAYS);
            File archiveRoot = new File(backupPath, "audit-archive");
            if (!archiveRoot.exists()) {
                return;
            }
            long expireMillis = System.currentTimeMillis() - archiveDays * 24L * 60L * 60L * 1000L;
            File[] tables = archiveRoot.listFiles(File::isDirectory);
            if (tables == null) {
                return;
            }
            for (File tableDir : tables) {
                File[] files = tableDir.listFiles();
                if (files == null) {
                    continue;
                }
                for (File file : files) {
                    if (file.lastModified() < expireMillis) {
                        Files.delete(file.toPath());
                        log.info("已删除过期归档文件: {}", file.getAbsolutePath());
                    }
                }
            }
        } catch (Exception e) {
            log.error("清理过期归档文件失败: {}", e.getMessage());
        }
    }

    private void recordArchiveEvent(String table, String fileName, long count) {
        try {
            var event = new com.internship.entity.SecurityEventLog();
            event.setEventType("AUDIT_ARCHIVE");
            event.setUsername("SYSTEM");
            event.setUserRole("SYSTEM");
            event.setRiskLevel("LOW");
            event.setResult("SUCCESS");
            event.setAffectedCount((int) count);
            event.setDescription("高风险日志到期归档：表=" + table + "，文件=" + fileName + "，共" + count + "条");
            event.setDeviceType("SYSTEM");
            auditLogService.recordSecurityEvent(event);
        } catch (Exception e) {
            log.error("记录归档安全事件失败: {}", e.getMessage());
        }
    }

    // ================================================================
    // 基础工具
    // ================================================================

    private int getRetentionConfig(String key, int defaultValue) {
        String rawValue = systemConfigService.getConfigValue(key);
        if (rawValue == null) {
            return defaultValue;
        }
        try {
            int value = Integer.parseInt(rawValue.trim());
            if (value < 30 || value > 3650) {
                throw new IllegalArgumentException("留存天数必须在30-3650之间");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("留存策略配置无效，请检查留存天数");
        }
    }

    private Date daysBefore(int days) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -days);
        return cal.getTime();
    }

    /** 分批删除直到无到期数据（避免长时间锁表） */
    private long deleteInBatches(ToIntFunction<Date> deleteBatch, Date beforeTime) {
        long total = 0;
        int deleted;
        do {
            deleted = deleteBatch.applyAsInt(beforeTime);
            total += deleted;
        } while (deleted >= RETENTION_BATCH_SIZE);
        return total;
    }

    /** 清理前后写入安全事件（SYSTEM 身份；清理记录为新数据不会被同批删除） */
    private void recordRetentionEvent(String result, long affectedCount) {
        try {
            var event = new com.internship.entity.SecurityEventLog();
            event.setEventType("RETENTION_CLEAN");
            event.setUsername("SYSTEM");
            event.setUserRole("SYSTEM");
            event.setRiskLevel("HIGH");
            event.setResult(result);
            event.setAffectedCount((int) affectedCount);
            event.setDescription("定时日志留存清理（02:30）");
            event.setDeviceType("SYSTEM");
            auditLogService.recordSecurityEvent(event);
        } catch (Exception e) {
            log.error("记录留存清理安全事件失败: {}", e.getMessage());
        }
    }

    /** 每批独立归档，写入及关闭成功后才能删除对应记录。 */
    private static class ArchiveWriter implements AutoCloseable {
        private final Writer writer;

        ArchiveWriter(File file) throws Exception {
            this.writer = new OutputStreamWriter(
                    new GZIPOutputStream(Files.newOutputStream(file.toPath(), StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE)), StandardCharsets.UTF_8);
        }

        void writeLine(String json) throws Exception {
            writer.write(json);
            writer.write('\n');
        }

        @Override
        public void close() throws Exception {
            writer.close();
        }
    }
}
