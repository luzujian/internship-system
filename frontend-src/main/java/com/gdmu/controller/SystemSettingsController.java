package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.entity.Result;
import com.gdmu.entity.SystemSettings;
import com.gdmu.service.SystemSettingsService;
import com.gdmu.config.SystemSettingsConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/settings")
public class SystemSettingsController {
    
    @Autowired
    private SystemSettingsService systemSettingsService;
    
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result getSettings() {
        log.info("获取系统设置");
        try {
            SystemSettings settings = systemSettingsService.findLatest();
            
            if (settings == null) {
                return Result.success(getDefaultSettings());
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("basic", getBasicSettings(settings));
            result.put("security", getSecuritySettings(settings));
            
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取系统设置失败: {}", e.getMessage());
            return Result.error("获取系统设置失败");
        }
    }
    
    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "SYSTEM_SETTINGS", operationType = "UPDATE", description = "更新系统设置")
    @SuppressWarnings("unchecked")
    public Result updateSettings(@RequestBody Map<String, Object> settings) {
        log.info("更新系统设置");
        try {
            SystemSettings currentSettings = systemSettingsService.findLatest();
            
            if (currentSettings == null) {
                currentSettings = new SystemSettings();
                systemSettingsService.insert(currentSettings);
            }
            
            if (settings.containsKey("basic")) {
                updateBasicSettings(currentSettings, (Map<String, Object>) settings.get("basic"));
            }
            if (settings.containsKey("security")) {
                updateSecuritySettings(currentSettings, (Map<String, Object>) settings.get("security"));
            }
            
            systemSettingsService.update(currentSettings);
            
            SystemSettingsConfig.reloadSettings();
            
            return Result.success("系统设置更新成功");
        } catch (Exception e) {
            log.error("更新系统设置失败: {}", e.getMessage());
            return Result.error("更新系统设置失败");
        }
    }
    
    private Map<String, Object> getDefaultSettings() {
        Map<String, Object> result = new HashMap<>();
        result.put("basic", new HashMap<String, Object>() {{
            put("systemName", "学生实习管理系统");
            put("systemStatus", 1);
        }});
        result.put("security", new HashMap<String, Object>() {{
            put("minPasswordLength", 6);
            put("passwordComplexity", new String[]{"lowercase", "number"});
            put("passwordExpireDays", 90);
            put("maxLoginAttempts", 5);
            put("lockTime", 30);
            put("sessionTimeout", 120);
            put("enableTwoFactor", 0);
        }});
        return result;
    }
    
    private Map<String, Object> getBasicSettings(SystemSettings settings) {
        Map<String, Object> basic = new HashMap<>();
        basic.put("systemStatus", settings.getSystemStatus());
        basic.put("dualSelectionStartDate", settings.getDualSelectionStartDate());
        basic.put("dualSelectionEndDate", settings.getDualSelectionEndDate());
        return basic;
    }
    
    private Map<String, Object> getSecuritySettings(SystemSettings settings) {
        Map<String, Object> security = new HashMap<>();
        security.put("minPasswordLength", settings.getMinPasswordLength());
        security.put("passwordComplexity", parseComplexity(settings.getPasswordComplexity()));
        security.put("passwordExpireDays", settings.getPasswordExpireDays());
        security.put("maxLoginAttempts", settings.getMaxLoginAttempts());
        security.put("lockTime", settings.getLockTime());
        security.put("sessionTimeout", settings.getSessionTimeout());
        security.put("enableTwoFactor", settings.getEnableTwoFactor());
        return security;
    }
    
    private void updateBasicSettings(SystemSettings settings, Map<String, Object> basic) {
        if (basic.containsKey("systemStatus")) {
            settings.setSystemStatus(toInteger(basic.get("systemStatus")));
        }
        if (basic.containsKey("dualSelectionStartDate")) {
            Object startDateObj = basic.get("dualSelectionStartDate");
            if (startDateObj != null && !startDateObj.toString().isEmpty()) {
                try {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                    settings.setDualSelectionStartDate(sdf.parse(startDateObj.toString()));
                } catch (Exception e) {
                    log.error("解析双向选择开始日期失败: {}", e.getMessage());
                }
            } else {
                settings.setDualSelectionStartDate(null);
            }
        }
        if (basic.containsKey("dualSelectionEndDate")) {
            Object endDateObj = basic.get("dualSelectionEndDate");
            if (endDateObj != null && !endDateObj.toString().isEmpty()) {
                try {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                    settings.setDualSelectionEndDate(sdf.parse(endDateObj.toString()));
                } catch (Exception e) {
                    log.error("解析双向选择结束日期失败: {}", e.getMessage());
                }
            } else {
                settings.setDualSelectionEndDate(null);
            }
        }
    }
    
    private void updateSecuritySettings(SystemSettings settings, Map<String, Object> security) {
        if (security.containsKey("minPasswordLength")) {
            settings.setMinPasswordLength(toInteger(security.get("minPasswordLength")));
        }
        if (security.containsKey("passwordComplexity")) {
            Object complexityObj = security.get("passwordComplexity");
            String complexityStr;
            if (complexityObj instanceof String[]) {
                complexityStr = formatComplexity((String[]) complexityObj);
            } else if (complexityObj instanceof String) {
                complexityStr = (String) complexityObj;
            } else {
                complexityStr = "lowercase,number";
            }
            settings.setPasswordComplexity(complexityStr);
        }
        if (security.containsKey("passwordExpireDays")) {
            settings.setPasswordExpireDays(toInteger(security.get("passwordExpireDays")));
        }
        if (security.containsKey("maxLoginAttempts")) {
            settings.setMaxLoginAttempts(toInteger(security.get("maxLoginAttempts")));
        }
        if (security.containsKey("lockTime")) {
            settings.setLockTime(toInteger(security.get("lockTime")));
        }
        if (security.containsKey("sessionTimeout")) {
            settings.setSessionTimeout(toInteger(security.get("sessionTimeout")));
        }
    }
    
    private String[] parseComplexity(String complexity) {
        if (complexity == null || complexity.isEmpty()) {
            return new String[]{"lowercase", "number"};
        }
        return complexity.split(",");
    }
    
    private String formatComplexity(String[] complexity) {
        if (complexity == null || complexity.length == 0) {
            return "lowercase,number";
        }
        return String.join(",", complexity);
    }
    
    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Boolean) {
            return ((Boolean) value) ? 1 : 0;
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }
}
