package com.gdmu.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        
        Map<String, Object> body = new HashMap<>();
        body.put("code", 403);
        
        String requestUri = request.getRequestURI();
        String requestMethod = request.getMethod();
        
        String errorMessage = accessDeniedException.getMessage();
        
        String userFriendlyMessage = "您没有访问权限";
        
        if (errorMessage != null && errorMessage.contains("Access is denied")) {
            if (requestUri.contains("/admin/")) {
                userFriendlyMessage = "您没有访问此管理功能的权限，请联系管理员分配相应权限";
            } else if (requestUri.contains("/teacher/")) {
                userFriendlyMessage = "您没有访问此教师功能的权限，请联系管理员分配相应权限";
            } else if (requestUri.contains("/student/")) {
                userFriendlyMessage = "您没有访问此学生功能的权限，请联系管理员分配相应权限";
            } else if (requestUri.contains("/company/")) {
                userFriendlyMessage = "您没有访问此企业功能的权限，请联系管理员分配相应权限";
            } else {
                userFriendlyMessage = "您没有访问此资源的权限，请联系管理员";
            }
        }
        
        body.put("message", userFriendlyMessage);
        body.put("details", errorMessage);
        body.put("path", requestUri);
        body.put("method", requestMethod);
        
        ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(response.getOutputStream(), body);
    }
}