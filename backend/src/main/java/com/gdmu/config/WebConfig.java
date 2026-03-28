package com.gdmu.config;

import com.gdmu.interceptor.SystemStatusInterceptor;
import com.gdmu.interceptor.TeacherPermissionInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private SystemStatusInterceptor systemStatusInterceptor;

    @Autowired
    private TeacherPermissionInterceptor teacherPermissionInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(systemStatusInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                    "/api/auth/login", 
                    "/api/admin/settings",
                    "/api/upload/**"
                );
        
        registry.addInterceptor(teacherPermissionInterceptor)
                .addPathPatterns("/api/teacher/**")
                .excludePathPatterns(
                    "/api/teacher/permissions/**",
                    "/api/teacher/roles/**",
                    "/api/teacher/role-permissions/**"
                );
    }
}
