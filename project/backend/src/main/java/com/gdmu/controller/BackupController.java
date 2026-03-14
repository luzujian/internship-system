package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.entity.Result;
import com.gdmu.entity.BackupRecord;
import com.gdmu.entity.BackupAuditLog;
import com.gdmu.service.BackupService;
import com.gdmu.service.BackupAuditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.io.File;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/backup")
public class BackupController {

    @Autowired
    private BackupService backupService;

    @Autowired
    private BackupAuditService backupAuditService;

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "system";
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    @PostMapping("/manual")
    @PreAuthorize("hasAuthority('backup:create')")
    @Log(module = "BACKUP", operationType = "BACKUP", description = "手动备份数据")
    public Result manualBackup(HttpServletRequest request) {
        String operator = getCurrentUsername();
        String operatorIp = getClientIp(request);
        BackupAuditLog auditLog = backupAuditService.startAuditLog("BACKUP", "手动备份数据", operator, operatorIp);
        long startTime = System.currentTimeMillis();
        
        log.info("开始手动备份数据");
        try {
            BackupRecord record = backupService.manualBackup();
            long duration = System.currentTimeMillis() - startTime;
            backupAuditService.completeAuditLog(auditLog.getId(), 1, null, duration);
            return Result.success("备份成功", record);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            String errorMsg = e.getMessage();
            log.error("手动备份失败: {}", errorMsg, e);
            backupAuditService.completeAuditLog(auditLog.getId(), 2, errorMsg, duration);
            return Result.error("备份失败: " + errorMsg);
        }
    }

    @GetMapping("/records")
    @PreAuthorize("hasAuthority('backup:view')")
    public Result getBackupRecords() {
        log.info("获取备份记录列表");
        try {
            List<BackupRecord> records = backupService.getBackupRecords();
            return Result.success(records);
        } catch (Exception e) {
            log.error("获取备份记录失败: {}", e.getMessage());
            return Result.error("获取备份记录失败");
        }
    }

    @PostMapping("/restore/{id}")
    @PreAuthorize("hasAuthority('backup:restore')")
    @Log(module = "BACKUP", operationType = "RESTORE", description = "恢复数据")
    public Result restoreBackup(@PathVariable Long id, HttpServletRequest request) {
        String operator = getCurrentUsername();
        String operatorIp = getClientIp(request);
        BackupAuditLog auditLog = backupAuditService.startAuditLog("RESTORE", "恢复数据，备份ID: " + id, operator, operatorIp);
        long startTime = System.currentTimeMillis();
        
        log.info("开始恢复数据，备份ID: {}", id);
        try {
            backupService.restoreBackup(id);
            long duration = System.currentTimeMillis() - startTime;
            backupAuditService.completeAuditLog(auditLog.getId(), 1, null, duration);
            return Result.success("数据恢复成功");
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            String errorMsg = e.getMessage();
            log.error("数据恢复失败: {}", errorMsg, e);
            backupAuditService.completeAuditLog(auditLog.getId(), 2, errorMsg, duration);
            return Result.error("数据恢复失败: " + errorMsg);
        }
    }

    @GetMapping("/download/{id}")
    @PreAuthorize("hasAuthority('backup:view')")
    @Log(module = "BACKUP", operationType = "DOWNLOAD", description = "下载备份文件")
    public ResponseEntity<Resource> downloadBackup(@PathVariable Long id) {
        log.info("下载备份文件，备份ID: {}", id);
        try {
            File file = backupService.getBackupFile(id);
            if (file == null || !file.exists()) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new org.springframework.core.io.FileSystemResource(file);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getName() + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(file.length())
                    .body(resource);
        } catch (Exception e) {
            log.error("下载备份文件失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('backup:delete')")
    @Log(module = "BACKUP", operationType = "DELETE", description = "删除备份")
    public Result deleteBackup(@PathVariable Long id) {
        log.info("删除备份，备份ID: {}", id);
        try {
            backupService.deleteBackup(id);
            return Result.success("备份删除成功");
        } catch (Exception e) {
            log.error("删除备份失败: {}", e.getMessage());
            return Result.error("删除备份失败");
        }
    }

    @PutMapping("/schedule")
    @PreAuthorize("hasAuthority('backup:create')")
    @Log(module = "BACKUP", operationType = "UPDATE", description = "更新自动备份策略")
    public Result updateBackupSchedule(@RequestBody Map<String, Object> schedule) {
        log.info("更新自动备份策略");
        try {
            backupService.updateBackupSchedule(schedule);
            return Result.success("自动备份策略更新成功");
        } catch (Exception e) {
            log.error("更新自动备份策略失败: {}", e.getMessage());
            return Result.error("更新自动备份策略失败");
        }
    }

    @GetMapping("/schedule")
    @PreAuthorize("hasAuthority('backup:view')")
    public Result getBackupSchedule() {
        log.info("获取自动备份策略");
        try {
            Map<String, Object> schedule = backupService.getBackupSchedule();
            return Result.success(schedule);
        } catch (Exception e) {
            log.error("获取自动备份策略失败: {}", e.getMessage());
            return Result.error("获取自动备份策略失败");
        }
    }

    @PostMapping("/incremental/{parentBackupId}")
    @PreAuthorize("hasAuthority('backup:create')")
    @Log(module = "BACKUP", operationType = "BACKUP", description = "增量备份")
    public Result incrementalBackup(@PathVariable Long parentBackupId, HttpServletRequest request) {
        String operator = getCurrentUsername();
        String operatorIp = getClientIp(request);
        BackupAuditLog auditLog = backupAuditService.startAuditLog("INCREMENTAL_BACKUP", 
            "增量备份，父备份ID: " + parentBackupId, operator, operatorIp);
        long startTime = System.currentTimeMillis();
        
        log.info("开始增量备份，父备份ID: {}", parentBackupId);
        try {
            BackupRecord record = backupService.incrementalBackup(parentBackupId);
            long duration = System.currentTimeMillis() - startTime;
            backupAuditService.completeAuditLog(auditLog.getId(), 1, null, duration);
            return Result.success("增量备份成功", record);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            String errorMsg = e.getMessage();
            log.error("增量备份失败: {}", errorMsg, e);
            backupAuditService.completeAuditLog(auditLog.getId(), 2, errorMsg, duration);
            return Result.error("增量备份失败: " + errorMsg);
        }
    }

    @GetMapping("/verify/{id}")
    @PreAuthorize("hasAuthority('backup:view')")
    @Log(module = "BACKUP", operationType = "VERIFY", description = "验证备份")
    public Result verifyBackup(@PathVariable Long id) {
        log.info("验证备份，备份ID: {}", id);
        try {
            boolean isValid = backupService.verifyBackup(id);
            if (isValid) {
                return Result.success("备份验证通过");
            } else {
                return Result.error("备份验证失败");
            }
        } catch (Exception e) {
            log.error("验证备份失败: {}", e.getMessage());
            return Result.error("验证备份失败");
        }
    }
}
