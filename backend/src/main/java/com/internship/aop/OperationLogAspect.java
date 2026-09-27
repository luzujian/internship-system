package com.internship.aop;

import com.internship.anno.Log;
import com.internship.entity.OperateLog;
import com.internship.entity.AdminUser;
import com.internship.entity.Result;
import com.internship.entity.StudentUser;
import com.internship.entity.TeacherUser;
import com.internship.entity.CompanyUser;
import com.internship.service.AdminUserService;
import com.internship.service.AuditLogService;
import com.internship.service.StudentUserService;
import com.internship.service.TeacherUserService;
import com.internship.service.CompanyUserService;
import com.internship.utils.CurrentHolder;
import com.internship.utils.IpUtils;
import com.internship.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

import java.lang.reflect.Method;

@Slf4j
@Aspect
@Component
public class OperationLogAspect {

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private AdminUserService adminUserService;

    @Autowired
    private StudentUserService studentUserService;

    @Autowired
    private TeacherUserService teacherUserService;

    @Autowired
    private CompanyUserService companyUserService;

    @Autowired
    private com.internship.service.PasswordResetService passwordResetService;

    @Autowired
    private JwtUtils jwtUtils;

    @Around("@annotation(com.internship.anno.Log)")
    public Object logOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        Long userId = getCurrentUserId();
        String userRole = getCurrentUserRole();
        String operatorName = null;
        String operatorUsername = null;
        String ipAddress = getIpAddress();

        String originalAdminUsername = getOriginalAdminUsername();
        Boolean isReadOnly = isReadOnly();

        if (userId != null && originalAdminUsername != null) {
            AdminUser originalAdmin = null;
            try {
                originalAdmin = adminUserService.findByUsername(originalAdminUsername);
            } catch (Exception e) {
                log.warn("获取原管理员信息失败：{}", e.getMessage());
            }
            operatorName = (originalAdmin != null ? originalAdmin.getName() : originalAdminUsername) + " (切换角色)";
            operatorUsername = originalAdminUsername + " (切换角色)";
            userRole = "ADMIN";
        } else if (userId != null) {
            try {
                if (userRole != null) {
                    if (userRole.equals("ROLE_STUDENT") || userRole.equals("STUDENT")) {
                        StudentUser studentUser = studentUserService.findById(userId);
                        if (studentUser != null) {
                            operatorName = studentUser.getName();
                            operatorUsername = studentUser.getUsername();
                        }
                    } else if (userRole.equals("ROLE_TEACHER") || userRole.startsWith("ROLE_TEACHER_") || userRole.equals("TEACHER")) {
                        TeacherUser teacherUser = teacherUserService.findById(userId);
                        if (teacherUser != null) {
                            operatorName = teacherUser.getName();
                            operatorUsername = teacherUser.getUsername();
                        }
                    } else if (userRole.equals("ROLE_COMPANY") || userRole.equals("COMPANY")) {
                        CompanyUser companyUser = companyUserService.findById(userId);
                        if (companyUser != null) {
                            operatorName = companyUser.getCompanyName();
                            operatorUsername = companyUser.getUsername();
                        }
                    } else {
                        AdminUser adminUser = adminUserService.findById(userId);
                        if (adminUser != null) {
                            operatorName = adminUser.getName();
                            operatorUsername = adminUser.getUsername();
                        }
                    }
                } else {
                        AdminUser adminUser = adminUserService.findById(userId);
                        if (adminUser != null) {
                            operatorName = adminUser.getName();
                            operatorUsername = adminUser.getUsername();
                        }
                }
            } catch (Exception e) {
                log.warn("获取操作人信息失败：{}", e.getMessage());
            }
        }

        // 匿名自助操作兜底：无登录态（如忘记密码重置）时，以请求体中的账号标识作为操作人，避免操作人为空
        if (operatorUsername == null) {
            String claimedUsername = extractClaimedUsername(joinPoint);
            if (claimedUsername != null) {
                operatorUsername = claimedUsername;
                operatorName = claimedUsername;
                // 能归属到具体账号时补上角色（学生 / 教师 / 企业），归属失败则角色仍未知
                String claimedRole = resolveClaimedRole(joinPoint, claimedUsername);
                if (claimedRole != null) {
                    userRole = claimedRole;
                }
            }
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Log logAnnotation = method.getAnnotation(Log.class);
        String operationType = logAnnotation.operationType();
        String module = logAnnotation.module();
        String baseDescription = logAnnotation.description();
        String riskLevel = logAnnotation.riskLevel();
        String targetType = logAnnotation.targetType();
        boolean critical = logAnnotation.critical();

        // 构建详细的操作描述
        String description = buildDetailedDescription(baseDescription, joinPoint, operationType);

        if (isReadOnly != null && isReadOnly) {
            description = "[只读模式] " + description;
        }

        // 目标对象ID：提取第一个数值型参数
        String targetId = extractTargetId(joinPoint);

        OperateLog attemptLog = new OperateLog();
        attemptLog.setOperateAdminId(userId);
        attemptLog.setOperatorName(operatorName);
        attemptLog.setOperatorUsername(operatorUsername);
        attemptLog.setOperatorRole(userRole != null && userRole.startsWith("ROLE_") ? userRole.substring(5) : userRole);
        attemptLog.setIpAddress(ipAddress);
        attemptLog.setOperationType(operationType);
        attemptLog.setModule(module);
        attemptLog.setDescription(description);
        attemptLog.setRiskLevel(riskLevel);
        attemptLog.setTargetType(targetType);
        attemptLog.setTargetId(targetId);
        attemptLog.setRealOperatorName(originalAdminUsername);
        applyRequestContext(attemptLog);

        // 关键操作：审计不可用则禁止执行（ATTEMPTED 写入失败会抛异常）
        if (critical) {
            auditLogService.recordCriticalAttempt(attemptLog);
        }

        Object result = null;
        String operationResult = "SUCCESS";
        Throwable operationFailure = null;
        long startMillis = System.currentTimeMillis();

        try {
            result = joinPoint.proceed();

            if (result instanceof Result resultObj) {
                if (resultObj.getCode() != null && resultObj.getCode() != 200) {
                    operationResult = "FAILURE";
                    log.warn("操作返回失败结果：module={}, operationType={}, code={}",
                            module, operationType, resultObj.getCode());
                }
            } else if (result instanceof ResponseEntity<?> responseEntity
                    && !responseEntity.getStatusCode().is2xxSuccessful()) {
                operationResult = "FAILURE";
                log.warn("操作返回失败状态：module={}, operationType={}, status={}",
                        module, operationType, responseEntity.getStatusCode().value());
            }
        } catch (Throwable e) {
            operationResult = "FAILURE";
            operationFailure = e;
            log.warn("操作执行失败：module={}, operationType={}, exceptionType={}",
                    module, operationType, e.getClass().getSimpleName());
        }

        OperateLog olog = new OperateLog();
        olog.setOperateAdminId(attemptLog.getOperateAdminId());
        olog.setOperatorName(attemptLog.getOperatorName());
        olog.setOperatorUsername(attemptLog.getOperatorUsername());
        olog.setOperatorRole(attemptLog.getOperatorRole());
        olog.setIpAddress(attemptLog.getIpAddress());
        olog.setOperationType(operationType);
        olog.setModule(module);
        olog.setDescription(description);
        olog.setOperationResult(operationResult);
        olog.setRiskLevel(riskLevel);
        olog.setTargetType(targetType);
        olog.setTargetId(targetId);
        olog.setRealOperatorName(originalAdminUsername);
        olog.setDurationMs((int) (System.currentTimeMillis() - startMillis));
        applyRequestContext(olog);

        // 统一经由审计服务写入；写入失败由服务内部告警，不影响业务结果
        auditLogService.record(olog);

        if (operationFailure != null) {
            throw operationFailure;
        }

        return result;
    }

    /**
     * 补充请求上下文字段（requestId 由审计服务统一补全，这里填充 HTTP 方法/路径/设备类别）
     */
    private void applyRequestContext(OperateLog olog) {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return;
            }
            HttpServletRequest request = attributes.getRequest();
            if (request == null) {
                return;
            }
            olog.setHttpMethod(request.getMethod());
            olog.setRequestPath(truncate(request.getRequestURI(), 250));
            olog.setDeviceType(resolveDeviceType(request));
        } catch (Exception e) {
            log.debug("补充审计请求上下文失败：{}", e.getMessage());
        }
    }

    private String resolveDeviceType(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null || userAgent.isEmpty()) {
            return "UNKNOWN";
        }
        // 当前系统仅提供 Web 端；移动端 App 预留
        return userAgent.contains("Mobile") || userAgent.contains("Android") || userAgent.contains("iPhone") ? "MOBILE_WEB" : "WEB";
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /**
     * 按账号+邮箱归属查询角色（TEACHER / STUDENT / COMPANY），查不到返回 null。
     * 最佳努力性质：异常或归属失败都不影响主流程，调用处按角色未知处理。
     */
    private String resolveClaimedRole(ProceedingJoinPoint joinPoint, String claimedUsername) {
        try {
            String email = extractMapValue(joinPoint, "email");
            if (email == null) {
                return null;
            }
            return passwordResetService.resolveAccountRole(claimedUsername, email);
        } catch (Exception e) {
            log.debug("归属查询匿名操作角色失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 提取匿名请求中的账号标识：遍历参数中的 Map，取 username 键的非空字符串。
     * 仅用于无登录态的自助操作（如忘记密码重置），此时操作人无法从 JWT 解析；
     * 有登录态的请求不受影响（操作人已从登录态解析，不会走到这里）。
     */
    private String extractClaimedUsername(ProceedingJoinPoint joinPoint) {
        return extractMapValue(joinPoint, "username");
    }

    /**
     * 从切点参数的 Map 中提取指定键的非空字符串（去首尾空格），没有返回 null。
     */
    private String extractMapValue(ProceedingJoinPoint joinPoint, String key) {
        try {
            Object[] args = joinPoint.getArgs();
            if (args == null) {
                return null;
            }
            for (Object arg : args) {
                if (arg instanceof java.util.Map) {
                    Object value = ((java.util.Map<?, ?>) arg).get(key);
                    if (value instanceof String) {
                        String text = ((String) value).trim();
                        if (!text.isEmpty()) {
                            return text;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("提取匿名操作请求字段失败：{}", e.getMessage());
        }
        return null;
    }

    /**
     * 提取第一个数值型参数作为目标对象ID
     */
    private String extractTargetId(ProceedingJoinPoint joinPoint) {
        try {
            Object[] args = joinPoint.getArgs();
            for (Object arg : args) {
                if (arg instanceof Long || arg instanceof Integer) {
                    return String.valueOf(arg);
                }
            }
        } catch (Exception e) {
            log.debug("提取目标对象ID失败：{}", e.getMessage());
        }
        return null;
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
            // 统一走 IpUtils：只有可信反向代理的转发头才会被采信
            return IpUtils.getClientIp(request);
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

                // 只记录明确的数值型资源 ID；字符串可能包含搜索词、备注或隐私数据
                if (arg instanceof Long || arg instanceof Integer) {
                    if (paramsInfo.length() > 0) {
                        paramsInfo.append(", ");
                    }
                    paramsInfo.append("id=").append(arg);
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
