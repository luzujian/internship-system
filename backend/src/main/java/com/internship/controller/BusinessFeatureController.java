package com.internship.controller;

import com.internship.anno.Log;
import com.internship.entity.Result;
import com.internship.entity.SystemConfig;
import com.internship.service.ReflectionFeatureService;
import com.internship.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class BusinessFeatureController {
    private final ReflectionFeatureService reflections;
    private final SystemConfigService configs;

    @GetMapping("/api/home/business-features")
    public Result current() {
        return Result.success(Map.of("reflectionEnabled", reflections.enabled()));
    }

    @PutMapping("/api/admin/business-features/reflection")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('system:settings:view')")
    @Log(module = "SYSTEM_CONFIG", operationType = "UPDATE", description = "设置心得业务开关")
    public Result configure(@RequestBody Map<String, Boolean> request) {
        Boolean enabled = request.get("reflectionEnabled");
        if (enabled == null) return Result.error("请选择心得业务状态");
        SystemConfig config = configs.findByConfigKey(ReflectionFeatureService.CONFIG_KEY);
        boolean insert = config == null;
        if (insert) {
            config = new SystemConfig();
            config.setConfigKey(ReflectionFeatureService.CONFIG_KEY);
            config.setConfigName("实习心得业务");
            config.setConfigType("boolean");
            config.setDescription("停用时隐藏入口并停止心得提交、分析、评分和催交，保留历史数据");
        }
        config.setStatus(1);
        config.setConfigValue(Boolean.toString(enabled));
        if (insert) configs.insert(config); else configs.update(config);
        return Result.success(Map.of("reflectionEnabled", reflections.enabled()));
    }
}
