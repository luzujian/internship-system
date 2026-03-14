package com.gdmu.aop;

import com.gdmu.anno.Log;
import com.gdmu.mapper.OperateLogMapper;
import com.gdmu.entity.OperateLog;
import com.gdmu.entity.AdminUser;
import com.gdmu.entity.Result;
import com.gdmu.service.AdminUserService;
import com.gdmu.utils.CurrentHolder;
import com.gdmu.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

import java.lang.reflect.Method;
import java.util.Date;

@Slf4j
@Aspect
@Component
public class OperationLogAspect {

    @Autowired
    private OperateLogMapper operateLogMapper;
    
    @Autowired
    private AdminUserService adminUserService;
    
    @Autowired
    private JwtUtils jwtUtils;

    @Around("@annotation(com.gdmu.anno.Log)")
    public Object logOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        Long userId = getCurrentUserId();
        String userRole = getCurrentUserRole();
        String username = null;
        String ipAddress = getIpAddress();
        
        String originalAdminUsername = getOriginalAdminUsername();
        Boolean isReadOnly = isReadOnly();
        
        if (userId != null && originalAdminUsername != null) {
            username = originalAdminUsername + " (切换角色)";
            userRole = "ADMIN_SWITCHED";
        } else if (userId != null) {
            try {
                AdminUser adminUser = adminUserService.findById(userId);
                if (adminUser != null) {
                    username = adminUser.getUsername();
                }
            } catch (Exception e) {
                log.warn("获取操作人信息失败: {}", e.getMessage());
            }
        }
        
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Log logAnnotation = method.getAnnotation(Log.class);
        String operationType = logAnnotation.operationType();
        String module = logAnnotation.module();
        String description = logAnnotation.description();
        
        if (isReadOnly != null && isReadOnly) {
            description = "[只读模式] " + description;
        }

        Object result = null;
        String operationResult = "SUCCESS";
        
        try {
            result = joinPoint.proceed();
            
            if (result instanceof Result) {
                Result resultObj = (Result) result;
                if (resultObj.getCode() != null && resultObj.getCode() != 200) {
                    operationResult = "FAILURE";
                    log.warn("操作返回失败结果: code={}, message={}", resultObj.getCode(), resultObj.getMessage());
                }
            }
        } catch (Exception e) {
            operationResult = "FAILURE";
            log.error("操作执行失败: {}", e.getMessage(), e);
            throw e;
        }

        OperateLog olog = new OperateLog();
        olog.setOperateAdminId(userId);
        olog.setOperatorName(username);
        olog.setOperatorUsername(username);
        olog.setOperatorRole(userRole != null && userRole.startsWith("ROLE_") ? userRole.substring(5) : userRole);
        olog.setIpAddress(ipAddress);
        olog.setOperationType(operationType);
        olog.setModule(module);
        olog.setDescription(description);
        olog.setOperationResult(operationResult);
        olog.setOperateTime(new Date());

        log.info("记录操作日志: {}", olog);
        operateLogMapper.insert(olog);

        return result;
    }

    private Long getCurrentUserId() {
        return CurrentHolder.getUserId();
    }

    private String getCurrentUserRole() {
        return CurrentHolder.getUserRole();
    }
    
    private String getOriginalAdminUsername() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return null;
            }
            HttpServletRequest request = attributes.getRequest();
            if (request == null) {
                return null;
            }
            String token = request.getHeader("Authorization");
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
                if (jwtUtils.validateToken(token)) {
                    Claims claims = jwtUtils.parseToken(token);
                    return claims.get("originalAdminUsername", String.class);
                }
            }
        } catch (Exception e) {
            log.warn("获取原始管理员用户名失败: {}", e.getMessage());
        }
        return null;
    }
    
    private Boolean isReadOnly() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return null;
            }
            HttpServletRequest request = attributes.getRequest();
            if (request == null) {
                return null;
            }
            String token = request.getHeader("Authorization");
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
                if (jwtUtils.validateToken(token)) {
                    Claims claims = jwtUtils.parseToken(token);
                    return claims.get("isReadOnly", Boolean.class);
                }
            }
        } catch (Exception e) {
            log.warn("获取只读模式标记失败: {}", e.getMessage());
        }
        return null;
    }

    private String getIpAddress() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return "未知";
            }
            HttpServletRequest request = attributes.getRequest();
            if (request == null) {
                return "未知";
            }
            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("Proxy-Client-IP");
            }
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("WL-Proxy-Client-IP");
            }
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("HTTP_CLIENT_IP");
            }
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("HTTP_X_FORWARDED_FOR");
            }
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getRemoteAddr();
            }
            return ip != null ? ip : "未知";
        } catch (Exception e) {
            log.error("获取IP地址失败: {}", e.getMessage());
            return "未知";
        }
    }
}