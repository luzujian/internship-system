package com.gdmu.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 文件上传配置
 * 注意：本项目已统一使用阿里云 OSS 存储文件，无需本地文件映射
 */
@Configuration
public class FileUploadConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 所有文件现在都存储在阿里云 OSS 上
        // 如果需要访问 OSS 文件，直接使用 OSS 返回的 URL 即可
        // 本地不再需要文件映射配置
    }
}
