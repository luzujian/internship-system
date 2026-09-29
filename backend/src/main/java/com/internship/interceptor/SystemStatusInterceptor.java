package com.internship.interceptor;

import com.internship.config.SystemSettingsConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class SystemStatusInterceptor implements HandlerInterceptor {
    
    private static final String[] EXCLUDE_PATHS = {
        "/api/login",
        "/api/admin/settings",
        "/api/error",
        "/api/auth/login",
        "/api/upload"
    };
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();

        // 刷新请求由控制器校验刷新令牌，不能依赖已经过期的访问令牌识别管理员。
        if ("POST".equals(request.getMethod())
                && "/api/auth/refresh-token".equals(path.substring(request.getContextPath().length()))) {
            return true;
        }
        
        for (String excludePath : EXCLUDE_PATHS) {
            if (path.startsWith(excludePath)) {
                return true;
            }
        }
        
        if (!SystemSettingsConfig.isSystemEnabled()) {
            // TokenFilter 已完成身份验证；管理员维护期间仍需访问后台及关闭维护模式。
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()
                    && authentication.getAuthorities().stream()
                            .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()))) {
                return true;
            }

            log.warn("系统已禁用，拒绝访问: {}", path);
            
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            
            Map<String, Object> result = new HashMap<>();
            result.put("code", 503);
            result.put("message", "系统维护中，暂时无法访问");
            result.put("data", null);
            
            ObjectMapper mapper = new ObjectMapper();
            response.getWriter().write(mapper.writeValueAsString(result));
            
            return false;
        }
        
        return true;
    }
}
