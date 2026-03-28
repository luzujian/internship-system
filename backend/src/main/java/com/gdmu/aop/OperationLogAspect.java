package com.gdmu.aop;

import com.gdmu.anno.Log;
import com.gdmu.mapper.OperateLogMapper;
import com.gdmu.entity.OperateLog;
import com.gdmu.entity.AdminUser;
import com.gdmu.entity.Result;
import com.gdmu.entity.StudentUser;
import com.gdmu.entity.TeacherUser;
import com.gdmu.entity.CompanyUser;
import com.gdmu.service.AdminUserService;
import com.gdmu.service.StudentUserService;
import com.gdmu.service.TeacherUserService;
import com.gdmu.service.CompanyUserService;
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
    private StudentUserService studentUserService;

    @Autowired
    private TeacherUserService teacherUserService;

    @Autowired
    private CompanyUserService companyUserService;

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
                if (userRole != null) {
                    if (userRole.equals("ROLE_STUDENT") || userRole.equals("STUDENT")) {
                        StudentUser studentUser = studentUserService.findById(userId);
                        if (studentUser != null) {
                            username = studentUser.getName();
                        }
                    } else if (userRole.equals("ROLE_TEACHER") || userRole.startsWith("ROLE_TEACHER_") || userRole.equals("TEACHER")) {
                        TeacherUser teacherUser = teacherUserService.findById(userId);
                        if (teacherUser != null) {
                            username = teacherUser.getName();
                        }
                    } else if (userRole.equals("ROLE_COMPANY") || userRole.equals("COMPANY")) {
                        CompanyUser companyUser = companyUserService.findById(userId);
                        if (companyUser != null) {
                            username = companyUser.getCompanyName();
                        }
                    } else {
                        AdminUser adminUser = adminUserService.findById(userId);
                        if (adminUser != null) {
                            username = adminUser.getUsername();
                        }
                    }
                } else {
                    AdminUser adminUser = adminUserService.findById(userId);
                    if (adminUser != null) {
                        username = adminUser.getUsername();
                    }
                }
            } catch (Exception e) {
                log.warn("获取操作人信息失败：{}", e.getMessage());
            }
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Log logAnnotation = method.getAnnotation(Log.class);
        String operationType = logAnnotation.operationType();
        String module = logAnnotation.module();
        String baseDescription = logAnnotation.description();

        // 构建详细的操作描述
        String description = buildDetailedDescription(baseDescription, joinPoint, operationType);

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
                    log.warn("操作返回失败结果：code={}, message={}", resultObj.getCode(), resultObj.getMessage());
                }
            }
        } catch (Exception e) {
            operationResult = "FAILURE";
            log.error("操作执行失败：{}", e.getMessage(), e);
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

        try {
            log.info("记录操作日志：{}", olog);
            operateLogMapper.insert(olog);
        } catch (Exception e) {
            log.error("记录操作日志失败：{}", e.getMessage());
            // 日志记录失败不影响业务操作
        }

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
            log.warn("获取原始管理员用户名失败：{}", e.getMessage());
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
            log.warn("获取只读模式标记失败：{}", e.getMessage());
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
            log.error("获取 IP 地址失败：{}", e.getMessage());
            return "未知";
        }
    }

    /**
     * 构建详细的操作描述
     */
    private String buildDetailedDescription(String baseDescription, ProceedingJoinPoint joinPoint, String operationType) {
        StringBuilder detailedDesc = new StringBuilder(baseDescription);

        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Object[] args = joinPoint.getArgs();

            StringBuilder paramsInfo = new StringBuilder();

            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];

                // 跳过不需要记录的参数类型
                if (arg instanceof HttpServletRequest ||
                    arg instanceof jakarta.servlet.http.HttpServletResponse ||
                    arg instanceof org.springframework.web.multipart.MultipartFile ||
                    (arg instanceof org.springframework.web.multipart.MultipartFile[])) {
                    continue;
                }

                // 简单处理：只记录 ID 和名称
                if (arg instanceof Long || arg instanceof Integer) {
                    if (paramsInfo.length() > 0) {
                        paramsInfo.append(", ");
                    }
                    paramsInfo.append("id=").append(arg);
                } else if (arg instanceof String) {
                    // 字符串参数太长就不记录了
                    String strArg = (String) arg;
                    if (strArg.length() <= 50) {
                        if (paramsInfo.length() > 0) {
                            paramsInfo.append(", ");
                        }
                        paramsInfo.append(strArg.length() > 20 ? strArg.substring(0, 20) + "..." : strArg);
                    }
                }
            }

            if (paramsInfo.length() > 0) {
                detailedDesc.append(" [").append(paramsInfo).append("]");
            }

        } catch (Exception e) {
            log.debug("构建详细操作描述失败：{}", e.getMessage());
        }

        return detailedDesc.toString();
    }
}
