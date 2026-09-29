package com.internship.controller;

import com.internship.anno.Log;
import com.internship.entity.LoginLog;
import com.internship.entity.SecurityEventLog;
import com.internship.entity.User;
import com.internship.entity.StudentUser;
import com.internship.entity.TeacherUser;
import com.internship.entity.AdminUser;
import com.internship.entity.CompanyUser;
import com.internship.entity.Permission;
import com.internship.entity.Result;
import com.internship.exception.BusinessException;
import com.internship.service.AuditLogService;
import com.internship.service.UserService;
import com.internship.service.StudentUserService;
import com.internship.service.TeacherUserService;
import com.internship.service.AdminUserService;
import com.internship.service.CompanyUserService;
import com.internship.service.ClassService;
import com.internship.service.PermissionService;
import com.internship.service.AdminPermissionService;
import com.internship.service.TeacherPermissionService;
import com.internship.utils.IpUtils;
import com.internship.utils.JwtUtils;
import com.internship.utils.PasswordValidator;
import com.internship.config.SystemSettingsConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import com.internship.service.LoginAttemptService;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private AuthenticationManager authenticationManager;
    
    @Autowired
    private ApplicationContext applicationContext;
    
    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private TeacherUserService teacherUserService;
    
    @Autowired
    private StudentUserService studentUserService;
    
    @Autowired
    private AdminUserService adminUserService;
    
    @Autowired
    private CompanyUserService companyUserService;

    @Autowired
    private ClassService classService;

    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private PermissionService permissionService;
    
    @Autowired
    private TeacherPermissionService teacherPermissionService;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private com.internship.service.PasswordResetService passwordResetService;

    /**
     * 获取密码修改规则（公开接口）
     */
    @GetMapping("/password-rules")
    public Result getPasswordRules() {
        Map<String, Object> rules = new HashMap<>();
        rules.put("minLength", SystemSettingsConfig.getMinPasswordLength());
        rules.put("complexity", SystemSettingsConfig.getPasswordComplexity());
        rules.put("expireDays", SystemSettingsConfig.getPasswordExpireDays());
        return Result.success(rules);
    }

    @PostMapping("/login")
    public Result login(@RequestBody Map<String, Object> loginRequest, HttpServletRequest request) {
        String username = (String) loginRequest.get("username");
        String password = (String) loginRequest.get("password");

        log.debug("用户登录请求: username={}", username);

        if (username == null || username.isEmpty()) {
            return Result.error("用户名不能为空");
        }
        if (password == null || password.isEmpty()) {
            return Result.error("密码不能为空");
        }

        // 快速检查是否已被锁定（无锁，仅读取）
        if (loginAttemptService.isLocked(username)) {
            long remainingTime = loginAttemptService.getRemainingLockTime(username);
            log.warn("用户账号已被锁定: username={}, 剩余锁定时间={}秒", username, remainingTime);
            return Result.error("账号已被锁定，请" + remainingTime + "秒后再试");
        }

        try {
            Map<String, Object> result = new HashMap<>();

            TeacherUser teacher = teacherUserService.findByTeacherUserId(username);
            if (teacher != null) {
                result = handleTeacherLogin(username, password, request);
            } else {
                result = handleOtherUserLogin(username, password, request);
            }

            // 登录成功：清除失败计数（原子操作）
            loginAttemptService.clearFailures(username);
            log.info("用户登录成功，清除登录失败记录: username={}", username);

            return Result.success(result);
        } catch (AuthenticationException e) {
            log.error("用户登录失败: {}, 错误信息: {}", username, e.getMessage());

            // 原子递增失败计数（Redis INCR 或 synchronized 递增，仅保护计数器）
            int currentCount = loginAttemptService.recordFailure(username);

            boolean locked = currentCount < 0 || loginAttemptService.isLocked(username);

            // 记录登录失败日志与安全事件（锁定时事件等级提升为 HIGH）
            recordLoginAudit(username, "UNKNOWN", username, request, "FAILURE", locked);

            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.contains("账号已被禁用")) {
                return Result.error("您的账号已被禁用，请联系管理员");
            }

            if (locked) {
                int lockTime = SystemSettingsConfig.getLockTime();
                return Result.error("登录失败次数过多，账号已被锁定" + lockTime + "分钟");
            }

            int maxAttempts = SystemSettingsConfig.getMaxLoginAttempts();
            int remainingAttempts = maxAttempts - currentCount;
            return Result.error("用户名或密码错误，剩余尝试次数: " + remainingAttempts);
        } catch (Exception e) {
            log.error("登录过程中发生异常: {}", e.getMessage(), e);
            return Result.error("登录失败: " + e.getMessage());
        }
    }
    
    private Map<String, Object> handleTeacherLogin(String username, String password, HttpServletRequest request) {
        log.info("处理教师登录: 教师编号={}", username);
        
        TeacherUser teacher = teacherUserService.findByTeacherUserId(username);
        if (teacher == null) {
            log.warn("教师登录失败: 教师编号不存在 - {}", username);
            throw new AuthenticationException("教师编号或密码错误") {};
        }

        if (!passwordEncoder.matches(password, teacher.getPassword())) {
            log.warn("教师登录失败: 密码错误 - {}", username);
            throw new AuthenticationException("教师编号或密码错误") {};
        }

        if (teacher.getStatus() == 0) {
            log.warn("教师登录失败: 账号已被禁用 - {}", username);
            throw new AuthenticationException("账号已被禁用") {};
        }

        String teacherType = teacher.getTeacherType();
        String role = getRoleByTeacherType(teacherType);
        if (role == null) {
            log.warn("教师登录失败: 教师身份未配置 - {}", username);
            throw new AuthenticationException("教师身份未配置，请联系管理员") {};
        }
        teacher.setRole(role);

        List<String> permissionCodes = teacherPermissionService.getPermissionCodesByRoleCode(teacherType);

        log.info("教师 {} (类型: {}) 的菜单权限列表: {}", username, teacherType, permissionCodes);

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", teacher.getId());
        claims.put("username", teacher.getTeacherUserId());
        claims.put("name", teacher.getName());
        claims.put("role", role);
        claims.put("teacherType", teacherType);
        claims.put("permissions", permissionCodes);

        claims.put("sid", java.util.UUID.randomUUID().toString());
        String accessToken = jwtUtils.generateAccessToken(claims);
        String refreshToken = jwtUtils.generateRefreshToken(claims);

        Map<String, Object> result = new HashMap<>();
        result.put("token", accessToken);
        result.put("refreshToken", refreshToken);
        result.put("userInfo", teacher);
        result.put("permissions", permissionCodes);
        
        log.info("教师登录成功: {}, 类型: {}, 权限数: {}", username, teacherType, permissionCodes.size());
        
        try {
            recordLoginAudit(String.valueOf(teacher.getId()), role, teacher.getTeacherUserId(), request, "SUCCESS", false);
        } catch (Exception e) {
            log.error("记录教师登录日志失败: {}", e.getMessage());
        }
        
        try {
            userService.clearAllUserDetailsCache();
            log.info("教师登录成功，已清除UserDetails缓存");
        } catch (Exception e) {
            log.warn("清除UserDetails缓存失败: {}", e.getMessage());
        }
        
        return result;
    }
    
    private Map<String, Object> handleOtherUserLogin(String username, String password, HttpServletRequest request) {
        log.info("处理非教师登录: username={}", username);

        // 直接使用 passwordEncoder 进行密码验证，避免 DelegatingPasswordEncoder 问题
        User loginUser = userService.findByUsername(username);
        if (loginUser == null || loginUser.getPassword() == null) {
            log.warn("用户不存在或密码为空：{}", username);
            throw new AuthenticationException("用户名或密码错误") {};
        }

        // 在认证之前先检查用户状态
        Integer status = 1;
        String role = loginUser.getRole();
        if (role != null && role.startsWith("ROLE_TEACHER")) {
            TeacherUser teacherUser = teacherUserService.findByTeacherUserId(username);
            if (teacherUser != null && teacherUser.getStatus() != null) {
                status = teacherUser.getStatus();
            }
        } else if ("ROLE_STUDENT".equals(role)) {
            StudentUser studentUser = studentUserService.findByStudentId(username);
            if (studentUser != null && studentUser.getStatus() != null) {
                status = studentUser.getStatus();
            }
        } else if ("ROLE_COMPANY".equals(role)) {
            var companyUser = companyUserService.findById(loginUser.getId());
            if (companyUser != null) {
                if (companyUser.getStatus() != null) {
                    status = companyUser.getStatus();
                }
                if (companyUser.getAuditStatus() != null && companyUser.getAuditStatus() != 1) {
                    log.warn("企业登录失败: 审核未通过 - {}, auditStatus={}", username, companyUser.getAuditStatus());
                    throw new AuthenticationException("企业注册申请尚未审核通过，请等待教师审核") {};
                }
            }
        } else if ("ROLE_ADMIN".equals(role)) {
            AdminUser adminUser = adminUserService.findByUsername(username);
            if (adminUser != null && adminUser.getStatus() != null) {
                status = adminUser.getStatus();
            }
        }

        if (status == 0) {
            log.warn("用户登录失败: 账号已被禁用 - {}", username);
            throw new AuthenticationException("账号已被禁用") {};
        }

        // 使用 CustomPasswordEncoder 的 matches 方法进行密码验证
        if (!passwordEncoder.matches(password, loginUser.getPassword())) {
            log.warn("密码验证失败：{}", username);
            throw new AuthenticationException("用户名或密码错误") {};
        }

        log.info("密码验证成功：{}", username);

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", loginUser.getId());
        claims.put("username", loginUser.getUsername());
        claims.put("role", loginUser.getRole());

        String displayName = loginUser.getName();
        if ("ROLE_COMPANY".equals(loginUser.getRole())) {
            var companyUser = companyUserService.findById(loginUser.getId());
            if (companyUser != null) {
                displayName = companyUser.getCompanyName();
                claims.put("companyId", companyUser.getId());
            }
        }
        claims.put("name", displayName);

        String authenticatedTeacherType = null;
        if (loginUser.getRole() != null && loginUser.getRole().startsWith("ROLE_TEACHER")) {
            TeacherUser teacherUser = teacherUserService.findByTeacherUserId(username);
            if (teacherUser != null) {
                authenticatedTeacherType = teacherUser.getTeacherType();
            }
            if (!StringUtils.hasText(authenticatedTeacherType)) {
                authenticatedTeacherType = switch (loginUser.getRole()) {
                    case "ROLE_TEACHER_COLLEGE" -> "COLLEGE";
                    case "ROLE_TEACHER_DEPARTMENT" -> "DEPARTMENT";
                    case "ROLE_TEACHER_COUNSELOR" -> "COUNSELOR";
                    default -> null;
                };
            }
            if (StringUtils.hasText(authenticatedTeacherType)) {
                claims.put("teacherType", authenticatedTeacherType);
            }
        }

        // 为学生用户添加必要的标识信息（PII字段不放入JWT，通过API获取）
        if ("ROLE_STUDENT".equals(loginUser.getRole())) {
            StudentUser studentUser = studentUserService.findByStudentId(username);
            if (studentUser != null) {
                claims.put("studentId", studentUser.getStudentId());
            }
        }
        
        claims.put("sid", java.util.UUID.randomUUID().toString());
        String accessToken = jwtUtils.generateAccessToken(claims);
        String refreshToken = jwtUtils.generateRefreshToken(claims);

        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", accessToken);
        result.put("refreshToken", refreshToken);
        
        Map<String, Object> safeUser = new HashMap<>();
        safeUser.put("id", loginUser.getId());
        safeUser.put("username", loginUser.getUsername());
        safeUser.put("role", loginUser.getRole());
        safeUser.put("name", displayName);
        safeUser.put("createTime", loginUser.getCreateTime());
        if (StringUtils.hasText(authenticatedTeacherType)) {
            safeUser.put("teacherType", authenticatedTeacherType);
        }

        // 为学生用户添加额外的字段
        if ("ROLE_STUDENT".equals(loginUser.getRole())) {
            StudentUser studentUser = studentUserService.findByStudentId(username);
            if (studentUser != null) {
                // 获取班级名称
                String className = null;
                if (studentUser.getClassId() != null) {
                    try {
                        var classEntity = classService.findById(studentUser.getClassId());
                        if (classEntity != null) {
                            className = classEntity.getName();
                        }
                    } catch (Exception e) {
                        log.warn("获取班级名称失败: {}", e.getMessage());
                    }
                }
                safeUser.put("studentId", studentUser.getStudentId());
                safeUser.put("school", studentUser.getSchool());
                safeUser.put("department", studentUser.getDepartment());
                safeUser.put("major", studentUser.getMajor());
                safeUser.put("class", className);
                safeUser.put("gender", studentUser.getGender());
                safeUser.put("phone", studentUser.getPhone());
                safeUser.put("email", studentUser.getEmail());
                safeUser.put("grade", studentUser.getGrade());
            }
        }
        
        if ("ROLE_COMPANY".equals(loginUser.getRole())) {
            var companyUser = companyUserService.findById(loginUser.getId());
            if (companyUser != null) {
                safeUser.put("name", companyUser.getCompanyName());
            }
        }
        
        result.put("user", safeUser);
        
        if ("ROLE_ADMIN".equals(loginUser.getRole())) {
            try {
                AdminPermissionService adminPermissionService = applicationContext.getBean(AdminPermissionService.class);
                List<Permission> permissions = adminPermissionService.getRolePermissionsByCode(loginUser.getRole());
                result.put("permissions", permissions);
                log.info("管理员登录成功，已返回权限信息: {} 个权限", permissions.size());
            } catch (Exception e) {
                log.warn("获取管理员权限失败: {}", e.getMessage());
            }
        }
        
        if (loginUser.getRole() != null && loginUser.getRole().startsWith("ROLE_TEACHER")) {
            try {
                com.internship.service.TeacherPermissionService teacherPermissionService = 
                    applicationContext.getBean(com.internship.service.TeacherPermissionService.class);
                List<String> permissionCodes = teacherPermissionService.getPermissionCodesByRoleCode(loginUser.getRole());
                result.put("permissions", permissionCodes);
                log.info("教师登录成功，已返回权限信息: {} 个权限", permissionCodes.size());
            } catch (Exception e) {
                log.warn("获取教师权限失败: {}", e.getMessage());
            }
        }
        
        log.info("用户登录成功: {}", username);
        
        try {
            String userId = loginUser.getId() != null ? String.valueOf(loginUser.getId()) : loginUser.getUsername();
            recordLoginAudit(userId, loginUser.getRole(), loginUser.getUsername(), request, "SUCCESS", false);
        } catch (Exception e) {
            log.error("记录用户登录日志失败: {}", e.getMessage());
        }
        
        return result;
    }
    
    private String getRoleByTeacherType(String teacherType) {
        if (teacherType == null) {
            return null;
        }
        return switch (teacherType) {
            case "COLLEGE" -> "ROLE_TEACHER_COLLEGE";
            case "DEPARTMENT" -> "ROLE_TEACHER_DEPARTMENT";
            case "COUNSELOR" -> "ROLE_TEACHER_COUNSELOR";
            default -> null;
        };
    }

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/current-user")
    public Result getCurrentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetails)) {
            return Result.error("未登录");
        }
        
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String username = userDetails.getUsername();
        
        // 查询用户信息
        User user = userService.findByUsername(username);
        if (user == null) {
            return Result.error("用户不存在");
        }
        
        // 创建一个不包含敏感信息的用户对象
        Map<String, Object> safeUser = new HashMap<>();
        safeUser.put("id", user.getId());
        safeUser.put("username", user.getUsername());
        safeUser.put("role", user.getRole());
        safeUser.put("name", user.getName());
        safeUser.put("createTime", user.getCreateTime());
        
        return Result.success(safeUser);
    }
    
    /**
     * 刷新访问令牌
     * 实现滑动窗口刷新策略，只在必要时生成新的刷新令牌
     */
    @PostMapping("/refresh-token")
    public Result refreshToken(@RequestBody Map<String, String> refreshData) {
        String refreshToken = refreshData.get("refreshToken");
        
        if (!StringUtils.hasText(refreshToken)) {
            return Result.error("刷新令牌不能为空");
        }
        
        try {
            // 验证刷新令牌
            Map<String, Object> claims = jwtUtils.parseToken(refreshToken);
            
            // Only refresh tokens with an explicit read-only marker may be refreshed.
            // Legacy tokens omitted it, including tokens issued while viewing another role.
            if (!"refresh".equals(claims.get("typ")) || !(claims.get("isReadOnly") instanceof Boolean)
                    || !(claims.get("sid") instanceof String sid) || sid.isBlank()) {
                return Result.error("刷新令牌无效，请重新登录");
            }
            boolean readOnly = Boolean.TRUE.equals(claims.get("isReadOnly"));
            Object originalAdminId = claims.get("originalAdminId");
            String originalAdminUsername = (String) claims.get("originalAdminUsername");
            if ((readOnly && (!(originalAdminId instanceof Number) || !StringUtils.hasText(originalAdminUsername)))
                    || (!readOnly && (originalAdminId != null || originalAdminUsername != null))) {
                return Result.error("刷新令牌无效，请重新登录");
            }

            // 从刷新令牌中获取用户信息
            String username = (String) claims.get("username");
            String role = (String) claims.get("role");
            // userId 可能是 Long 或 Integer 类型，需要安全转换
            Object userIdObj = claims.get("userId");
            Long userId = null;
            if (userIdObj instanceof Long) {
                userId = (Long) userIdObj;
            } else if (userIdObj instanceof Integer) {
                userId = ((Integer) userIdObj).longValue();
            } else if (userIdObj instanceof String) {
                userId = Long.parseLong((String) userIdObj);
            }
            
            if (username == null || role == null || userId == null) {
                return Result.error("刷新令牌无效");
            }
            if ("ROLE_TEACHER".equals(role)) {
                return Result.error("教师身份需重新验证");
            }

            // 从刷新令牌中获取其他信息
            String name = (String) claims.get("name");
            String teacherType = (String) claims.get("teacherType");

            // 生成新的访问令牌
            Map<String, Object> newClaims = new HashMap<>();
            newClaims.put("sid", claims.get("sid"));
            newClaims.put("userId", userId);
            newClaims.put("username", username);
            newClaims.put("name", name);
            newClaims.put("role", role);
            newClaims.put("isReadOnly", readOnly);
            if (readOnly) {
                newClaims.put("originalAdminId", originalAdminId);
                newClaims.put("originalAdminUsername", originalAdminUsername);
            }
            if (teacherType != null) {
                newClaims.put("teacherType", teacherType);
            }
            String newAccessToken = jwtUtils.generateAccessToken(newClaims);
            
            // 使用滑动窗口策略决定是否生成新的刷新令牌
            // 只有当刷新令牌即将过期时（剩余有效期小于滑动窗口阈值），才生成新的刷新令牌
            String newRefreshToken = refreshToken; // 默认复用原刷新令牌
            if (jwtUtils.shouldRefreshToken(refreshToken)) {
                newRefreshToken = jwtUtils.generateRefreshToken(newClaims);
                log.info("用户 {} 的刷新令牌已更新（滑动窗口策略）", username);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("accessToken", newAccessToken);
            result.put("refreshToken", newRefreshToken);
            
            log.info("用户刷新令牌成功: {}", username);
            return Result.success(result);
        } catch (JwtUtils.JwtException e) {
            log.error("刷新令牌失败: {}, 错误码: {}", e.getMessage(), e.getErrorCode());
            return Result.error("刷新令牌无效或已过期");
        } catch (Exception e) {
            log.error("刷新令牌过程发生异常: {}", e.getMessage());
            return Result.error("刷新令牌失败，请重新登录");
        }
    }
    
    /**
     * 用户登出接口
     * 将访问令牌加入黑名单
     */
    @PostMapping("/logout")
    @Log(operationType = "LOGOUT", module = "AUTH", description = "用户登出系统")
    public Result logout(HttpServletRequest request, Authentication authentication) {
        try {
            Long adminId = null;
            String username = null;
            
            if (authentication != null && authentication.getPrincipal() instanceof UserDetails) {
                UserDetails userDetails = (UserDetails) authentication.getPrincipal();
                username = userDetails.getUsername();
                
                try {
                    AdminUser adminUser = adminUserService.findByUsername(username);
                    if (adminUser != null) {
                        adminId = adminUser.getId();
                    }
                } catch (Exception e) {
                    log.warn("获取管理员ID失败: {}", e.getMessage());
                }
                
                String authorizationHeader = request.getHeader("Authorization");
                if (StringUtils.hasText(authorizationHeader) && authorizationHeader.startsWith("Bearer ")) {
                    String accessToken = authorizationHeader.substring(7);
                    jwtUtils.revokeSession(accessToken);
                    log.info("管理员 {} (ID: {}) 登出成功，访问令牌已加入黑名单", username, adminId);
                }

                // 记录登出安全事件（P1）
                SecurityEventLog logoutEvent = new SecurityEventLog();
                logoutEvent.setEventType("LOGOUT");
                logoutEvent.setUserId(adminId != null ? String.valueOf(adminId) : username);
                logoutEvent.setUsername(username);
                logoutEvent.setRiskLevel(AuditLogService.RISK_LOW);
                logoutEvent.setResult("SUCCESS");
                logoutEvent.setIpAddress(IpUtils.getClientIp(request));
                logoutEvent.setHttpMethod(request.getMethod());
                logoutEvent.setRequestPath(request.getRequestURI());
                logoutEvent.setDescription("用户登出系统");
                auditLogService.recordSecurityEvent(logoutEvent);
            }

            return Result.success("登出成功");
        } catch (Exception e) {
            log.error("用户登出失败: {}", e.getMessage());
            return Result.error("登出失败");
        }
    }
    
    /**
     * 管理员角色切换接口
     * 允许管理员切换到教师、学生或企业端账号进行测试
     */
    @PostMapping("/switch-role")
    @Log(operationType = "SWITCH_ROLE", module = "AUTH", description = "管理员切换角色", riskLevel = "MEDIUM", targetType = "ROLE", critical = true)
    public Result switchRole(@RequestBody Map<String, String> switchRequest, Authentication authentication, HttpServletRequest request) {
        try {
            if (authentication == null || !(authentication.getPrincipal() instanceof UserDetails)) {
                return Result.error("未登录或无权限");
            }
            
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            String adminUsername = userDetails.getUsername();
            
            AdminUser adminUser = adminUserService.findByUsername(adminUsername);
            if (adminUser == null || !"ROLE_ADMIN".equals(adminUser.getRole())) {
                return Result.error("只有管理员才能切换角色");
            }
            
            String adminPassword = switchRequest.get("adminPassword");
            if (!StringUtils.hasText(adminPassword)) {
                return Result.error("请输入管理员密码进行验证");
            }
            
            if (!passwordEncoder.matches(adminPassword, adminUser.getPassword())) {
                log.warn("管理员 {} 切换角色时密码验证失败", adminUsername);
                return Result.error("管理员密码错误");
            }
            
            String targetRole = switchRequest.get("targetRole");
            String targetUsername = switchRequest.get("targetUsername");
            
            if (!StringUtils.hasText(targetRole) || !StringUtils.hasText(targetUsername)) {
                return Result.error("目标角色和用户名不能为空");
            }
            
            Map<String, Object> result = new HashMap<>();
            
            if ("teacher".equals(targetRole)) {
                TeacherUser teacher = teacherUserService.findByTeacherUserId(targetUsername);
                if (teacher == null) {
                    return Result.error("教师工号不存在");
                }
                
                if (teacher.getStatus() == 0) {
                    return Result.error("该教师账号已被禁用");
                }
                
                String teacherType = teacher.getTeacherType();
                String role = getRoleByTeacherType(teacherType);
                if (role == null) {
                    return Result.error("教师身份未配置，请联系管理员");
                }
                List<String> permissionCodes = teacherPermissionService.getPermissionCodesByRoleCode(teacherType);

                Map<String, Object> claims = new HashMap<>();
                claims.put("userId", teacher.getId());
                claims.put("username", teacher.getTeacherUserId());
                claims.put("name", teacher.getName());
                claims.put("role", role);
                claims.put("teacherType", teacherType);
                claims.put("permissions", permissionCodes);
                claims.put("isReadOnly", true);
                claims.put("originalAdminId", adminUser.getId());
                claims.put("originalAdminUsername", adminUser.getUsername());
                
                claims.put("sid", java.util.UUID.randomUUID().toString());
                String accessToken = jwtUtils.generateAccessToken(claims);
                String refreshToken = jwtUtils.generateRefreshToken(claims);
                
                result.put("token", accessToken);
                result.put("refreshToken", refreshToken);
                result.put("userInfo", teacher);
                result.put("permissions", permissionCodes);
                result.put("role", role);
                result.put("isReadOnly", true);
                result.put("originalAdminUsername", adminUser.getUsername());
                
                log.info("管理员 {} 切换到教师角色: {} ({}), 进入只读模式", adminUsername, teacher.getName(), teacher.getTeacherUserId());
                
            } else if ("student".equals(targetRole)) {
                StudentUser student = studentUserService.findByStudentId(targetUsername);
                if (student == null) {
                    return Result.error("学生学号不存在");
                }
                
                if (student.getStatus() == 0) {
                    return Result.error("该学生账号已被禁用");
                }
                
                User user = userService.findByUsername(targetUsername);
                if (user == null) {
                    return Result.error("学生用户信息不存在");
                }
                
                Map<String, Object> claims = new HashMap<>();
                claims.put("userId", student.getId());
                claims.put("username", student.getStudentId());
                claims.put("role", "ROLE_STUDENT");
                claims.put("name", student.getName());
                claims.put("isReadOnly", true);
                claims.put("originalAdminId", adminUser.getId());
                claims.put("originalAdminUsername", adminUser.getUsername());
                
                claims.put("sid", java.util.UUID.randomUUID().toString());
                String accessToken = jwtUtils.generateAccessToken(claims);
                String refreshToken = jwtUtils.generateRefreshToken(claims);
                
                Map<String, Object> safeUser = new HashMap<>();
                safeUser.put("id", student.getId());
                safeUser.put("username", student.getStudentId());
                safeUser.put("role", "ROLE_STUDENT");
                safeUser.put("name", student.getName());
                
                result.put("accessToken", accessToken);
                result.put("refreshToken", refreshToken);
                result.put("user", safeUser);
                result.put("role", "ROLE_STUDENT");
                result.put("isReadOnly", true);
                result.put("originalAdminUsername", adminUser.getUsername());
                
                log.info("管理员 {} 切换到学生角色: {} ({}), 进入只读模式", adminUsername, student.getName(), student.getStudentId());
                
            } else if ("company".equals(targetRole)) {
                User user = userService.findByUsername(targetUsername);
                if (user == null) {
                    return Result.error("企业用户不存在");
                }
                
                if (!"ROLE_COMPANY".equals(user.getRole())) {
                    return Result.error("该用户不是企业用户");
                }
                
                var companyUser = companyUserService.findById(user.getId());
                if (companyUser == null) {
                    return Result.error("企业信息不存在");
                }
                
                if (companyUser.getStatus() == 0) {
                    return Result.error("该企业账号已被禁用");
                }
                
                Map<String, Object> claims = new HashMap<>();
                claims.put("userId", user.getId());
                claims.put("username", user.getUsername());
                claims.put("role", "ROLE_COMPANY");
                claims.put("name", companyUser.getCompanyName());
                claims.put("isReadOnly", true);
                claims.put("originalAdminId", adminUser.getId());
                claims.put("originalAdminUsername", adminUser.getUsername());
                
                claims.put("sid", java.util.UUID.randomUUID().toString());
                String accessToken = jwtUtils.generateAccessToken(claims);
                String refreshToken = jwtUtils.generateRefreshToken(claims);
                
                Map<String, Object> safeUser = new HashMap<>();
                safeUser.put("id", user.getId());
                safeUser.put("username", user.getUsername());
                safeUser.put("role", "ROLE_COMPANY");
                safeUser.put("name", companyUser.getCompanyName());
                
                result.put("accessToken", accessToken);
                result.put("refreshToken", refreshToken);
                result.put("user", safeUser);
                result.put("role", "ROLE_COMPANY");
                result.put("isReadOnly", true);
                result.put("originalAdminUsername", adminUser.getUsername());
                
                log.info("管理员 {} 切换到企业角色: {}, 进入只读模式", adminUsername, companyUser.getCompanyName());
                
            } else {
                return Result.error("不支持的目标角色");
            }
            
            return Result.success(result);
        } catch (Exception e) {
            log.error("角色切换失败: {}", e.getMessage(), e);
            return Result.error("角色切换失败: " + e.getMessage());
        }
    }

    /**
     * 验证密码（仅用于安全验证，不修改密码，不增加令牌版本号）
     * 用于清除数据等危险操作前的安全验证
     */
    @PostMapping("/verify-password")
    @Log(operationType = "VERIFY", module = "AUTH", description = "验证密码（敏感读取）", riskLevel = "MEDIUM")
    public Result verifyPassword(@RequestBody Map<String, String> passwordData, Authentication authentication) {
        if (authentication == null) {
            return Result.error("未登录");
        }
        
        String username = null;
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails) {
            username = ((UserDetails) principal).getUsername();
        } else if (principal instanceof User) {
            username = ((User) principal).getUsername();
        } else if (principal != null) {
            username = principal.toString();
        }
        
        if (username == null) {
            return Result.error("未登录");
        }
        
        String password = passwordData.get("password");
        
        if (!StringUtils.hasText(password)) {
            return Result.error("密码不能为空");
        }
        
        try {
            User currentUser = userService.findByUsername(username);
            if (currentUser == null) {
                return Result.error("用户不存在");
            }
            
            if (!passwordEncoder.matches(password, currentUser.getPassword())) {
                log.warn("用户 {} 密码验证失败：密码错误", username);
                return Result.error("密码错误");
            }
            
            log.info("用户 {} 密码验证成功", username);
            return Result.success("密码验证成功");
        } catch (Exception e) {
            log.error("密码验证失败: {}", e.getMessage(), e);
            return Result.error("密码验证失败，请稍后再试");
        }
    }

    /**
     * 修改密码
     */
    @PutMapping("/change-password")
    @Log(operationType = "RESET_PASSWORD", module = "AUTH", description = "修改密码", riskLevel = "HIGH", targetType = "USER", critical = true)
    public Result changePassword(@RequestBody Map<String, String> passwordData, Authentication authentication, HttpServletRequest request) {
        if (authentication == null) {
            return Result.error("未登录");
        }
        
        // 获取用户名，支持多种 principal 类型
        String username = null;
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails) {
            username = ((UserDetails) principal).getUsername();
        } else if (principal instanceof User) {
            username = ((User) principal).getUsername();
        } else if (principal != null) {
            username = principal.toString();
        }
        
        if (username == null) {
            return Result.error("未登录");
        }
        
        String oldPassword = passwordData.get("oldPassword");
        String newPassword = passwordData.get("newPassword");
        
        // 参数验证 - 使用hasText方法替代过时的isEmpty方法
        if (!StringUtils.hasText(oldPassword) || !StringUtils.hasText(newPassword)) {
            return Result.error("密码不能为空");
        }
        
        // 验证新密码复杂度：必须包含字母和数字
        PasswordValidator.ValidationResult validationResult = PasswordValidator.validatePassword(newPassword);
        if (!validationResult.isValid()) {
            log.warn("用户 {} 修改密码失败：新密码不符合复杂度要求 - {}", username, validationResult.getMessage());
            return Result.error(validationResult.getMessage());
        }
        
        try {
            // 直接从数据库查询用户信息，避免使用缓存
            User currentUser = userService.findByUsername(username);
            if (currentUser == null) {
                return Result.error("用户不存在");
            }

            // 新密码不能与当前密码相同
            if (currentUser.getPassword() != null && passwordEncoder.matches(newPassword, currentUser.getPassword())) {
                log.warn("用户 {} 修改密码失败：新密码与当前密码相同", username);
                return Result.error("新密码不能与当前密码相同。");
            }
            
            // 直接比较密码（系统使用NoOpPasswordEncoder，密码是明文存储）
            if (!passwordEncoder.matches(oldPassword, currentUser.getPassword())) {
                log.warn("用户 {} 修改密码失败：旧密码错误", username);
                return Result.error("旧密码错误");
            }
            
            // 增加用户令牌版本号，使之前的令牌失效（安全措施）
            jwtUtils.incrementUserTokenVersion(username);
            userService.updatePassword(username, newPassword);
            
            log.info("用户 {} 修改密码成功，已增加令牌版本号使旧令牌失效", username);
            return Result.success("密码修改成功，请重新登录");
        } catch (AuthenticationException e) {
            log.error("用户 {} 修改密码失败：认证异常", username, e);
            return Result.error("旧密码错误");
        } catch (Exception e) {
            log.error("修改密码失败: {}", e.getMessage(), e);
            return Result.error("修改密码失败，请稍后再试");
        }
    }

    // 检查当前用户状态
    @GetMapping("/check-status")
    public Result checkUserStatus(HttpServletRequest request) {
        try {
            String username = null;
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication == null) {
                log.warn("检查用户状态失败: SecurityContext中的authentication为null");
                return Result.error("未登录");
            }
            
            Object principal = authentication.getPrincipal();
            
            if (principal == null) {
                log.warn("检查用户状态失败: authentication中的principal为null");
                return Result.error("未登录");
            }
            
            if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
                username = ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
            } else if (principal instanceof User) {
                username = ((User) principal).getUsername();
            } else if (principal != null) {
                username = principal.toString();
            }
            
            if (username == null || username.isEmpty()) {
                log.warn("检查用户状态失败: 无法从principal中获取username, principal类型: {}", principal.getClass().getName());
                return Result.error("未登录");
            }
            
            User user = userService.findByUsername(username);
            if (user == null) {
                log.warn("检查用户状态失败: 用户不存在 - {}", username);
                return Result.error("用户不存在");
            }
            
            // 根据用户角色检查状态
            Integer status = 1;
            String role = user.getRole();
            if (role != null && role.startsWith("ROLE_TEACHER")) {
                try {
                    TeacherUser teacherUser = teacherUserService.findByTeacherUserId(username);
                    if (teacherUser != null && teacherUser.getStatus() != null) {
                        status = teacherUser.getStatus();
                    }
                } catch (Exception e) {
                    log.error("查询教师用户状态失败: {}", e.getMessage());
                }
            } else if ("ROLE_STUDENT".equals(role)) {
                try {
                    StudentUser studentUser = studentUserService.findByStudentId(username);
                    if (studentUser != null && studentUser.getStatus() != null) {
                        status = studentUser.getStatus();
                    }
                } catch (Exception e) {
                    log.error("查询学生用户状态失败: {}", e.getMessage());
                }
            } else if ("ROLE_COMPANY".equals(role)) {
                try {
                    var companyUser = companyUserService.findById(user.getId());
                    if (companyUser != null && companyUser.getStatus() != null) {
                        status = companyUser.getStatus();
                    }
                } catch (Exception e) {
                    log.error("查询企业用户状态失败: {}", e.getMessage());
                }
            } else if ("ROLE_ADMIN".equals(role)) {
                try {
                    AdminUser adminUser = adminUserService.findByUsername(username);
                    if (adminUser != null && adminUser.getStatus() != null) {
                        status = adminUser.getStatus();
                    }
                } catch (Exception e) {
                    log.error("查询管理员用户状态失败: {}", e.getMessage());
                }
            }
            
            if (status == 0) {
                return Result.error("您的账号已被禁用，请联系管理员");
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("status", status);
            result.put("username", user.getUsername());
            result.put("name", user.getName());
            
            return Result.success(result);
        } catch (Exception e) {
            log.error("检查用户状态失败: {}", e.getMessage(), e);
            return Result.error("检查用户状态失败");
        }
    }

    /**
     * 修改用户名
     */
    @PutMapping("/update-username")
    @Log(operationType = "UPDATE", module = "AUTH", description = "修改用户名", riskLevel = "MEDIUM")
    public Result updateUsername(@RequestBody Map<String, String> usernameData, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Result.error("未登录");
        }
        
        String oldUsername = usernameData.get("oldUsername");
        String newUsername = usernameData.get("newUsername");
        
        if (!StringUtils.hasText(oldUsername) || !StringUtils.hasText(newUsername)) {
            return Result.error("用户名不能为空");
        }

        // The old name is supplied by the client only for display/consistency. It must
        // never select the account to update; the authenticated principal owns it.
        if (!authentication.getName().equals(oldUsername)) {
            throw new org.springframework.security.access.AccessDeniedException("无权修改其他账号");
        }
        
        if (newUsername.length() < 3 || newUsername.length() > 20) {
            return Result.error("用户名长度必须在3-20个字符之间");
        }
        
        if (oldUsername.equals(newUsername)) {
            return Result.error("新用户名不能与旧用户名相同");
        }
        
        try {
            int result = userService.updateUsername(authentication.getName(), newUsername);
            if (result > 0) {
                log.info("用户名修改成功: {} -> {}", oldUsername, newUsername);
                return Result.success("用户名修改成功");
            } else {
                return Result.error("用户名修改失败");
            }
        } catch (BusinessException e) {
            log.warn("用户名修改失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("用户名修改失败: {}", e.getMessage());
            return Result.error("用户名修改失败");
        }
    }


    /**
     * 忘记密码：发送邮箱验证码（公开接口）
     * 覆盖学生 / 教师 / 企业账号，管理员暂不支持自助找回。
     * 身份校验失败统一提示，防止账号枚举。
     */
    @PostMapping("/forgot-password/send-code")
    public Result sendPasswordResetCode(@RequestBody Map<String, String> requestData, HttpServletRequest request) {
        try {
            passwordResetService.sendResetCode(
                    requestData.get("username"),
                    requestData.get("email"),
                    IpUtils.getClientIp(request));
            return Result.success("验证码已发送，请查收邮件");
        } catch (BusinessException e) {
            log.warn("忘记密码发码失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("忘记密码发码异常: {}", e.getMessage(), e);
            return Result.error("发送验证码失败，请稍后再试");
        }
    }

    /**
     * 忘记密码：第一步身份确认（账号 + 邮箱 + 验证码预校验，不消费验证码）
     */
    @PostMapping("/forgot-password/verify-code")
    public Result verifyPasswordResetCode(@RequestBody Map<String, String> requestData, HttpServletRequest request) {
        try {
            passwordResetService.verifyResetCode(
                    requestData.get("username"),
                    requestData.get("email"),
                    requestData.get("code"),
                    IpUtils.getClientIp(request));
            return Result.success("身份验证通过");
        } catch (BusinessException e) {
            log.warn("忘记密码身份确认失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("忘记密码身份确认异常: {}", e.getMessage(), e);
            return Result.error("身份验证失败，请稍后再试");
        }
    }

    /**
     * 忘记密码：验证码重置密码（公开接口）
     */
    @PostMapping("/forgot-password/reset")
    @Log(operationType = "RESET_PASSWORD", module = "AUTH", description = "邮箱验证重置密码", riskLevel = "HIGH", targetType = "USER", critical = true)
    public Result resetPasswordByEmail(@RequestBody Map<String, String> requestData, HttpServletRequest request) {
        try {
            passwordResetService.resetPassword(
                    requestData.get("username"),
                    requestData.get("email"),
                    requestData.get("code"),
                    requestData.get("newPassword"),
                    IpUtils.getClientIp(request));
            return Result.success("密码重置成功，请使用新密码登录");
        } catch (BusinessException e) {
            log.warn("忘记密码重置失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("忘记密码重置异常: {}", e.getMessage(), e);
            return Result.error("密码重置失败，请稍后再试");
        }
    }


    /**
     * 统一登录审计入口（P1）：写登录日志 + 安全事件
     * locked=true 表示本次失败触发了账号锁定（事件类型 ACCOUNT_LOCK，等级 HIGH）
     */
    private void recordLoginAudit(String userId, String userType, String userName, HttpServletRequest request, String status, boolean locked) {
        boolean failure = "FAILURE".equalsIgnoreCase(status);

        LoginLog loginLog = new LoginLog();
        loginLog.setUserId(userId);
        loginLog.setUserType(userType);
        loginLog.setUserName(userName);
        loginLog.setIpAddress(IpUtils.getClientIp(request));
        loginLog.setDeviceInfo(request.getHeader("User-Agent"));
        loginLog.setLoginStatus(status);
        loginLog.setRiskLevel(failure ? AuditLogService.RISK_MEDIUM : AuditLogService.RISK_LOW);
        loginLog.setDeviceType(resolveDeviceType(request));
        loginLog.setErrorCode(failure ? "AUTH_FAILED" : null);
        auditLogService.recordLoginLog(loginLog);

        SecurityEventLog event = new SecurityEventLog();
        event.setEventType(locked ? "ACCOUNT_LOCK" : (failure ? "LOGIN_FAILURE" : "LOGIN_SUCCESS"));
        event.setUserId(userId);
        event.setUsername(userName);
        event.setUserRole(userType);
        event.setRiskLevel(locked ? AuditLogService.RISK_HIGH : (failure ? AuditLogService.RISK_MEDIUM : AuditLogService.RISK_LOW));
        event.setResult(failure ? "FAILURE" : "SUCCESS");
        event.setErrorCode(failure ? "AUTH_FAILED" : null);
        event.setIpAddress(IpUtils.getClientIp(request));
        event.setHttpMethod(request.getMethod());
        event.setRequestPath(request.getRequestURI());
        event.setDeviceType(loginLog.getDeviceType());
        event.setDescription(locked ? "登录失败次数过多，账号已锁定"
                : (failure ? "登录失败" : "登录成功"));
        auditLogService.recordSecurityEvent(event);
    }

    private String resolveDeviceType(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null || userAgent.isEmpty()) {
            return "UNKNOWN";
        }
        return userAgent.contains("Mobile") || userAgent.contains("Android") || userAgent.contains("iPhone") ? "MOBILE_WEB" : "WEB";
    }
}
