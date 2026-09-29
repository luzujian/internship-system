package com.internship.config;

import com.internship.entity.SystemSettings;
import com.internship.service.SystemSettingsService;
import com.internship.utils.SpringContextHolder;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@Configuration
public class SystemSettingsConfig {
    
    @Autowired
    private SystemSettingsService systemSettingsService;
    
    private static SystemSettings currentSettings;
    
    @PostConstruct
    public void init() {
        loadSettings();
    }
    
    @Scheduled(fixedRate = 300000)
    public void refreshSettings() {
        loadSettings();
    }
    
    public static void reloadSettings() {
        SystemSettingsConfig instance = getInstance();
        if (instance != null) {
            instance.loadSettings();
        }
    }
    
    private static SystemSettingsConfig getInstance() {
        try {
            return SpringContextHolder.getBean(SystemSettingsConfig.class);
        } catch (Exception e) {
            log.error("获取SystemSettingsConfig实例失败: {}", e.getMessage());
            return null;
        }
    }
    
    private void loadSettings() {
        try {
            SystemSettings settings = systemSettingsService.findLatest();
            if (settings != null) {
                currentSettings = settings;
                log.info("系统设置已更新: maxLoginAttempts={}, lockTime={}, sessionTimeout={}", 
                    settings.getMaxLoginAttempts(), settings.getLockTime(), settings.getSessionTimeout());
            }
        } catch (Exception e) {
            log.error("加载系统设置失败: {}", e.getMessage(), e);
        }
    }
    
    public static SystemSettings getCurrentSettings() {
        return currentSettings;
    }
    
    /** 邮件提醒总开关：无配置（含历史行/列缺失）时默认开启，保持既有行为。 */
    public static boolean isEmailNotifyEnabled() {
        if (currentSettings == null || currentSettings.getEmailNotifyEnabled() == null) {
            return true;
        }
        return currentSettings.getEmailNotifyEnabled() == 1;
    }

    /** 指定业务是否启用邮件提醒：总开关关闭一律不发；范围为空视为全部启用。 */
    public static boolean isEmailNotifyScopeEnabled(EmailNotifyScope scope) {
        if (scope == null || !isEmailNotifyEnabled()) {
            return false;
        }
        if (currentSettings == null) {
            return true;
        }
        String scopes = currentSettings.getEmailNotifyScopes();
        if (scopes == null || scopes.isBlank()) {
            return true;
        }
        for (String item : scopes.split(",")) {
            if (scope.name().equalsIgnoreCase(item.trim())) {
                return true;
            }
        }
        return false;
    }

    public static String getSystemName() {
        return currentSettings != null ? currentSettings.getSystemName() : "学生实习管理系统";
    }
    
    public static Integer getSessionTimeout() {
        return currentSettings != null ? currentSettings.getSessionTimeout() : 120;
    }
    
    public static Integer getMinPasswordLength() {
        return currentSettings != null ? currentSettings.getMinPasswordLength() : 6;
    }
    
    public static Integer getMaxLoginAttempts() {
        return currentSettings != null ? currentSettings.getMaxLoginAttempts() : 5;
    }
    
    public static Integer getLockTime() {
        return currentSettings != null ? currentSettings.getLockTime() : 30;
    }
    
    public static boolean isSystemEnabled() {
        return currentSettings != null && currentSettings.getSystemStatus() == 1;
    }
    
    public static boolean isTwoFactorEnabled() {
        return currentSettings != null && currentSettings.getEnableTwoFactor() == 1;
    }
    
    public static String getPasswordComplexity() {
        return currentSettings != null && currentSettings.getPasswordComplexity() != null 
                ? currentSettings.getPasswordComplexity() : "lowercase,number";
    }
    
    public static Integer getPasswordExpireDays() {
        return currentSettings != null ? currentSettings.getPasswordExpireDays() : 90;
    }
}
