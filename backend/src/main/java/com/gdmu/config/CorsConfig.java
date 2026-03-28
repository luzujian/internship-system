package com.gdmu.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * 跨域配置类
 */
@Configuration
public class CorsConfig {

    @Value("${ALLOWED_ORIGINS:http://localhost:5173,https://luzujian.xyz,https://www.luzujian.xyz}")
    private List<String> allowedOrigins;

    @Bean
    public CorsFilter corsFilter() {
        // 1. 创建 CORS 配置对象
        CorsConfiguration config = new CorsConfiguration();
        // 允许哪些域名访问跨域资源（支持 patterns）
        config.setAllowedOriginPatterns(allowedOrigins);
        // 允许发送 Cookie
        config.setAllowCredentials(true);
        // 允许哪些请求方式
        config.addAllowedMethod("*");
        // 允许哪些请求头
        config.addAllowedHeader("*");
        // 允许的最大年龄（秒），表示预检请求的有效期
        config.setMaxAge(3600L);

        // 2. 创建 CORS 源对象
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 对所有请求应用 CORS 配置
        source.registerCorsConfiguration("/**", config);

        // 3. 返回 CORS 过滤器
        return new CorsFilter(source);
    }
}
