package com.internship.controller;

import com.internship.anno.Log;
import com.internship.entity.Result;
import com.internship.entity.SystemSettings;
import com.internship.service.SystemSettingsService;
import com.internship.service.EmailService;
import com.internship.config.EmailNotifyScope;
import com.internship.config.SystemSettingsConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/admin/settings")
public class SystemSettingsController {
    
    @Autowired
    private SystemSettingsService systemSettingsService;

    @Autowired
    private EmailService emailService;
    
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
            result.put("emailNotifyScopeOptions", emailScopeOptions());
            
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
            if (settings.get("security") instanceof Map<?, ?> security && security.containsKey("sessionTimeout")) {
                Object value = security.get("sessionTimeout");
                if (value == null || !value.toString().matches("[0-9]{1,3}")
                        || Integer.parseInt(value.toString()) < 15 || Integer.parseInt(value.toString()) > 480) {
                    return Result.error("会话超时时间须为 15 至 480 分钟的整数");
                }
            }
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

    @PostMapping("/test-email")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "SYSTEM_SETTINGS", operationType = "OTHER", description = "发送邮件通道测试")
    public Result testEmail() {
        if (!emailService.isConfigured()) return Result.error("邮件通道未配置");
        return emailService.sendTestEmail() ? Result.success("测试邮件已发送")
                : Result.error("测试邮件发送失败");
    }
    
    private Map<String, Object> getDefaultSettings() {
        Map<String, Object> result = new HashMap<>();
        result.put("basic", new HashMap<String, Object>() {{
            put("systemName", "学生实习管理系统");
            put("systemStatus", 1);
            put("emailNotifyEnabled", 1);
            put("emailNotifyScopes", defaultScopeCodes());
        }});
        result.put("emailNotifyScopeOptions", emailScopeOptions());
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
        basic.put("emailNotifyEnabled", settings.getEmailNotifyEnabled() == null ? 1 : settings.getEmailNotifyEnabled());
        basic.put("emailNotifyScopes", parseScopes(settings.getEmailNotifyScopes()));
        return basic;
    }

    /** 供前端渲染的可选邮件提醒业务范围（代码 + 中文标签）。 */
    private List<Map<String, String>> emailScopeOptions() {
        List<Map<String, String>> options = new ArrayList<>();
        for (EmailNotifyScope scope : EmailNotifyScope.values()) {
            options.add(Map.of("code", scope.name(), "label", scope.getLabel()));
        }
        return options;
    }

    private String[] defaultScopeCodes() {
        return Arrays.stream(EmailNotifyScope.values()).map(Enum::name).toArray(String[]::new);
    }

    /** null/空白视为全部启用（历史数据兼容）；过滤未知代码。 */
    private String[] parseScopes(String scopes) {
        if (scopes == null || scopes.isBlank()) {
            return defaultScopeCodes();
        }
        return Arrays.stream(scopes.split(","))
                .map(EmailNotifyScope::fromCode)
                .filter(Objects::nonNull)
                .map(Enum::name)
                .distinct()
                .toArray(String[]::new);
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
        if (basic.containsKey("emailNotifyEnabled")) {
            Integer enabled = toInteger(basic.get("emailNotifyEnabled"));
            settings.setEmailNotifyEnabled(enabled != null && enabled == 1 ? 1 : 0);
        }
        if (basic.containsKey("emailNotifyScopes")) {
            settings.setEmailNotifyScopes(normalizeScopes(basic.get("emailNotifyScopes")));
        }
        // 双阶段开关与双选时间窗已退役。旧客户端携带这些字段时明确忽略，
        // 保留实体/数据库列仅用于历史兼容，不再形成活跃配置契约。
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
    
    /** 接受字符串数组/逗号分隔字符串，仅保留合法代码；空列表表示全部业务不提醒。 */
    private String normalizeScopes(Object scopesObj) {
        List<String> codes = new ArrayList<>();
        if (scopesObj instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) codes.add(item.toString());
            }
        } else if (scopesObj instanceof String str) {
            codes.addAll(Arrays.asList(str.split(",")));
        }
        return codes.stream()
                .map(EmailNotifyScope::fromCode)
                .filter(Objects::nonNull)
                .map(Enum::name)
                .distinct()
                .collect(Collectors.joining(","));
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
