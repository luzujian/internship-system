package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.entity.SystemConfig;
import com.gdmu.entity.Result;
import com.gdmu.service.SystemConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/admin/system-config")
public class SystemConfigController {

    @Autowired
    private SystemConfigService systemConfigService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "SYSTEM_CONFIG", operationType = "INSERT", description = "添加系统配置")
    public Result addConfig(@RequestBody SystemConfig systemConfig) {
        log.info("添加系统配置: {}", systemConfig.getConfigKey());
        try {
            int result = systemConfigService.insert(systemConfig);
            return Result.success("添加成功", systemConfig);
        } catch (Exception e) {
            log.error("添加系统配置失败: {}", e.getMessage());
            return Result.error("添加失败: " + e.getMessage());
        }
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "SYSTEM_CONFIG", operationType = "SELECT", description = "查询系统配置列表")
    public Result getConfigs() {
        log.info("查询所有系统配置");
        try {
            List<SystemConfig> configs = systemConfigService.findAll();
            return Result.success(configs);
        } catch (Exception e) {
            log.error("查询系统配置失败: {}", e.getMessage());
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "SYSTEM_CONFIG", operationType = "SELECT", description = "查询系统配置详情")
    public Result getConfigById(@PathVariable Long id) {
        log.info("查询系统配置详情，ID: {}", id);
        try {
            SystemConfig config = systemConfigService.findById(id);
            return Result.success(config);
        } catch (Exception e) {
            log.error("查询系统配置详情失败: {}", e.getMessage());
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/key/{configKey}")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "SYSTEM_CONFIG", operationType = "SELECT", description = "根据配置键查询")
    public Result getConfigByKey(@PathVariable String configKey) {
        log.info("根据配置键查询系统配置，键: {}", configKey);
        try {
            SystemConfig config = systemConfigService.findByConfigKey(configKey);
            return Result.success(config);
        } catch (Exception e) {
            log.error("根据配置键查询系统配置失败: {}", e.getMessage());
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "SYSTEM_CONFIG", operationType = "UPDATE", description = "更新系统配置")
    public Result updateConfig(@PathVariable Long id, @RequestBody SystemConfig systemConfig) {
        log.info("更新系统配置，ID: {}", id);
        try {
            systemConfig.setId(id);
            int result = systemConfigService.update(systemConfig);
            return Result.success("更新成功", systemConfig);
        } catch (Exception e) {
            log.error("更新系统配置失败: {}", e.getMessage());
            return Result.error("更新失败: " + e.getMessage());
        }
    }

    @PutMapping("/key/{configKey}")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "SYSTEM_CONFIG", operationType = "UPDATE", description = "根据配置键更新配置值")
    public Result updateConfigByKey(@PathVariable String configKey, @RequestBody SystemConfig systemConfig) {
        log.info("根据配置键更新系统配置，键: {}", configKey);
        try {
            SystemConfig existingConfig = systemConfigService.findByConfigKey(configKey);
            if (existingConfig == null) {
                return Result.error("配置不存在: " + configKey);
            }
            existingConfig.setConfigValue(systemConfig.getConfigValue());
            int result = systemConfigService.update(existingConfig);
            return Result.success("更新成功", existingConfig);
        } catch (Exception e) {
            log.error("根据配置键更新系统配置失败: {}", e.getMessage());
            return Result.error("更新失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "SYSTEM_CONFIG", operationType = "DELETE", description = "删除系统配置")
    public Result deleteConfig(@PathVariable Long id) {
        log.info("删除系统配置，ID: {}", id);
        try {
            int result = systemConfigService.deleteById(id);
            return Result.success("删除成功");
        } catch (Exception e) {
            log.error("删除系统配置失败: {}", e.getMessage());
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "SYSTEM_CONFIG", operationType = "SELECT", description = "按状态查询系统配置")
    public Result getConfigsByStatus(@PathVariable Integer status) {
        log.info("按状态查询系统配置，状态: {}", status);
        try {
            List<SystemConfig> configs = systemConfigService.findByStatus(status);
            return Result.success(configs);
        } catch (Exception e) {
            log.error("按状态查询系统配置失败: {}", e.getMessage());
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/search/{configName}")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "SYSTEM_CONFIG", operationType = "SELECT", description = "搜索系统配置")
    public Result searchConfigs(@PathVariable String configName) {
        log.info("搜索系统配置，配置名称: {}", configName);
        try {
            List<SystemConfig> configs = systemConfigService.searchByConfigName(configName);
            return Result.success(configs);
        } catch (Exception e) {
            log.error("搜索系统配置失败: {}", e.getMessage());
            return Result.error("搜索失败: " + e.getMessage());
        }
    }
}
