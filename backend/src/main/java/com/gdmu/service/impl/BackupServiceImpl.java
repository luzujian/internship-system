package com.gdmu.service.impl;

import com.gdmu.entity.BackupRecord;
import com.gdmu.entity.BackupSchedule;
import com.gdmu.entity.BackupAuditLog;
import com.gdmu.mapper.BackupRecordMapper;
import com.gdmu.mapper.BackupScheduleMapper;
import com.gdmu.service.BackupService;
import com.gdmu.service.BackupAuditService;
import com.gdmu.utils.BackupUtils;
import com.gdmu.utils.AliyunOSSOperator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.io.File;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import jakarta.annotation.PostConstruct;

@Slf4j
@Service
public class BackupServiceImpl implements BackupService {

    private final ReentrantLock backupLock = new ReentrantLock();
    private volatile boolean autoBackupExecutedToday = false;

    @Autowired
    private BackupRecordMapper backupRecordMapper;

    @Autowired
    private BackupScheduleMapper backupScheduleMapper;

    @Autowired
    private BackupAuditService backupAuditService;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AliyunOSSOperator aliyunOSSOperator;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String datasourceUsername;

    @Value("${spring.datasource.password}")
    private String datasourcePassword;

    @Value("${backup.path:OSS}")
    private String backupPath;

    @Value("${backup.retention.days:30}")
    private Integer retentionDays;

    @Value("${backup.compress:false}")
    private Boolean compressBackup;

    @Value("${backup.format:SQL}")
    private String backupFormat;

    @Value("${backup.storage.type:OSS}")
    private String storageType;

    @Value("${backup.storage.oss.enabled:true}")
    private Boolean ossEnabled;

    private static final Pattern DB_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");
    private static final Pattern PATH_PATTERN = Pattern.compile("^[a-zA-Z]:[\\\\/].*$|^[a-zA-Z0-9_\\-/]{1,255}$");

    @PostConstruct
    public void init() {
        log.info("备份存储类型: {}, OSS启用: {}", storageType, ossEnabled);
        if ("OSS".equalsIgnoreCase(storageType) && ossEnabled) {
            log.info("备份文件将存储在阿里云OSS上");
        } else {
            log.info("备份文件将存储在本地文件系统: {}", backupPath);
        }
        syncBackupRecords();
    }

    @Override
    public BackupRecord manualBackup() {
        return manualBackup(null);
    }

    @Override
    public BackupRecord manualBackup(Long parentBackupId) {
        backupLock.lock();
        File localBackupFile = null;
        try {
            String dbName = extractDatabaseName();
            validateDatabaseName(dbName);
            
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String backupName = "backup_" + dbName + "_" + timestamp + ".sql";
            String localPath = System.getProperty("java.io.tmpdir") + File.separator + backupName;

            log.info("开始手动备份: 数据库={}, 本地临时文件={}", dbName, localPath);
            executeMysqlDump(dbName, localPath);

            localBackupFile = new File(localPath);
            if (!localBackupFile.exists()) {
                throw new RuntimeException("备份文件创建失败: " + localPath);
            }
            
            long fileSize = localBackupFile.length();
            log.info("备份文件创建成功: {}, 大小: {} bytes", localPath, fileSize);

            String checksum = BackupUtils.calculateChecksum(localPath);
            log.info("备份文件校验和: {}", checksum);

            String tableList = extractTableListFromBackup(localPath);
            int dbTableCount = getDatabaseTableCount(dbName);
            log.info("数据库总表数: {}, 备份文件中表数: {}", dbTableCount, tableList.split(",").length);

            String finalBackupPath = localPath;
            Boolean isCompressed = false;
            
            if (compressBackup) {
                String compressedPath = BackupUtils.changeFileExtension(localPath, "sql.gz");
                BackupUtils.compressFile(localPath, compressedPath);
                
                File compressedFile = new File(compressedPath);
                if (compressedFile.exists()) {
                    BackupUtils.deleteFile(localPath);
                    finalBackupPath = compressedPath;
                    fileSize = compressedFile.length();
                    isCompressed = true;
                    log.info("备份文件压缩完成: {}, 压缩后大小: {} bytes", compressedPath, fileSize);
                }
            }

            log.info("开始上传备份文件到OSS");
            byte[] fileContent = Files.readAllBytes(Paths.get(finalBackupPath));
            String ossObjectName = "backups/" + backupName;
            String ossUrl = aliyunOSSOperator.uploadWithObjectName(fileContent, ossObjectName);
            log.info("备份文件上传到OSS成功: {}", ossUrl);

            BackupRecord record = new BackupRecord();
            record.setBackupName(backupName);
            record.setBackupPath(ossUrl);
            record.setBackupSize(fileSize);
            record.setBackupType("MANUAL");
            record.setBackupTime(new Date());
            record.setStatus(1);
            record.setRemark("手动备份");
            record.setBackupFormat(backupFormat);
            record.setIsCompressed(isCompressed);
            record.setChecksum(checksum);
            record.setParentBackupId(parentBackupId);
            record.setTableList(tableList);
            record.setTableCount(tableList.split(",").length);

            try {
                log.info("准备保存备份记录到数据库: {}", record);
                backupRecordMapper.insert(record);
                log.info("备份记录保存成功，ID: {}", record.getId());
            } catch (Exception e) {
                log.error("保存备份记录失败: {}", e.getMessage(), e);
                log.warn("备份文件已上传到OSS，但记录保存失败，OSS URL: {}", ossUrl);
            }

            log.info("手动备份成功: {}, OSS URL: {}, 文件大小: {} bytes", backupName, ossUrl, fileSize);
            return record;
        } catch (Exception e) {
            log.error("手动备份失败: {}", e.getMessage(), e);
            throw new RuntimeException("备份失败: " + e.getMessage(), e);
        } finally {
            backupLock.unlock();
            if (localBackupFile != null && localBackupFile.exists()) {
                try {
                    localBackupFile.delete();
                    log.info("删除本地临时备份文件: {}", localBackupFile.getAbsolutePath());
                } catch (Exception e) {
                    log.warn("删除本地临时备份文件失败: {}", e.getMessage());
                }
            }
        }
    }

    @Override
    public List<BackupRecord> getBackupRecords() {
        return backupRecordMapper.findAll();
    }

    @Override
    public void restoreBackup(Long id) {
        backupLock.lock();
        File localBackupFile = null;
        File localPreRestoreFile = null;
        try {
            BackupRecord record = backupRecordMapper.findById(id);
            if (record == null) {
                throw new RuntimeException("备份记录不存在");
            }

            log.info("从OSS下载备份文件: {}", record.getBackupPath());
            byte[] backupContent = aliyunOSSOperator.downloadFile(record.getBackupPath());
            
            String localBackupPath = System.getProperty("java.io.tmpdir") + File.separator + record.getBackupName();
            Files.write(Paths.get(localBackupPath), backupContent);
            localBackupFile = new File(localBackupPath);
            
            if (!localBackupFile.exists()) {
                throw new RuntimeException("备份文件下载失败");
            }

            String dbName = extractDatabaseName();
            validateDatabaseName(dbName);

            log.info("恢复前创建当前数据库的快照备份");
            localPreRestoreFile = createPreRestoreBackup(dbName);
            log.info("快照备份创建成功: {}", localPreRestoreFile.getAbsolutePath());

            log.info("备份新增表的数据和结构");
            Map<String, List<Map<String, Object>>> newTableData = backupNewTablesData(dbName, localBackupPath);
            Map<String, String> newTableStructures = backupNewTablesStructures(dbName, localBackupPath);

            log.info("备份现有表的新增字段结构");
            Map<String, List<Map<String, Object>>> newColumnsBackup = backupNewColumns(dbName, localBackupPath);

            try {
                executeMysqlRestore(dbName, localBackupPath);
                log.info("数据恢复成功，备份ID: {}, 备份文件: {}", id, record.getBackupName());
                waitForDatabaseRecovery(60);
                
                if (!newTableStructures.isEmpty()) {
                    log.info("重建新增的表结构");
                    recreateNewTables(dbName, newTableStructures);
                }
                
                if (!newTableData.isEmpty()) {
                    log.info("恢复新增表的数据");
                    restoreNewTablesData(dbName, newTableData);
                }

                if (!newColumnsBackup.isEmpty()) {
                    log.info("恢复现有表的新增字段");
                    restoreNewColumns(dbName, newColumnsBackup);
                }
                
                log.info("恢复后同步备份记录");
                syncBackupRecords();
            } catch (Exception e) {
                log.error("数据恢复失败，正在回滚到快照备份: {}", e.getMessage());
                if (localPreRestoreFile != null && localPreRestoreFile.exists()) {
                    executeMysqlRestore(dbName, localPreRestoreFile.getAbsolutePath());
                    log.info("已回滚到恢复前的状态");
                }
                throw new RuntimeException("数据恢复失败，已自动回滚: " + e.getMessage(), e);
            }

        } catch (Exception e) {
            log.error("数据恢复失败: {}", e.getMessage(), e);
            throw new RuntimeException("数据恢复失败: " + e.getMessage(), e);
        } finally {
            backupLock.unlock();
            if (localBackupFile != null && localBackupFile.exists()) {
                try {
                    localBackupFile.delete();
                    log.info("删除本地临时备份文件: {}", localBackupFile.getAbsolutePath());
                } catch (Exception e) {
                    log.warn("删除本地临时备份文件失败: {}", e.getMessage());
                }
            }
            if (localPreRestoreFile != null && localPreRestoreFile.exists()) {
                try {
                    localPreRestoreFile.delete();
                    log.info("删除本地临时快照文件: {}", localPreRestoreFile.getAbsolutePath());
                } catch (Exception e) {
                    log.warn("删除本地临时快照文件失败: {}", e.getMessage());
                }
            }
        }
    }

    private void waitForDatabaseRecovery(int maxSeconds) {
        log.info("等待数据库恢复连接...");
        int attempts = 0;
        int maxAttempts = maxSeconds * 2;

        while (attempts < maxAttempts) {
            try {
                Thread.sleep(500);
                Connection connection = dataSource.getConnection();
                boolean isValid = connection.isValid(2);
                connection.close();

                if (isValid) {
                    try {
                        jdbcTemplate.queryForObject("SELECT 1", Integer.class);
                        log.info("数据库连接已恢复");
                        return;
                    } catch (Exception e) {
                        log.debug("数据库查询测试失败，重试中... (尝试 {}/{})", attempts + 1, maxAttempts);
                    }
                }
            } catch (Exception e) {
                log.debug("数据库连接测试失败，重试中... (尝试 {}/{})", attempts + 1, maxAttempts);
            }
            attempts++;
        }

        log.warn("数据库连接在{}秒内未完全恢复，但数据已成功导入", maxSeconds);
    }

    @Override
    public File getBackupFile(Long id) {
        BackupRecord record = backupRecordMapper.findById(id);
        if (record == null) {
            return null;
        }
        return new File(record.getBackupPath());
    }

    @Override
    @Transactional
    public void deleteBackup(Long id) {
        try {
            BackupRecord record = backupRecordMapper.findById(id);
            if (record != null) {
                try {
                    log.info("从OSS删除备份文件: {}", record.getBackupPath());
                    aliyunOSSOperator.deleteFile(record.getBackupPath());
                    log.info("OSS备份文件删除成功");
                } catch (Exception e) {
                    log.warn("从OSS删除备份文件失败: {}", e.getMessage());
                }
                backupRecordMapper.deleteById(id);
                log.info("备份删除成功，备份ID: {}", id);
            }
        } catch (Exception e) {
            log.error("删除备份失败: {}", e.getMessage(), e);
            throw new RuntimeException("删除备份失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void updateBackupSchedule(Map<String, Object> schedule) {
        try {
            BackupSchedule existingSchedule = backupScheduleMapper.getSchedule();
            
            if (existingSchedule == null) {
                existingSchedule = new BackupSchedule();
            }
            
            if (schedule.containsKey("enabled")) {
                existingSchedule.setEnabled((Boolean) schedule.get("enabled"));
            }
            if (schedule.containsKey("frequency")) {
                existingSchedule.setFrequency((String) schedule.get("frequency"));
            }
            
            if (schedule.containsKey("time")) {
                String backupTime = (String) schedule.get("time");
                if (backupTime != null && !backupTime.isEmpty()) {
                    backupTime = backupTime.trim();
                    
                    if (backupTime.contains("T")) {
                        backupTime = backupTime.substring(backupTime.indexOf("T") + 1);
                    }
                    if (backupTime.contains(" ")) {
                        backupTime = backupTime.substring(backupTime.lastIndexOf(" ") + 1);
                    }
                    if (backupTime.contains("-") && !backupTime.contains(":")) {
                        backupTime = "02:00";
                    }
                    
                    if (backupTime.length() > 5) {
                        backupTime = backupTime.substring(0, 5);
                    }
                    
                    if (!backupTime.matches("^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$")) {
                        log.warn("备份时间格式不正确: {}, 使用默认值 02:00", backupTime);
                        backupTime = "02:00";
                    }
                } else {
                    backupTime = "02:00";
                }
                existingSchedule.setBackupTime(backupTime);
            }
            
            if (schedule.containsKey("retentionDays")) {
                Integer retentionDays = (Integer) schedule.get("retentionDays");
                if (retentionDays == null || retentionDays < 1) {
                    retentionDays = 30;
                }
                existingSchedule.setRetentionDays(retentionDays);
            }
            
            if (schedule.containsKey("remark")) {
                existingSchedule.setRemark((String) schedule.get("remark"));
            }
            
            backupScheduleMapper.updateSchedule(existingSchedule);
            log.info("自动备份策略更新成功: {}", schedule);
        } catch (Exception e) {
            log.error("更新自动备份策略失败: {}", e.getMessage(), e);
            throw new RuntimeException("更新自动备份策略失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> getBackupSchedule() {
        try {
            BackupSchedule schedule = backupScheduleMapper.getSchedule();
            if (schedule == null) {
                log.warn("未找到自动备份配置，返回默认值");
                Map<String, Object> defaultSchedule = new HashMap<>();
                defaultSchedule.put("enabled", false);
                defaultSchedule.put("frequency", "daily");
                defaultSchedule.put("time", "02:00");
                defaultSchedule.put("retentionDays", retentionDays);
                return defaultSchedule;
            }
            Map<String, Object> result = new HashMap<>();
            result.put("enabled", schedule.getEnabled());
            result.put("frequency", schedule.getFrequency());
            result.put("time", schedule.getBackupTime());
            result.put("retentionDays", schedule.getRetentionDays());
            result.put("remark", schedule.getRemark());
            if (schedule.getLastExecutionDate() != null) {
                result.put("lastExecutionDate", new SimpleDateFormat("yyyy-MM-dd").format(schedule.getLastExecutionDate()));
            } else {
                result.put("lastExecutionDate", null);
            }
            log.info("获取自动备份配置成功: enabled={}, frequency={}, time={}, lastExecutionDate={}", 
                schedule.getEnabled(), schedule.getFrequency(), schedule.getBackupTime(), 
                schedule.getLastExecutionDate() != null ? new SimpleDateFormat("yyyy-MM-dd").format(schedule.getLastExecutionDate()) : "null");
            return result;
        } catch (Exception e) {
            log.error("获取自动备份策略失败: {}", e.getMessage(), e);
            Map<String, Object> defaultSchedule = new HashMap<>();
            defaultSchedule.put("enabled", false);
            defaultSchedule.put("frequency", "daily");
            defaultSchedule.put("time", "02:00");
            defaultSchedule.put("retentionDays", retentionDays);
            return defaultSchedule;
        }
    }

    @Override
    @Scheduled(cron = "0 0/5 * * * ?")
    public void autoBackup() {
        BackupAuditLog auditLog = null;
        long startTime = System.currentTimeMillis();
        
        try {
            BackupSchedule schedule = backupScheduleMapper.getSchedule();
            if (schedule == null || !schedule.getEnabled()) {
                log.debug("自动备份未启用，跳过执行");
                return;
            }

            String currentTime = new SimpleDateFormat("HH:mm").format(new Date());
            String backupTime = schedule.getBackupTime();
            
            int currentMinutes = Integer.parseInt(currentTime.substring(0, 2)) * 60 + Integer.parseInt(currentTime.substring(3));
            int backupMinutes = Integer.parseInt(backupTime.substring(0, 2)) * 60 + Integer.parseInt(backupTime.substring(3));
            
            if (currentMinutes < backupMinutes) {
                log.debug("当前时间 {} 未达到备份时间 {}，跳过执行", currentTime, backupTime);
                return;
            }
            
            if (currentMinutes > backupMinutes + 5) {
                log.debug("当前时间 {} 已超过备份时间窗口 {}，跳过执行", currentTime, backupTime);
                return;
            }

            String frequency = schedule.getFrequency();
            String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
            String dayOfWeek = new SimpleDateFormat("u").format(new Date());
            String dayOfMonth = new SimpleDateFormat("dd").format(new Date());
            
            Date lastExecutionDate = schedule.getLastExecutionDate();
            String lastExecutionDateStr = null;
            if (lastExecutionDate != null) {
                lastExecutionDateStr = new SimpleDateFormat("yyyy-MM-dd").format(lastExecutionDate);
            }
            
            if (today.equals(lastExecutionDateStr)) {
                log.debug("今日已执行过自动备份，最后执行日期: {}, 跳过执行", lastExecutionDateStr);
                return;
            }
            
            boolean shouldBackup = false;
            if ("daily".equals(frequency)) {
                shouldBackup = true;
            } else if ("weekly".equals(frequency) && "1".equals(dayOfWeek)) {
                shouldBackup = true;
            } else if ("monthly".equals(frequency) && "01".equals(dayOfMonth)) {
                shouldBackup = true;
            }
            
            if (!shouldBackup) {
                log.debug("当前时间不符合备份频率要求，跳过执行");
                return;
            }

            log.info("开始执行自动备份，频率: {}, 备份时间: {}", frequency, backupTime);
            auditLog = backupAuditService.startAuditLog("AUTO_BACKUP", "自动备份 - " + frequency, "system", "127.0.0.1");
            
            String dbName = extractDatabaseName();
            validateDatabaseName(dbName);
            
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String backupName = "backup_" + dbName + "_" + timestamp + ".sql";
            String localPath = System.getProperty("java.io.tmpdir") + File.separator + backupName;

            log.info("开始自动备份: 数据库={}, 本地临时文件={}", dbName, localPath);
            executeMysqlDump(dbName, localPath);

            File backupFile = new File(localPath);
            if (!backupFile.exists()) {
                throw new RuntimeException("备份文件创建失败: " + localPath);
            }
            
            long fileSize = backupFile.length();
            log.info("备份文件创建成功: {}, 大小: {} bytes", localPath, fileSize);

            String checksum = BackupUtils.calculateChecksum(localPath);
            log.info("备份文件校验和: {}", checksum);

            String tableList = extractTableListFromBackup(localPath);
            int dbTableCount = getDatabaseTableCount(dbName);
            log.info("数据库总表数: {}, 备份文件中表数: {}", dbTableCount, tableList.split(",").length);

            String finalBackupPath = localPath;
            Boolean isCompressed = false;
            
            if (compressBackup) {
                String compressedPath = BackupUtils.changeFileExtension(localPath, "sql.gz");
                BackupUtils.compressFile(localPath, compressedPath);
                
                File compressedFile = new File(compressedPath);
                if (compressedFile.exists()) {
                    BackupUtils.deleteFile(localPath);
                    finalBackupPath = compressedPath;
                    fileSize = compressedFile.length();
                    isCompressed = true;
                    log.info("备份文件压缩完成: {}, 压缩后大小: {} bytes", compressedPath, fileSize);
                }
            }

            log.info("开始上传自动备份文件到OSS");
            byte[] fileContent = Files.readAllBytes(Paths.get(finalBackupPath));
            String ossObjectName = "backups/" + backupName;
            String ossUrl = aliyunOSSOperator.uploadWithObjectName(fileContent, ossObjectName);
            log.info("自动备份文件上传到OSS成功: {}", ossUrl);

            BackupRecord record = new BackupRecord();
            record.setBackupName(backupName);
            record.setBackupPath(ossUrl);
            record.setBackupSize(fileSize);
            record.setBackupType("AUTO");
            record.setBackupTime(new Date());
            record.setStatus(1);
            record.setRemark("自动备份 - " + frequency);
            record.setBackupFormat(backupFormat);
            record.setIsCompressed(isCompressed);
            record.setChecksum(checksum);
            record.setParentBackupId(null);
            record.setTableList(tableList);
            record.setTableCount(tableList.split(",").length);
            
            backupRecordMapper.insert(record);
            log.info("自动备份记录保存成功，ID: {}", record.getId());
            
            autoBackupExecutedToday = true;
            
            schedule.setLastExecutionDate(new Date());
            backupScheduleMapper.updateSchedule(schedule);
            log.info("更新自动备份最后执行日期: {}", new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
            
            long duration = System.currentTimeMillis() - startTime;
            backupAuditService.completeAuditLog(auditLog.getId(), 1, null, duration);
            log.info("自动备份成功，备份ID: {}, OSS URL: {}, 耗时: {}ms", record.getId(), ossUrl, duration);
            
            try {
                Files.deleteIfExists(Paths.get(finalBackupPath));
                log.info("删除本地临时自动备份文件: {}", finalBackupPath);
            } catch (Exception e) {
                log.warn("删除本地临时自动备份文件失败: {}", e.getMessage());
            }
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            String errorMsg = e.getMessage();
            log.error("自动备份失败: {}", errorMsg, e);
            if (auditLog != null) {
                backupAuditService.completeAuditLog(auditLog.getId(), 2, errorMsg, duration);
            }
        }
    }

    @Override
    @Scheduled(cron = "0 0 0 * * ?")
    public void resetAutoBackupFlag() {
        autoBackupExecutedToday = false;
        try {
            BackupSchedule schedule = backupScheduleMapper.getSchedule();
            if (schedule != null && schedule.getLastExecutionDate() != null) {
                schedule.setLastExecutionDate(null);
                backupScheduleMapper.updateSchedule(schedule);
                log.info("重置自动备份执行标志和最后执行日期，准备新的一天的备份");
            } else {
                log.info("重置自动备份执行标志，准备新的一天的备份");
            }
        } catch (Exception e) {
            log.error("重置自动备份最后执行日期失败: {}", e.getMessage(), e);
        }
    }

    @Override
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanExpiredBackups() {
        BackupAuditLog auditLog = null;
        long startTime = System.currentTimeMillis();
        
        try {
            BackupSchedule schedule = backupScheduleMapper.getSchedule();
            if (schedule == null) {
                return;
            }
            
            int retentionDays = schedule.getRetentionDays();
            if (retentionDays <= 0) {
                log.debug("备份保留天数设置为0，不清理过期备份");
                return;
            }
            
            Date expireDate = new Date(System.currentTimeMillis() - (long) retentionDays * 24 * 60 * 60 * 1000);
            List<BackupRecord> expiredRecords = backupRecordMapper.findExpiredRecords(expireDate);
            
            if (expiredRecords.isEmpty()) {
                log.debug("没有找到过期的备份记录");
                return;
            }
            
            log.info("开始清理过期备份，保留天数: {}, 过期日期: {}, 待清理数量: {}", 
                retentionDays, expireDate, expiredRecords.size());
            
            auditLog = backupAuditService.startAuditLog("CLEAN_EXPIRED", 
                "清理过期备份，保留天数: " + retentionDays + ", 待清理数量: " + expiredRecords.size(), 
                "system", "127.0.0.1");
            
            int successCount = 0;
            int failCount = 0;
            List<Long> deletedIds = new ArrayList<>();
            
            for (BackupRecord record : expiredRecords) {
                try {
                    File backupFile = new File(record.getBackupPath());
                    if (backupFile.exists()) {
                        boolean deleted = backupFile.delete();
                        if (!deleted) {
                            log.warn("备份文件删除失败: {}", record.getBackupPath());
                            failCount++;
                            continue;
                        }
                    }
                    deletedIds.add(record.getId());
                    successCount++;
                    log.info("过期备份删除成功: {}", record.getBackupName());
                } catch (Exception e) {
                    log.error("删除过期备份失败: {}, 错误: {}", record.getBackupName(), e.getMessage());
                    failCount++;
                }
            }
            
            if (!deletedIds.isEmpty()) {
                String ids = deletedIds.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
                backupRecordMapper.deleteByIds(ids);
            }
            
            long duration = System.currentTimeMillis() - startTime;
            String operationDetail = String.format("清理过期备份完成，成功: %d, 失败: %d", successCount, failCount);
            backupAuditService.completeAuditLog(auditLog.getId(), 1, null, duration);
            log.info("过期备份清理完成，成功: {}, 失败: {}, 耗时: {}ms", successCount, failCount, duration);
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            String errorMsg = e.getMessage();
            log.error("清理过期备份失败: {}", errorMsg, e);
            if (auditLog != null) {
                backupAuditService.completeAuditLog(auditLog.getId(), 2, errorMsg, duration);
            }
        }
    }

    private String extractDatabaseName() {
        try {
            String url = datasourceUrl;
            log.debug("原始数据源URL: {}", url);
            
            int paramStartIndex = url.indexOf("?");
            String dbPath = paramStartIndex != -1 ? url.substring(0, paramStartIndex) : url;
            
            int startIndex = dbPath.lastIndexOf("/");
            if (startIndex != -1 && startIndex < dbPath.length() - 1) {
                String dbName = dbPath.substring(startIndex + 1);
                
                if (dbName.contains("${") || dbName.contains("}")) {
                    log.warn("数据库名称包含未解析的占位符: {}, 使用默认值", dbName);
                    dbName = extractDefaultFromPlaceholder(dbName);
                }
                
                dbName = dbName.replaceAll("[^a-zA-Z0-9_-]", "");
                
                if (dbName.isEmpty()) {
                    log.warn("数据库名称为空，使用默认值: internship");
                    return "internship";
                }
                
                log.debug("提取的数据库名称: {}", dbName);
                return dbName;
            }
            log.warn("无法从URL提取数据库名称，使用默认值: internship");
            return "internship";
        } catch (Exception e) {
            log.error("提取数据库名称失败: {}", e.getMessage());
            return "internship";
        }
    }
    
    private String extractDefaultFromPlaceholder(String placeholder) {
        int colonIndex = placeholder.indexOf(":");
        int endIndex = placeholder.indexOf("}");
        if (colonIndex != -1 && endIndex != -1 && colonIndex < endIndex) {
            String defaultValue = placeholder.substring(colonIndex + 1, endIndex);
            log.debug("从占位符提取默认值: {}", defaultValue);
            return defaultValue;
        }
        return "internship";
    }

    private String extractDatabaseHost() {
        try {
            String url = datasourceUrl;
            log.debug("原始数据源URL: {}", url);
            
            String host = "localhost";
            
            if (url.contains("jdbc:mysql://")) {
                String cleanUrl = url.replace("jdbc:mysql://", "");
                
                int paramStartIndex = cleanUrl.indexOf("?");
                String hostPortDb = paramStartIndex != -1 ? cleanUrl.substring(0, paramStartIndex) : cleanUrl;
                
                int slashIndex = hostPortDb.indexOf("/");
                String hostPort = slashIndex != -1 ? hostPortDb.substring(0, slashIndex) : hostPortDb;
                
                if (hostPort.contains(":")) {
                    host = hostPort.substring(0, hostPort.indexOf(":"));
                } else {
                    host = hostPort;
                }
                
                if (host.contains("${") || host.contains("}")) {
                    log.warn("主机名包含未解析的占位符: {}, 使用默认值", host);
                    String defaultValue = extractDefaultFromPlaceholder(host);
                    if (!defaultValue.isEmpty()) {
                        host = defaultValue;
                    } else {
                        host = "localhost";
                    }
                }
            }
            
            log.debug("提取的数据库主机: {}", host);
            return host;
        } catch (Exception e) {
            log.error("提取数据库主机失败: {}", e.getMessage());
            return "localhost";
        }
    }

    private String extractDatabasePort() {
        try {
            String url = datasourceUrl;
            log.debug("原始数据源URL: {}", url);
            
            String port = "3306";
            
            if (url.contains("jdbc:mysql://")) {
                String cleanUrl = url.replace("jdbc:mysql://", "");
                
                int paramStartIndex = cleanUrl.indexOf("?");
                String hostPortDb = paramStartIndex != -1 ? cleanUrl.substring(0, paramStartIndex) : cleanUrl;
                
                int slashIndex = hostPortDb.indexOf("/");
                String hostPort = slashIndex != -1 ? hostPortDb.substring(0, slashIndex) : hostPortDb;
                
                if (hostPort.contains(":")) {
                    port = hostPort.substring(hostPort.indexOf(":") + 1);
                }
                
                if (port.contains("${") || port.contains("}")) {
                    log.warn("端口包含未解析的占位符: {}, 使用默认值", port);
                    String defaultValue = extractDefaultFromPlaceholder(port);
                    if (!defaultValue.isEmpty()) {
                        port = defaultValue;
                    } else {
                        port = "3306";
                    }
                }
            }
            
            log.debug("提取的数据库端口: {}", port);
            return port;
        } catch (Exception e) {
            log.error("提取数据库端口失败: {}", e.getMessage());
            return "3306";
        }
    }

    private void validateDatabaseName(String dbName) {
        if (dbName == null || dbName.isEmpty()) {
            throw new RuntimeException("数据库名称不能为空");
        }
        if (!DB_NAME_PATTERN.matcher(dbName).matches()) {
            throw new RuntimeException("数据库名称格式不正确，只能包含字母、数字、下划线和连字符，长度1-64位");
        }
    }

    private void validateBackupPath(String path) {
        if (path == null || path.isEmpty()) {
            throw new RuntimeException("备份路径不能为空");
        }
        if (!PATH_PATTERN.matcher(path).matches()) {
            throw new RuntimeException("备份路径格式不正确");
        }
        try {
            Path normalizedPath = Paths.get(path).normalize();
            if (!normalizedPath.startsWith(Paths.get(path).getRoot())) {
                throw new RuntimeException("备份路径不能包含路径遍历字符");
            }
        } catch (Exception e) {
            throw new RuntimeException("备份路径验证失败: " + e.getMessage());
        }
    }

    private File createTempConfigFile() throws Exception {
        File tempFile = File.createTempFile("mysql-config-", ".cnf");
        tempFile.deleteOnExit();
        
        String host = extractDatabaseHost();
        String port = extractDatabasePort();
        
        log.info("创建MySQL配置文件，主机: {}, 端口: {}", host, port);
        
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("[client]\n");
            writer.write("user=" + datasourceUsername + "\n");
            writer.write("password=" + datasourcePassword + "\n");
            writer.write("host=" + host + "\n");
            writer.write("port=" + port + "\n");
        }
        
        tempFile.setReadable(true, true);
        return tempFile;
    }

    private File createPreRestoreBackup(String dbName) throws Exception {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String backupName = "prerestore_" + dbName + "_" + timestamp + ".sql";
        String localPath = System.getProperty("java.io.tmpdir") + File.separator + backupName;
        
        log.info("创建恢复前快照备份: {}", localPath);
        executeMysqlDump(dbName, localPath);
        
        File backupFile = new File(localPath);
        if (!backupFile.exists()) {
            throw new RuntimeException("快照备份文件创建失败: " + localPath);
        }
        
        return backupFile;
    }

    private void executeMysqlDump(String dbName, String outputPath) throws Exception {
        log.info("准备执行mysqldump命令，数据库: {}, 输出文件: {}", dbName, outputPath);
        
        File configFile = createTempConfigFile();
        
        String[] command = {
            "mysqldump",
            "--defaults-file=" + configFile.getAbsolutePath(),
            dbName,
            "--result-file=" + outputPath,
            "--default-character-set=utf8mb4",
            "--single-transaction",
            "--quick",
            "--lock-tables=false",
            "--routines",
            "--triggers",
            "--events",
            "--add-drop-table",
            "--complete-insert",
            "--extended-insert",
            "--set-charset"
        };

        log.info("执行mysqldump命令: mysqldump --defaults-file=*** {} --result-file={} --default-character-set=utf8mb4 --single-transaction --quick --lock-tables=false --routines --triggers --events --add-drop-table --complete-insert --extended-insert --set-charset",
            dbName, outputPath);

        Process process = Runtime.getRuntime().exec(command);
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        String line;
        StringBuilder error = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            error.append(line).append("\n");
            log.debug("mysqldump错误输出: {}", line);
        }
        
        int exitCode = process.waitFor();
        log.info("mysqldump命令执行完成，退出码: {}", exitCode);
        
        try {
            List<String> backedUpTables = extractTableListFromBackupFile(outputPath);
            log.info("备份文件 {} 中共包含 {} 个表", outputPath, backedUpTables.size());
        } catch (Exception e) {
            log.warn("统计备份表数量失败: {}", e.getMessage());
        }

        if (exitCode != 0) {
            String errorMsg = error.toString();
            log.error("mysqldump执行失败，退出码: {}, 错误信息: {}", exitCode, errorMsg);
            throw new RuntimeException("mysqldump执行失败，退出码: " + exitCode + ", 错误信息: " + errorMsg);
        }

        File outputFile = new File(outputPath);
        if (!outputFile.exists() || outputFile.length() == 0) {
            throw new RuntimeException("备份文件创建失败或文件为空");
        }
    }

    private void executeMysqlRestore(String dbName, String inputPath) throws Exception {
        log.info("准备执行mysql恢复命令，数据库: {}, 备份文件: {}", dbName, inputPath);
        
        File configFile = createTempConfigFile();
        
        String[] command = {
            "mysql",
            "--defaults-file=" + configFile.getAbsolutePath(),
            dbName,
            "--force",
            "--verbose"
        };

        log.info("执行mysql命令: mysql --defaults-file=*** {} --force --verbose < {}", 
            dbName, inputPath);

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectInput(new File(inputPath));
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        StringBuilder output = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
            log.debug("mysql恢复输出: {}", line);
        }

        int exitCode = process.waitFor();
        log.info("mysql恢复命令执行完成，退出码: {}", exitCode);

        if (exitCode != 0) {
            String error = output.toString();
            log.error("mysql恢复失败，退出码: {}, 输出: {}", exitCode, error);
            throw new RuntimeException("mysql恢复失败，退出码: " + exitCode + ", 错误信息: " + error);
        }

        log.info("数据恢复成功");
    }

    @Override
    public BackupRecord incrementalBackup(Long parentBackupId) {
        backupLock.lock();
        File localBackupFile = null;
        try {
            BackupRecord parentRecord = backupRecordMapper.findById(parentBackupId);
            if (parentRecord == null) {
                throw new RuntimeException("父备份记录不存在");
            }

            String parentBackupPath = parentRecord.getBackupPath();
            if (parentRecord.getIsCompressed()) {
                String decompressedPath = BackupUtils.changeFileExtension(parentBackupPath, "sql");
                BackupUtils.decompressFile(parentBackupPath, decompressedPath);
                parentBackupPath = decompressedPath;
            }

            log.info("开始增量备份，父备份ID: {}, 父备份路径: {}", parentBackupId, parentBackupPath);

            String dbName = extractDatabaseName();
            validateDatabaseName(dbName);
            
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String backupName = "incremental_" + dbName + "_" + timestamp + ".sql";
            String localPath = System.getProperty("java.io.tmpdir") + File.separator + backupName;

            executeMysqlDump(dbName, localPath);

            localBackupFile = new File(localPath);
            if (!localBackupFile.exists()) {
                throw new RuntimeException("增量备份文件创建失败: " + localPath);
            }
            
            long fileSize = localBackupFile.length();
            log.info("增量备份文件创建成功: {}, 大小: {} bytes", localPath, fileSize);

            String checksum = BackupUtils.calculateChecksum(localPath);
            log.info("增量备份文件校验和: {}", checksum);

            String tableList = extractTableListFromBackup(localPath);
            int dbTableCount = getDatabaseTableCount(dbName);
            log.info("数据库总表数: {}, 备份文件中表数: {}", dbTableCount, tableList.split(",").length);

            String finalBackupPath = localPath;
            Boolean isCompressed = false;
            
            if (compressBackup) {
                String compressedPath = BackupUtils.changeFileExtension(localPath, "sql.gz");
                BackupUtils.compressFile(localPath, compressedPath);
                
                File compressedFile = new File(compressedPath);
                if (compressedFile.exists()) {
                    BackupUtils.deleteFile(localPath);
                    finalBackupPath = compressedPath;
                    fileSize = compressedFile.length();
                    isCompressed = true;
                    log.info("增量备份文件压缩完成: {}, 压缩后大小: {} bytes", compressedPath, fileSize);
                }
            }

            log.info("开始上传增量备份文件到OSS");
            byte[] fileContent = Files.readAllBytes(Paths.get(finalBackupPath));
            String ossObjectName = "backups/" + backupName;
            String ossUrl = aliyunOSSOperator.uploadWithObjectName(fileContent, ossObjectName);
            log.info("增量备份文件上传到OSS成功: {}", ossUrl);

            BackupRecord record = new BackupRecord();
            record.setBackupName(backupName);
            record.setBackupPath(ossUrl);
            record.setBackupSize(fileSize);
            record.setBackupType("INCREMENTAL");
            record.setBackupTime(new Date());
            record.setStatus(1);
            record.setRemark("增量备份，基于备份ID: " + parentBackupId);
            record.setBackupFormat(backupFormat);
            record.setIsCompressed(isCompressed);
            record.setChecksum(checksum);
            record.setParentBackupId(parentBackupId);
            record.setTableList(tableList);
            record.setTableCount(tableList.split(",").length);

            try {
                backupRecordMapper.insert(record);
                log.info("增量备份记录保存成功，ID: {}", record.getId());
            } catch (Exception e) {
                log.error("保存增量备份记录失败: {}", e.getMessage(), e);
            }

            log.info("增量备份成功: {}, OSS URL: {}, 文件大小: {} bytes", backupName, ossUrl, fileSize);
            return record;
        } catch (Exception e) {
            log.error("增量备份失败: {}", e.getMessage(), e);
            throw new RuntimeException("增量备份失败: " + e.getMessage(), e);
        } finally {
            backupLock.unlock();
            if (localBackupFile != null && localBackupFile.exists()) {
                try {
                    localBackupFile.delete();
                    log.info("删除本地临时增量备份文件: {}", localBackupFile.getAbsolutePath());
                } catch (Exception e) {
                    log.warn("删除本地临时增量备份文件失败: {}", e.getMessage());
                }
            }
        }
    }

    @Override
    public boolean verifyBackup(Long id) {
        try {
            BackupRecord record = backupRecordMapper.findById(id);
            if (record == null) {
                log.error("备份记录不存在，ID: {}", id);
                return false;
            }

            String backupPath = record.getBackupPath();
            File backupFile = new File(backupPath);
            
            if (!backupFile.exists()) {
                log.error("备份文件不存在: {}", backupPath);
                return false;
            }

            if (backupFile.length() == 0) {
                log.error("备份文件为空: {}", backupPath);
                return false;
            }

            if (record.getChecksum() != null && !record.getChecksum().isEmpty()) {
                boolean isValid = BackupUtils.verifyChecksum(backupPath, record.getChecksum());
                if (!isValid) {
                    log.error("备份文件校验失败，校验和不匹配");
                    return false;
                }
            }

            log.info("备份文件验证成功，ID: {}, 路径: {}", id, backupPath);
            return true;
        } catch (Exception e) {
            log.error("验证备份失败: {}", e.getMessage(), e);
            return false;
        }
    }

    private String extractTableListFromBackup(String backupFilePath) throws Exception {
        try (BufferedReader reader = new BufferedReader(new java.io.FileReader(backupFilePath))) {
            String line;
            Set<String> tables = new LinkedHashSet<>();
            java.util.regex.Pattern tablePattern = java.util.regex.Pattern.compile("CREATE TABLE `([^`]+)`");
            
            while ((line = reader.readLine()) != null) {
                java.util.regex.Matcher matcher = tablePattern.matcher(line);
                if (matcher.find()) {
                    String tableName = matcher.group(1);
                    tables.add(tableName);
                }
            }
            
            String tableList = String.join(",", tables);
            log.info("从备份文件中提取到 {} 个表: {}", tables.size(), tableList);
            return tableList;
        }
    }

    private int getDatabaseTableCount(String dbName) throws Exception {
        try {
            String sql = "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = ?";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, dbName);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.error("获取数据库表数量失败: {}", e.getMessage());
            return 0;
        }
    }

    private List<String> getDatabaseTables(String dbName) throws Exception {
        try {
            String sql = "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? ORDER BY TABLE_NAME";
            List<String> tables = jdbcTemplate.queryForList(sql, String.class, dbName);
            return tables;
        } catch (Exception e) {
            log.error("获取数据库表列表失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private Map<String, String> backupNewTablesStructures(String dbName, String backupFilePath) {
        Map<String, String> newTableStructures = new HashMap<>();
        try {
            List<String> currentTables = getDatabaseTables(dbName);
            Set<String> currentTableSet = new HashSet<>(currentTables);
            
            List<String> backupTables = extractTableListFromBackupFile(backupFilePath);
            Set<String> backupTableSet = new HashSet<>(backupTables);
            
            Set<String> newTables = new HashSet<>(currentTableSet);
            newTables.removeAll(backupTableSet);
            
            if (newTables.isEmpty()) {
                log.info("没有新增的表需要备份结构");
                return newTableStructures;
            }
            
            log.info("发现 {} 个新增的表需要备份结构: {}", newTables.size(), newTables);
            
            for (String tableName : newTables) {
                try {
                    String createSql = getTableCreateStatement(tableName);
                    if (createSql != null) {
                        newTableStructures.put(tableName, createSql);
                        log.info("表 {} 的结构已备份", tableName);
                    }
                } catch (Exception e) {
                    log.warn("表 {} 结构备份失败: {}", tableName, e.getMessage());
                }
            }
            
        } catch (Exception e) {
            log.error("备份新增表结构失败: {}", e.getMessage());
        }
        return newTableStructures;
    }

    private String getTableCreateStatement(String tableName) {
        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList("SHOW CREATE TABLE " + tableName);
            if (!results.isEmpty()) {
                Map<String, Object> result = results.get(0);
                for (Object value : result.values()) {
                    if (value != null && value.toString().startsWith("CREATE TABLE")) {
                        return value.toString();
                    }
                }
            }
        } catch (Exception e) {
            log.error("获取表 {} 创建语句失败: {}", tableName, e.getMessage());
        }
        return null;
    }

    private void recreateNewTables(String dbName, Map<String, String> newTableStructures) {
        if (newTableStructures == null || newTableStructures.isEmpty()) {
            return;
        }
        
        for (Map.Entry<String, String> entry : newTableStructures.entrySet()) {
            String tableName = entry.getKey();
            String createSql = entry.getValue();
            
            try {
                String checkSql = "SHOW TABLES LIKE '" + tableName + "'";
                List<String> tables = jdbcTemplate.queryForList(checkSql, String.class);
                
                if (tables.isEmpty()) {
                    jdbcTemplate.execute(createSql);
                    log.info("表 {} 已重建", tableName);
                } else {
                    log.info("表 {} 已存在，跳过重建", tableName);
                }
            } catch (Exception e) {
                log.error("表 {} 重建失败: {}", tableName, e.getMessage());
            }
        }
    }

    private Map<String, List<Map<String, Object>>> backupNewTablesData(String dbName, String backupFilePath) {
        Map<String, List<Map<String, Object>>> newTableData = new HashMap<>();
        try {
            List<String> currentTables = getDatabaseTables(dbName);
            Set<String> currentTableSet = new HashSet<>(currentTables);
            
            List<String> backupTables = extractTableListFromBackupFile(backupFilePath);
            Set<String> backupTableSet = new HashSet<>(backupTables);
            
            Set<String> newTables = new HashSet<>(currentTableSet);
            newTables.removeAll(backupTableSet);
            
            if (newTables.isEmpty()) {
                log.info("没有新增的表需要备份");
                return newTableData;
            }
            
            log.info("发现 {} 个新增的表: {}", newTables.size(), newTables);
            
            for (String tableName : newTables) {
                try {
                    List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM " + tableName);
                    if (!rows.isEmpty()) {
                        newTableData.put(tableName, rows);
                        log.info("表 {} 的数据已备份，共 {} 条记录", tableName, rows.size());
                    } else {
                        log.info("表 {} 没有数据需要备份", tableName);
                    }
                } catch (Exception e) {
                    log.warn("表 {} 数据备份失败: {}", tableName, e.getMessage());
                }
            }
            
        } catch (Exception e) {
            log.error("备份新增表数据失败: {}", e.getMessage());
        }
        return newTableData;
    }

    private void restoreNewTablesData(String dbName, Map<String, List<Map<String, Object>>> newTableData) {
        if (newTableData == null || newTableData.isEmpty()) {
            return;
        }
        
        for (Map.Entry<String, List<Map<String, Object>>> entry : newTableData.entrySet()) {
            String tableName = entry.getKey();
            List<Map<String, Object>> rows = entry.getValue();
            
            if (rows.isEmpty()) {
                continue;
            }
            
            try {
                List<Map<String, Object>> currentRows = jdbcTemplate.queryForList("SELECT COUNT(*) as cnt FROM " + tableName);
                int currentCount = ((Number) currentRows.get(0).get("cnt")).intValue();
                
                if (currentCount == 0) {
                    for (Map<String, Object> row : rows) {
                        insertTableRow(tableName, row);
                    }
                    log.info("表 {} 的数据已恢复，共 {} 条记录", tableName, rows.size());
                } else {
                    log.info("表 {} 已有数据 {} 条，跳过恢复", tableName, currentCount);
                }
            } catch (Exception e) {
                log.warn("表 {} 数据恢复失败: {}", tableName, e.getMessage());
            }
        }
    }

    private void insertTableRow(String tableName, Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return;
        }
        
        StringBuilder columns = new StringBuilder();
        StringBuilder values = new StringBuilder();
        List<Object> params = new ArrayList<>();
        
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (columns.length() > 0) {
                columns.append(", ");
                values.append(", ");
            }
            columns.append("`").append(entry.getKey()).append("`");
            values.append("?");
            params.add(entry.getValue());
        }
        
        String sql = "INSERT INTO `" + tableName + "` (" + columns.toString() + ") VALUES (" + values.toString() + ")";
        jdbcTemplate.update(sql, params.toArray());
    }

    private List<String> extractTableListFromBackupFile(String backupFilePath) throws Exception {
        List<String> tables = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new java.io.FileReader(backupFilePath))) {
            String line;
            Pattern tablePattern = Pattern.compile("CREATE TABLE `([^`]+)`");
            
            while ((line = reader.readLine()) != null) {
                Matcher matcher = tablePattern.matcher(line);
                if (matcher.find()) {
                    tables.add(matcher.group(1));
                }
            }
        }
        return tables;
    }

    private Map<String, List<Map<String, Object>>> backupNewColumns(String dbName, String backupFilePath) {
        Map<String, List<Map<String, Object>>> newColumnsMap = new HashMap<>();
        try {
            List<String> currentTables = getDatabaseTables(dbName);
            List<String> backupTables = extractTableListFromBackupFile(backupFilePath);
            
            Set<String> commonTables = new HashSet<>(currentTables);
            commonTables.retainAll(new HashSet<>(backupTables));
            
            if (commonTables.isEmpty()) {
                log.info("没有共同的表需要检查新增字段");
                return newColumnsMap;
            }
            
            log.info("检查 {} 个共同表的新增字段", commonTables.size());
            
            for (String tableName : commonTables) {
                try {
                    List<Map<String, Object>> currentColumns = getTableColumns(tableName);
                    List<String> backupColumns = extractColumnsFromBackupFile(backupFilePath, tableName);
                    
                    List<Map<String, Object>> newColumns = new ArrayList<>();
                    for (Map<String, Object> column : currentColumns) {
                        String columnName = (String) column.get("Field");
                        if (!backupColumns.contains(columnName)) {
                            newColumns.add(column);
                            log.info("发现表 {} 的新增字段: {}", tableName, columnName);
                        }
                    }
                    
                    if (!newColumns.isEmpty()) {
                        newColumnsMap.put(tableName, newColumns);
                        log.info("表 {} 有 {} 个新增字段需要备份", tableName, newColumns.size());
                    }
                } catch (Exception e) {
                    log.warn("检查表 {} 的新增字段失败: {}", tableName, e.getMessage());
                }
            }
            
        } catch (Exception e) {
            log.error("备份新增字段失败: {}", e.getMessage());
        }
        return newColumnsMap;
    }

    private List<Map<String, Object>> getTableColumns(String tableName) {
        try {
            return jdbcTemplate.queryForList("SHOW COLUMNS FROM " + tableName);
        } catch (Exception e) {
            log.error("获取表 {} 的列信息失败: {}", tableName, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<String> extractColumnsFromBackupFile(String backupFilePath, String tableName) throws Exception {
        List<String> columns = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new java.io.FileReader(backupFilePath))) {
            String line;
            boolean inTable = false;
            Pattern tableStartPattern = Pattern.compile("CREATE TABLE `" + Pattern.quote(tableName) + "`");
            Pattern columnPattern = Pattern.compile("^\\s+`([^`]+)`\\s+");
            
            while ((line = reader.readLine()) != null) {
                if (tableStartPattern.matcher(line).find()) {
                    inTable = true;
                    continue;
                }
                
                if (inTable) {
                    if (line.contains(") ENGINE=") || line.trim().startsWith(")")) {
                        break;
                    }
                    
                    Matcher columnMatcher = columnPattern.matcher(line);
                    if (columnMatcher.find()) {
                        columns.add(columnMatcher.group(1));
                    }
                }
            }
        }
        return columns;
    }

    private void restoreNewColumns(String dbName, Map<String, List<Map<String, Object>>> newColumnsBackup) {
        if (newColumnsBackup == null || newColumnsBackup.isEmpty()) {
            return;
        }
        
        for (Map.Entry<String, List<Map<String, Object>>> entry : newColumnsBackup.entrySet()) {
            String tableName = entry.getKey();
            List<Map<String, Object>> columns = entry.getValue();
            
            for (Map<String, Object> column : columns) {
                try {
                    String columnName = (String) column.get("Field");
                    String columnType = (String) column.get("Type");
                    String isNullable = (String) column.get("Null");
                    Object defaultValue = column.get("Default");
                    String extra = (String) column.get("Extra");
                    
                    List<String> existingColumns = jdbcTemplate.queryForList(
                        "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                        String.class, dbName, tableName, columnName);
                    
                    if (existingColumns.isEmpty()) {
                        StringBuilder alterSql = new StringBuilder();
                        alterSql.append("ALTER TABLE `").append(tableName).append("` ADD COLUMN `")
                                .append(columnName).append("` ").append(columnType);
                        
                        if ("NO".equalsIgnoreCase(isNullable)) {
                            alterSql.append(" NOT NULL");
                        } else {
                            alterSql.append(" NULL");
                        }
                        
                        if (defaultValue != null) {
                            if (defaultValue instanceof String) {
                                alterSql.append(" DEFAULT '").append(defaultValue).append("'");
                            } else {
                                alterSql.append(" DEFAULT ").append(defaultValue);
                            }
                        }
                        
                        if (extra != null && !extra.isEmpty()) {
                            alterSql.append(" ").append(extra);
                        }
                        
                        jdbcTemplate.execute(alterSql.toString());
                        log.info("表 {} 已恢复新增字段: {}", tableName, columnName);
                    } else {
                        log.info("表 {} 的字段 {} 已存在，跳过恢复", tableName, columnName);
                    }
                } catch (Exception e) {
                    log.warn("表 {} 恢复新增字段失败: {}", tableName, e.getMessage());
                }
            }
        }
    }

    private void syncBackupRecords() {
        try {
            log.info("备份文件存储在OSS上，跳过本地文件系统同步");
            return;
        } catch (Exception e) {
            log.error("同步备份记录失败: {}", e.getMessage(), e);
        }
    }
}
