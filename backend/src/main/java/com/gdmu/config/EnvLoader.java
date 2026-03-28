package com.gdmu.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 启动时加载 .env 文件到系统环境变量
 * 通过 ApplicationContextInitializer 在 Spring 容器初始化前加载
 */
@Slf4j
public class EnvLoader implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment env = applicationContext.getEnvironment();

        try {
            Path envPath = findEnvFile();
            if (envPath == null || !Files.exists(envPath)) {
                log.debug("未找到 .env 文件，跳过环境变量加载");
                return;
            }

            Map<String, String> envMap = Files.lines(envPath)
                .filter(line -> line.contains("=") && !line.trim().startsWith("#"))
                .collect(Collectors.toMap(
                    line -> line.substring(0, line.indexOf("=")).trim(),
                    line -> line.substring(line.indexOf("=") + 1).trim()
                ));

            for (Map.Entry<String, String> entry : envMap.entrySet()) {
                String key = entry.getKey();
                // 优先使用已存在的环境变量，不覆盖
                if (System.getProperty(key) == null && System.getenv(key) == null) {
                    System.setProperty(key, entry.getValue());
                }
            }

            log.info("环境变量加载完成，共加载 {} 个变量", envMap.size());
        } catch (IOException e) {
            log.warn("加载 .env 文件失败: {}", e.getMessage());
        }
    }

    private Path findEnvFile() {
        // 1. 尝试 user.dir（项目根目录）
        Path path = Paths.get(System.getProperty("user.dir"), ".env");
        if (Files.exists(path)) {
            return path;
        }

        // 2. 尝试 backend 目录
        path = Paths.get(System.getProperty("user.dir"), "backend", ".env");
        if (Files.exists(path)) {
            return path;
        }

        // 3. 尝试类路径
        try {
            ClassPathResource resource = new ClassPathResource(".env");
            if (resource.exists()) {
                return resource.getFile().toPath();
            }
        } catch (IOException ignored) {
        }

        return null;
    }
}
