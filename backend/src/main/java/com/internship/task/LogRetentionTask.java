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
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.function.ToIntFunction;
import java.util.zip.GZIPOutputStream;

/**
 * 日志时间留存任务（P1 + P2 归档增强）
 * 每天 02:30 分批删除到期数据；清理前后写入安全事件；失败告警。
 * P2：高风险记录（权限、管理员、备份、日志管理等）到期后先归档为 JSONL.gz 不可变文件（默认再保留2年）再删除，
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
            int highRiskDays = getRetentionConfig(RETENTION_HIGH_RISK_DAYS_KEY, 365);
            int normalDays = getRetentionConfig(RETENTION_NORMAL_DAYS_KEY, 180);
            int loginDays = getRetentionConfig(RETENTION_LOGIN_DAYS_KEY, 180);

            recordRetentionEvent("ATTEMPTED", 0);

            long totalDeleted = 0;
            // 普通业务日志（非高风险）
            totalDeleted += deleteInBatches(
                    before -> operateLogMapper.deleteBeforeTimeBatchExcludeHighRisk(before, RETENTION_BATCH_SIZE),
                    daysBefore(normalDays));
            // 登录日志
            totalDeleted += deleteInBatches(
                    before -> loginLogMapper.deleteBeforeTimeBatch(before, RETENTION_BATCH_SIZE),
                    daysBefore(loginDays));
            // 安全事件（非高风险）
            totalDeleted += deleteInBatches(
                    before -> securityEventLogMapper.deleteBeforeTimeBatchExcludeHighRisk(before, RETENTION_BATCH_SIZE),
                    daysBefore(normalDays));

            // 高风险记录：先归档（不可变 JSONL.gz）再删除，按更长留存
            Date highRiskBefore = daysBefore(highRiskDays);
            totalDeleted += archiveAndDeleteHighRiskOperateLogs(highRiskBefore);
            totalDeleted += archiveAndDeleteHighRiskSecurityEvents(highRiskBefore);

            // 清理超过归档保留期的归档文件
            cleanupExpiredArchives();

            recordRetentionEvent("SUCCESS", totalDeleted);
            log.info("日志留存清理完成：共删除 {} 条到期数据", totalDeleted);
        } catch (Exception e) {
            log.error("日志留存清理失败：{}", e.getMessage(), e);
            recordRetentionEvent("FAILURE", 0);
            auditAlertService.alert(AuditAlertService.TYPE_RETENTION_FAILURE,
                    "日志留存清理失败: " + e.getMessage());
        }
    }

    // ================================================================
    // P2 高风险归档 + 删除
    // ================================================================

    /** operate_log 高风险到期数据：归档 → 删除 */
    private long archiveAndDeleteHighRiskOperateLogs(Date before) throws Exception {
        String fileName = "operate_log-" + new SimpleDateFormat("yyyyMMdd").format(new Date()) + ".jsonl.gz";
        long archived = 0;
        long afterId = 0;
        try (ArchiveWriter writer = new ArchiveWriter(archiveFile("operate_log", fileName))) {
            while (true) {
                var batch = operateLogMapper.selectExpiredHighRiskBatch(before, afterId, RETENTION_BATCH_SIZE);
                if (batch.isEmpty()) {
                    break;
                }
                for (OperateLog row : batch) {
                    writer.writeLine(archiveMapper.writeValueAsString(row));
                    afterId = row.getId();
                    archived++;
                }
            }
        }
        long deleted = deleteInBatches(
                beforeTime -> operateLogMapper.deleteBeforeTimeBatch(beforeTime, RETENTION_BATCH_SIZE), before);
        if (archived > 0) {
            recordArchiveEvent("operate_log", fileName, archived);
        }
        return deleted;
    }

    /** security_event_log 高风险到期数据：归档 → 删除 */
    private long archiveAndDeleteHighRiskSecurityEvents(Date before) throws Exception {
        String fileName = "security_event_log-" + new SimpleDateFormat("yyyyMMdd").format(new Date()) + ".jsonl.gz";
        long archived = 0;
        long afterId = 0;
        try (ArchiveWriter writer = new ArchiveWriter(archiveFile("security_event_log", fileName))) {
            while (true) {
                var batch = securityEventLogMapper.selectExpiredHighRiskBatch(before, afterId, RETENTION_BATCH_SIZE);
                if (batch.isEmpty()) {
                    break;
                }
                for (SecurityEventLog row : batch) {
                    writer.writeLine(archiveMapper.writeValueAsString(row));
                    afterId = row.getId();
                    archived++;
                }
            }
        }
        long deleted = deleteInBatches(
                beforeTime -> securityEventLogMapper.deleteBeforeTimeBatch(beforeTime, RETENTION_BATCH_SIZE), before);
        if (archived > 0) {
            recordArchiveEvent("security_event_log", fileName, archived);
        }
        return deleted;
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
        try {
            Integer value = systemConfigService.getConfigValueAsInt(key);
            return value != null ? value : defaultValue;
        } catch (Exception e) {
            return defaultValue;
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

    /** 追加式 JSONL.gz 归档写入器（gzip 支持多成员追加，标准解压工具可读） */
    private static class ArchiveWriter implements AutoCloseable {
        private final Writer writer;

        ArchiveWriter(File file) throws Exception {
            this.writer = new OutputStreamWriter(
                    new GZIPOutputStream(new FileOutputStream(file, true)), StandardCharsets.UTF_8);
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
