package com.gdmu.service;

import com.gdmu.entity.BackupRecord;

import java.io.File;
import java.util.List;
import java.util.Map;

public interface BackupService {
    
    BackupRecord manualBackup();
    
    BackupRecord manualBackup(Long parentBackupId);
    
    List<BackupRecord> getBackupRecords();
    
    void restoreBackup(Long id);
    
    File getBackupFile(Long id);
    
    void deleteBackup(Long id);
    
    void updateBackupSchedule(Map<String, Object> schedule);
    
    Map<String, Object> getBackupSchedule();
    
    void autoBackup();
    
    void resetAutoBackupFlag();
    
    void cleanExpiredBackups();
    
    BackupRecord incrementalBackup(Long parentBackupId);
    
    boolean verifyBackup(Long id);
}
