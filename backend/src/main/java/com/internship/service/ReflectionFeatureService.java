package com.internship.service;

import com.internship.entity.SystemConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Retired business: preserve its implementation and data, default to disabled. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReflectionFeatureService {
    public static final String CONFIG_KEY = "INTERNSHIP_REFLECTION_ENABLED";
    public static final String DISABLED_MESSAGE = "实习心得业务已停用，历史数据保留。";
    private final SystemConfigService configs;

    public boolean enabled() {
        return isEnabled(configs);
    }

    public static boolean isEnabled(SystemConfigService configs) {
        if (configs == null) return false;
        try {
            SystemConfig config = configs.findByConfigKey(CONFIG_KEY);
            return config != null && Integer.valueOf(1).equals(config.getStatus())
                    && "true".equalsIgnoreCase(config.getConfigValue() == null ? "" : config.getConfigValue().trim());
        } catch (Exception failure) {
            log.warn("心得业务开关暂时无法读取，保持停用");
            return false;
        }
    }
}
