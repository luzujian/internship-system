package com.gdmu.interceptor;

import com.gdmu.config.SystemSettingsConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
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
        "/api/upload",
        "/api/uploads"
    };
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        
        for (String excludePath : EXCLUDE_PATHS) {
            if (path.startsWith(excludePath)) {
                return true;
            }
        }
        
        if (!SystemSettingsConfig.isSystemEnabled()) {
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
