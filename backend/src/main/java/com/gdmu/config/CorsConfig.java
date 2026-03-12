package com.gdmu.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * 跨域配置类
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        // 1. 创建CORS配置对象
        CorsConfiguration config = new CorsConfiguration();
        // 允许哪些域名访问跨域资源
        config.addAllowedOrigin("http://localhost:5173");
        // 允许发送Cookie
        config.setAllowCredentials(true);
        // 允许哪些请求方式
        config.addAllowedMethod("*");
        // 允许哪些请求头
        config.addAllowedHeader("*");
        // 允许的最大年龄（秒），表示预检请求的有效期
        config.setMaxAge(3600L);

        // 2. 创建CORS源对象
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 对所有请求应用CORS配置
        source.registerCorsConfiguration("/**", config);

        // 3. 返回CORS过滤器
        return new CorsFilter(source);
    }
}