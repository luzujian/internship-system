package com.internship.agent.business;

import com.internship.entity.SystemConfig;
import com.internship.service.SystemConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * AI 功能统一开关。
 * system 跟随 system_config.AI_ASSISTANT_ENABLED；false 强制关闭；true 仅用于本地/测试覆盖。
 */
@Service
@Slf4j
public class AgentFeatureGate {
    public static final String CONFIG_KEY = "AI_ASSISTANT_ENABLED";

    private final SystemConfigService config;
    private final String mode;

    public AgentFeatureGate(SystemConfigService config,
                            @Value("${agent.enabled:system}") String mode) {
        this.config = config;
        this.mode = mode == null || mode.isBlank() ? "system" : mode.trim().toLowerCase();
    }

    public boolean enabled() {
        if ("true".equals(mode)) return true;
        if ("false".equals(mode)) return false;
        try {
            SystemConfig value = config == null ? null : config.findByConfigKey(CONFIG_KEY);
            if (value == null || value.getConfigValue() == null || value.getConfigValue().isBlank()) return true;
            if (value.getStatus() != null && value.getStatus() != 1) return false;
            return Boolean.parseBoolean(value.getConfigValue());
        } catch (Exception e) {
            log.warn("AI 功能开关读取失败，临时关闭 AI 助手：{}", e.getMessage());
            return false;
        }
    }

    public String mode() {
        return mode;
    }

    public void requireEnabled() {
        if (!enabled()) throw new AgentException("AI_DISABLED", "系统当前未启用AI助手。", 503);
    }
}
