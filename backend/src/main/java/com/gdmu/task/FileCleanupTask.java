package com.gdmu.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class FileCleanupTask {
    
    @Value("${backup.path:D:/Web-work/Internship/backups}")
    private String backupPath;
    
    @Value("${file.upload.path:D:/Web-work/Internship/uploads}")
    private String uploadPath;
    
    @Value("${file.retention.days:30}")
    private int defaultRetentionDays;
    
    private static final List<String> EXCLUDED_DIRECTORIES = Arrays.asList(
        "backups",
        "backup",
        "temp",
        "tmp"
    );
    
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanExpiredFiles() {
        log.info("开始清理过期文件");
        
        try {
            String storagePath = uploadPath;
            int retentionDays = defaultRetentionDays;
            
            File storageDir = new File(storagePath);
            if (!storageDir.exists() || !storageDir.isDirectory()) {
                log.warn("存储路径不存在或不是目录: {}", storagePath);
                return;
            }
            
            cleanDirectory(storageDir, retentionDays);
            
            log.info("过期文件清理完成");
        } catch (Exception e) {
            log.error("清理过期文件失败: {}", e.getMessage(), e);
        }
    }
    
    private void cleanDirectory(File directory, int retentionDays) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        
        for (File file : files) {
            if (file.isDirectory()) {
                if (shouldExcludeDirectory(file)) {
                    log.debug("跳过排除的目录: {}", file.getAbsolutePath());
                    continue;
                }
                cleanDirectory(file, retentionDays);
            } else {
                if (isFileExpired(file, retentionDays)) {
                    try {
                        Files.delete(file.toPath());
                        log.info("已删除过期文件: {}", file.getAbsolutePath());
                    } catch (IOException e) {
                        log.error("删除文件失败: {}", file.getAbsolutePath(), e);
                    }
                }
            }
        }
    }
    
    private boolean shouldExcludeDirectory(File directory) {
        String dirName = directory.getName().toLowerCase();
        String absolutePath = directory.getAbsolutePath().toLowerCase();
        
        if (EXCLUDED_DIRECTORIES.contains(dirName)) {
            return true;
        }
        
        if (absolutePath.contains("backups") || absolutePath.contains("backup")) {
            return true;
        }
        
        if (backupPath != null && absolutePath.startsWith(backupPath.toLowerCase())) {
            return true;
        }
        
        return false;
    }
    
    private boolean isFileExpired(File file, int retentionDays) {
        try {
            Path path = file.toPath();
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            
            LocalDateTime fileTime = attrs.lastModifiedTime()
                    .toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
            
            LocalDateTime expireTime = LocalDateTime.now().minusDays(retentionDays);
            
            return fileTime.isBefore(expireTime);
        } catch (IOException e) {
            log.error("读取文件属性失败: {}", file.getAbsolutePath(), e);
            return false;
        }
    }
}
