package com.internship.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException {
        // 如果响应已提交（如 SSE 流式请求），不再尝试写入
        if (response.isCommitted()) {
            return;
        }

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        
        Map<String, Object> body = new HashMap<>();
        body.put("code", 401);
        
        String errorMessage = authException.getMessage();
        if (errorMessage != null && errorMessage.contains("账号已被禁用")) {
            body.put("message", "您的账号已被禁用，请联系管理员");
        } else if (errorMessage != null && errorMessage.contains("用户不存在")) {
            body.put("message", "用户名或密码错误");
        } else {
            body.put("message", "用户名或密码错误");
        }
        
        ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(response.getOutputStream(), body);
    }
}